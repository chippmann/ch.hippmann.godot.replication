package ch.hippmann.godot.replication.sync

import ch.hippmann.godot.replication.OwnerLeavePolicy
import ch.hippmann.godot.replication.OwnershipPolicy
import ch.hippmann.godot.replication.SpawnOptions
import ch.hippmann.godot.replication.core.codec.ByteReader
import ch.hippmann.godot.replication.core.codec.ByteWriter
import ch.hippmann.godot.replication.core.session.NetworkId
import ch.hippmann.godot.replication.core.session.PlayerId
import ch.hippmann.godot.replication.core.wire.Despawn
import ch.hippmann.godot.replication.core.wire.OwnerLeavePolicyId
import ch.hippmann.godot.replication.core.wire.OwnershipPolicyId
import ch.hippmann.godot.replication.core.wire.ParentReference
import ch.hippmann.godot.replication.core.wire.Spawn
import ch.hippmann.godot.replication.transport.Channels
import ch.hippmann.godot.replication.transport.Transport
import ch.hippmann.godot.replication.transport.TransportFlags
import godot.api.Node
import godot.api.PackedScene
import godot.api.SceneTree
import godot.core.NodePath
import godot.core.asStringName
import godot.global.GD

internal class SpawnService(private val transport: Transport, private val tree: SceneTree) {
    private class PendingSpawn(val spawn: Spawn, val since: Long)

    private var counter = 0
    private val pendingSpawns = ArrayList<PendingSpawn>()

    fun <T : Node> spawn(scene: PackedScene, parent: Node, owner: PlayerId, spawnData: Any?, options: SpawnOptions): T {
        check(parent.isInsideTree()) { "The parent must be inside the tree to spawn under it" }
        val node = checkNotNull(scene.instantiate()) { "Could not instantiate ${scene.resourcePath}" }
        val replica = NodeRegistry.replicaFor(node)
        val networkId = NetworkId.spawned(NodeRegistry.localPlayerId, ++counter)
        val name = options.name ?: "${scene.resourcePath.substringAfterLast('/').substringBefore('.')}_${owner.value}_${networkId.counter}"
        val parentReference = NodeRegistry.replicaOf(parent)?.networkId?.let(ParentReference::Networked) ?: ParentReference.Path(parent.getPath().path)
        replica.spawnRecord = SpawnRecord(scene.resourcePath, parentReference, encodeSpawnData(replica, spawnData))
        replica.ownerLeavePolicy = options.ownerLeavePolicy
        replica.ownershipPolicy = options.ownershipPolicy
        node.name = name.asStringName()
        NodeRegistry.activator?.configure(replica, scenePlaced = false)
        NodeRegistry.activate(replica, networkId, owner)
        parent.addChild(node)
        node.setMultiplayerAuthority(owner.value)

        transport.broadcast(Channels.CONTROL, spawnMessage(replica).encode(), TransportFlags.RELIABLE)
        @Suppress("UNCHECKED_CAST")
        return node as T
    }

    /** The spawn as it would be sent right now: the initial state is the node's current state. */
    fun spawnMessage(replica: Replica): Spawn {
        val record = checkNotNull(replica.spawnRecord) { "${replica.node.name} was not spawned through the network" }
        val schema = replica.freezeSchema()
        val initialState = ByteWriter().also { writer -> replica.writeValues(writer, schema.fullMask) }.toByteArray()
        return Spawn(
            networkId = checkNotNull(replica.networkId),
            scenePath = record.scenePath,
            parent = record.parent,
            name = replica.node.name.toString(),
            owner = replica.owner,
            ownerLeavePolicy = replica.ownerLeavePolicy.toId(),
            ownershipPolicy = replica.ownershipPolicy.toId(),
            schemaHash = schema.hash,
            spawnData = record.spawnData,
            initialState = initialState,
        )
    }

    /** Spawns whose parent is not in the tree yet (a level still loading) are retried for a while. */
    fun retryPendingSpawns() {
        if (pendingSpawns.isEmpty()) return
        val now = FrameClock.nowMilliseconds
        val iterator = pendingSpawns.iterator()
        while (iterator.hasNext()) {
            val pending = iterator.next()
            if (resolveParent(pending.spawn.parent) != null) {
                iterator.remove()
                instantiate(pending.spawn)
            } else if (now - pending.since > PENDING_SPAWN_TIMEOUT_MILLISECONDS) {
                iterator.remove()
                GD.printErr("Replication: gave up spawning ${pending.spawn.scenePath}, parent ${pending.spawn.parent} never appeared")
            }
        }
    }

    fun despawn(node: Node) {
        val replica = NodeRegistry.replicaOf(node) ?: return
        val networkId = replica.networkId ?: return
        replica.despawning = true
        transport.broadcastMessage(Despawn(networkId))
        node.queueFree()
    }

    fun onSpawn(sender: PlayerId, bytes: ByteArray) {
        val spawn = Spawn.decode(bytes)
        if (spawn.owner != sender && spawn.networkId.ownerPart != sender) {
            GD.printErr("Replication: ignoring spawn of ${spawn.scenePath} from ${sender.value}, owner is ${spawn.owner.value}")
            return
        }
        if (NodeRegistry.byNetworkId(spawn.networkId) != null) return
        if (resolveParent(spawn.parent) == null) {
            pendingSpawns += PendingSpawn(spawn, FrameClock.nowMilliseconds)
            return
        }
        instantiate(spawn)
    }

    private fun instantiate(spawn: Spawn) {
        val parent = resolveParent(spawn.parent) ?: return
        val scene = GD.load<PackedScene>(spawn.scenePath)
        val node = scene?.instantiate()
        if (node == null) {
            GD.printErr("Replication: cannot load ${spawn.scenePath}")
            return
        }
        val replica = NodeRegistry.replicaFor(node)
        val schema = replica.freezeSchema()
        if (schema.hash != spawn.schemaHash) {
            GD.printErr("Replication: schema mismatch for ${spawn.scenePath}, local ${schema.hash} remote ${spawn.schemaHash}")
            node.queueFree()
            return
        }
        replica.spawnRecord = SpawnRecord(spawn.scenePath, spawn.parent, spawn.spawnData)
        replica.ownerLeavePolicy = spawn.ownerLeavePolicy.toPolicy()
        replica.ownershipPolicy = spawn.ownershipPolicy.toPolicy()
        node.name = spawn.name.asStringName()
        decodeSpawnData(replica, spawn.spawnData)
        // Stamped one delay back so the first stream sample, stamped in the sender's clock, is never older than the spawn.
        val receiveTime = FrameClock.nowMilliseconds - replica.interpolationDelayMilliseconds
        replica.readValues(ByteReader(spawn.initialState), schema.fullMask, receiveTime, applyEngineBindings = false)
        NodeRegistry.activator?.configure(replica, scenePlaced = false)
        NodeRegistry.activate(replica, spawn.networkId, spawn.owner)
        parent.addChild(node)
        node.setMultiplayerAuthority(spawn.owner.value)
        replica.applyDeferredEngineValues(receiveTime)
    }

    fun onDespawn(sender: PlayerId, despawn: Despawn) {
        val replica = NodeRegistry.byNetworkId(despawn.networkId) ?: return
        if (replica.owner != sender) return
        replica.despawning = true
        replica.node.queueFree()
    }

    fun despawnLocally(replica: Replica) {
        replica.despawning = true
        replica.node.queueFree()
    }

    private fun resolveParent(reference: ParentReference): Node? = when (reference) {
        is ParentReference.Networked -> NodeRegistry.byNetworkId(reference.networkId)?.node
        is ParentReference.Path -> tree.root.getNodeOrNull(NodePath(reference.absolutePath))
    }

    private fun encodeSpawnData(replica: Replica, spawnData: Any?): ByteArray {
        val property = replica.spawnDataProperties.firstOrNull()
        if (property == null) {
            require(spawnData == null) { "${replica.node::class.simpleName} declares no spawnData property but data was given" }
            return ByteArray(0)
        }
        requireNotNull(spawnData) { "${replica.node::class.simpleName} needs spawn data for '${property.name}'" }
        property.assign(spawnData)
        @Suppress("UNCHECKED_CAST")
        return ByteWriter().also { writer -> (property.codec as ch.hippmann.godot.replication.core.replication.PropertyCodec<Any?>).write(writer, spawnData) }.toByteArray()
    }

    private fun decodeSpawnData(replica: Replica, bytes: ByteArray) {
        val property = replica.spawnDataProperties.firstOrNull() ?: return
        property.assign(property.codec.read(ByteReader(bytes)))
    }

    private fun OwnerLeavePolicy.toId(): OwnerLeavePolicyId = when (this) {
        OwnerLeavePolicy.Despawn -> OwnerLeavePolicyId.DESPAWN
        OwnerLeavePolicy.TransferToMaster -> OwnerLeavePolicyId.TRANSFER_TO_MASTER
        is OwnerLeavePolicy.TransferTo -> OwnerLeavePolicyId.TRANSFER_TO
    }

    private fun OwnerLeavePolicyId.toPolicy(): OwnerLeavePolicy = when (this) {
        OwnerLeavePolicyId.DESPAWN -> OwnerLeavePolicy.Despawn
        OwnerLeavePolicyId.TRANSFER_TO_MASTER -> OwnerLeavePolicy.TransferToMaster
        OwnerLeavePolicyId.TRANSFER_TO -> OwnerLeavePolicy.TransferToMaster
    }

    private fun OwnershipPolicy.toId(): OwnershipPolicyId = OwnershipPolicyId.entries[ordinal]

    private fun OwnershipPolicyId.toPolicy(): OwnershipPolicy = OwnershipPolicy.entries[ordinal]

    private companion object {
        const val PENDING_SPAWN_TIMEOUT_MILLISECONDS = 5_000L
    }
}
