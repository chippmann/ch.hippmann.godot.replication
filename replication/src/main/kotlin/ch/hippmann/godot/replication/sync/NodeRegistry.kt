package ch.hippmann.godot.replication.sync

import ch.hippmann.godot.replication.core.replication.SchemaHash
import ch.hippmann.godot.replication.core.session.NetworkId
import ch.hippmann.godot.replication.core.session.PlayerId
import godot.api.Node
import godot.core.lambdaCallable0
import java.util.IdentityHashMap

/** Replicas by node and by network id; nodes attach on tree entry and activate as soon as a session is running. */
object NodeRegistry {
    private val replicas = IdentityHashMap<Node, Replica>()
    private val byNetworkId = HashMap<Long, Replica>()
    private val pending = LinkedHashSet<Replica>()

    internal var localPlayerId: PlayerId = PlayerId.NONE
    internal var currentTick: Int = 0
    internal var activator: ReplicaActivator? = null

    val active: Collection<Replica>
        get() = byNetworkId.values

    fun replicaOf(node: Node): Replica? = replicas[node]

    fun byNetworkId(networkId: NetworkId): Replica? = byNetworkId[networkId.value]

    @PublishedApi internal fun replicaFor(node: Node): Replica = replicas.getOrPut(node) {
        Replica(node).also { replica ->
            node.treeEntered.connect(lambdaCallable0<Unit> { attach(replica) })
            node.treeExiting.connect(lambdaCallable0<Unit> { detach(replica) })
        }
    }

    internal fun ownedBy(player: PlayerId): List<Replica> = byNetworkId.values.filter { replica -> replica.owner == player }

    internal fun activatePending() {
        val activator = activator ?: return
        for (replica in pending.toList()) activator.activate(replica)
        pending.clear()
    }

    internal fun activate(replica: Replica, networkId: NetworkId, owner: PlayerId) {
        replica.networkId = networkId
        replica.owner = owner
        replica.active = true
        replica.freezeSchema()
        byNetworkId[networkId.value] = replica
        pending.remove(replica)
    }

    internal fun deactivateAll() {
        for (replica in byNetworkId.values) {
            replica.active = false
            replica.dirtyMask = 0
            if (replica.networkId?.isScenePlaced == true) {
                replica.networkId = null
                pending += replica
            }
        }
        byNetworkId.clear()
        localPlayerId = PlayerId.NONE
    }

    internal fun scenePlacedId(node: Node): NetworkId = NetworkId.scenePlaced(SchemaHash.ofString(node.getPath().path))

    private fun attach(replica: Replica) {
        val activator = activator
        if (activator == null) {
            pending += replica
        } else {
            activator.activate(replica)
        }
    }

    private fun detach(replica: Replica) {
        replica.networkId?.let { id -> if (byNetworkId[id.value] === replica) byNetworkId.remove(id.value) }
        replica.active = false
        replica.dirtyMask = 0
        pending.remove(replica)
        // A node on its way to be freed never re-enters the tree; keeping it pending would activate a dead object later.
        if (replica.despawning || replica.spawnRecord != null || replica.node.isBeingFreed()) {
            replicas.remove(replica.node)
        } else {
            replica.networkId = null
            pending += replica
        }
    }
}

/** `queue_free` flags only the node it was called on, so a child of a freed level looks alive until its ancestors are checked. */
private fun Node.isBeingFreed(): Boolean = generateSequence(this) { node -> node.getParent() }.any { node -> node.isQueuedForDeletion() }

internal interface ReplicaActivator {
    fun activate(replica: Replica)

    /** Applies the node's [ch.hippmann.godot.replication.NetworkConfigured] settings; spawned nodes get this before activation. */
    fun configure(replica: Replica, scenePlaced: Boolean)
}
