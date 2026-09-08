package ch.hippmann.godot.replication.sync

import ch.hippmann.godot.replication.NetworkConfigured
import ch.hippmann.godot.replication.NodeNetworkConfiguration
import ch.hippmann.godot.replication.core.session.NetworkId
import ch.hippmann.godot.replication.core.session.PlayerId
import ch.hippmann.godot.replication.core.wire.Despawn
import ch.hippmann.godot.replication.core.wire.MessageType
import ch.hippmann.godot.replication.session.SessionRuntime
import godot.api.Engine

/** Per session glue between the transport, the registry and the frame loop. */
internal class ReplicationRuntime(private val session: SessionRuntime) : ReplicaActivator {
    private val tick = ReplicationTick(session.transport, session.configuration.tickRate)
    private val receiver = StateReceiver(session.transport)
    private val applier = InterpolationApplier()
    private val physicsStepsPerTick = maxOf(1, Engine.physicsTicksPerSecond / session.configuration.tickRate)
    private var physicsSteps = 0

    val spawnService = SpawnService(session.transport, session.manager.getTree() ?: error("No scene tree"))
    val ownership = OwnershipService(session)
    val snapshot = SnapshotService(session)

    fun start() {
        NodeRegistry.localPlayerId = session.localPlayerId
        NodeRegistry.activator = this
        NodeRegistry.activatePending()
    }

    fun stop() {
        NodeRegistry.activator = null
        NodeRegistry.deactivateAll()
    }

    override fun activate(replica: Replica) {
        val scenePlaced = replica.networkId == null
        val networkId = replica.networkId ?: NodeRegistry.scenePlacedId(replica.node)
        val owner = if (scenePlaced) session.membership?.master ?: PlayerId.NONE else replica.owner
        configure(replica, scenePlaced)
        NodeRegistry.activate(replica, networkId, owner)
        replica.node.setMultiplayerAuthority(owner.value, recursive = true)
    }

    override fun configure(replica: Replica, scenePlaced: Boolean) {
        replica.interpolationDelayMilliseconds = session.configuration.effectiveInterpolationDelayMilliseconds.toLong()
        val node = replica.node as? NetworkConfigured ?: return
        val configuration = NodeNetworkConfiguration().also(node::configureNetwork)
        if (scenePlaced) {
            replica.ownerLeavePolicy = configuration.ownerLeavePolicy
            replica.ownershipPolicy = configuration.ownershipPolicy
        }
        replica.onOwnershipRequest = configuration.onOwnershipRequest
        replica.interestFilter = configuration.interestFilter
    }

    fun forgetNodeForPeers(networkId: NetworkId): Unit = tick.forgetNode(networkId)

    fun onMasterChanged() {
        session.manager.getTree()?.root?.let(ownership::mirrorMasterAuthority)
    }

    fun physicsStep() {
        physicsSteps++
        if (physicsSteps % physicsStepsPerTick != 0) return
        tick.run(session.transport.connectedPlayers)
    }

    fun frame() {
        applier.frame()
        spawnService.retryPendingSpawns()
    }

    fun onStatePacket(sender: PlayerId, type: MessageType, bytes: ByteArray) {
        when (type) {
            MessageType.SPAWN -> spawnService.onSpawn(sender, bytes)
            MessageType.STATE_DELTA, MessageType.STATE_RELIABLE, MessageType.STATE_FULL -> receiver.onPacket(sender, bytes)
            else -> Unit
        }
    }

    fun onDespawn(sender: PlayerId, despawn: Despawn): Unit = spawnService.onDespawn(sender, despawn)

    fun onMemberLeft(player: PlayerId) {
        receiver.forgetPeer(player)
        tick.forgetPeer(player)
        ownership.onMemberLeft(player)
    }
}
