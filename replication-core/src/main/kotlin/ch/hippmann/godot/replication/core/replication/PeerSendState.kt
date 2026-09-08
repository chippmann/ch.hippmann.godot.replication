package ch.hippmann.godot.replication.core.replication

import ch.hippmann.godot.replication.core.session.NetworkId
import ch.hippmann.godot.replication.core.session.PlayerId

/** Which of the local player's nodes each peer already holds a full state of. */
public class PeerSendState {
    private val knownNodes = HashMap<PlayerId, MutableSet<NetworkId>>()

    public fun knows(peer: PlayerId, networkId: NetworkId): Boolean = knownNodes[peer]?.contains(networkId) == true

    public fun markKnown(peer: PlayerId, networkId: NetworkId) {
        knownNodes.getOrPut(peer) { HashSet() }.add(networkId)
    }

    public fun forgetNode(networkId: NetworkId) {
        knownNodes.values.forEach { nodes -> nodes.remove(networkId) }
    }

    public fun forgetPeer(peer: PlayerId) {
        knownNodes.remove(peer)
    }

    public fun forget(peer: PlayerId, networkId: NetworkId) {
        knownNodes[peer]?.remove(networkId)
    }
}
