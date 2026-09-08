package ch.hippmann.godot.replication.sync

import ch.hippmann.godot.replication.NetworkEvent
import ch.hippmann.godot.replication.OwnerLeavePolicy
import ch.hippmann.godot.replication.OwnershipPolicy
import ch.hippmann.godot.replication.core.session.NetworkId
import ch.hippmann.godot.replication.core.session.PlayerId
import ch.hippmann.godot.replication.core.wire.OwnershipChanged
import ch.hippmann.godot.replication.core.wire.OwnershipEntry
import ch.hippmann.godot.replication.core.wire.OwnershipReply
import ch.hippmann.godot.replication.core.wire.OwnershipRequest
import ch.hippmann.godot.replication.core.wire.WireMessage
import ch.hippmann.godot.replication.session.SessionRuntime
import godot.api.Node
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** Ownership moves by the owner's or the master's decision; every peer mirrors it into Godot's authority. */
internal class OwnershipService(private val session: SessionRuntime) {
    private val pendingRequests = HashMap<Long, CompletableDeferred<Boolean>>()

    fun transfer(node: Node, to: PlayerId) {
        val replica = requireNotNull(NodeRegistry.replicaOf(node)) { "${node.name} is not a networked node" }
        val networkId = requireNotNull(replica.networkId) { "${node.name} is not active" }
        check(replica.isOwnedLocally || session.isMaster) { "Only the owner or the master can transfer ${node.name}" }
        check(session.membership?.contains(to) == true) { "Player ${to.value} is not a member" }
        val epoch = session.membership?.epoch ?: return
        apply(replica, to)
        session.transport.broadcastMessage(OwnershipChanged(epoch, listOf(OwnershipEntry(networkId, to))))
    }

    suspend fun request(node: Node, timeoutMilliseconds: Long): Boolean {
        val replica = NodeRegistry.replicaOf(node) ?: return false
        val networkId = replica.networkId ?: return false
        if (replica.isOwnedLocally) return true
        if (replica.ownershipPolicy == OwnershipPolicy.Fixed) return false
        val deferred = CompletableDeferred<Boolean>()
        pendingRequests[networkId.value] = deferred
        session.transport.sendMessage(replica.owner, OwnershipRequest(networkId))
        val granted = withTimeoutOrNull(timeoutMilliseconds) { deferred.await() } ?: false
        pendingRequests.remove(networkId.value)
        return granted
    }

    fun onMessage(sender: PlayerId, message: WireMessage): Boolean {
        when (message) {
            is OwnershipChanged -> onChanged(sender, message)
            is OwnershipRequest -> onRequest(sender, message)
            is OwnershipReply -> pendingRequests[message.networkId.value]?.complete(message.granted)
            else -> return false
        }
        return true
    }

    /** Runs on every member for [Despawn]; the master of the new epoch resolves and broadcasts the transfers. */
    fun onMemberLeft(player: PlayerId) {
        val membership = session.membership ?: return
        val transfers = mutableListOf<OwnershipEntry>()
        for (replica in NodeRegistry.ownedBy(player)) {
            val networkId = replica.networkId ?: continue
            when (val policy = replica.ownerLeavePolicy) {
                OwnerLeavePolicy.Despawn -> session.replication.spawnService.despawnLocally(replica)
                OwnerLeavePolicy.TransferToMaster -> if (session.isMaster) transfers += OwnershipEntry(networkId, membership.master)
                is OwnerLeavePolicy.TransferTo -> if (session.isMaster) {
                    val candidates = membership.members.keys.sortedBy { id -> id.value }
                    transfers += OwnershipEntry(networkId, policy.select(candidates, replica.node))
                }
            }
        }
        if (transfers.isEmpty()) return
        for (entry in transfers) NodeRegistry.byNetworkId(entry.networkId)?.let { replica -> apply(replica, entry.newOwner) }
        session.transport.broadcastMessage(OwnershipChanged(membership.epoch, transfers))
    }

    /** Godot's default authority is peer 1, which never exists here; the master owns everything not owned otherwise. */
    fun mirrorMasterAuthority(root: Node) {
        val master = session.membership?.master ?: return
        root.setMultiplayerAuthority(master.value, recursive = true)
        for (replica in NodeRegistry.active) replica.node.setMultiplayerAuthority(replica.owner.value, recursive = true)
    }

    private fun onChanged(sender: PlayerId, message: OwnershipChanged) {
        val membership = session.membership ?: return
        if (message.epoch.value < membership.epoch.value) return
        for (entry in message.entries) {
            val replica = NodeRegistry.byNetworkId(entry.networkId) ?: continue
            val allowed = sender == replica.owner || sender == membership.master
            if (!allowed || !membership.contains(entry.newOwner)) continue
            apply(replica, entry.newOwner)
        }
    }

    private fun onRequest(sender: PlayerId, request: OwnershipRequest) {
        val replica = NodeRegistry.byNetworkId(request.networkId)
        if (replica == null || !replica.isOwnedLocally) {
            session.transport.sendMessage(sender, OwnershipReply(request.networkId, granted = false))
            return
        }
        val granted = when (replica.ownershipPolicy) {
            OwnershipPolicy.Fixed -> false
            OwnershipPolicy.Transferable -> true
            OwnershipPolicy.RequestRequired -> replica.onOwnershipRequest(sender)
        }
        if (granted) transfer(replica.node, sender)
        session.transport.sendMessage(sender, OwnershipReply(request.networkId, granted))
    }

    private fun apply(replica: Replica, newOwner: PlayerId) {
        val previous = replica.owner
        if (previous == newOwner) return
        replica.owner = newOwner
        replica.node.setMultiplayerAuthority(newOwner.value, recursive = true)
        if (newOwner == NodeRegistry.localPlayerId) {
            replica.dirtyMask = replica.schema?.fullMask ?: 0
            replica.properties.forEach { property -> property.lastChangedTick = NodeRegistry.currentTick }
            replica.properties.forEach { property -> property.buffer?.clear() }
        }
        session.replication.forgetNodeForPeers(replica.networkId ?: return)
        session.emit(NetworkEvent.OwnershipChanged(replica.node, previous, newOwner))
    }
}
