package ch.hippmann.godot.replication.rpc

import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.core.session.PlayerId
import godot.api.Node

sealed interface Target {
    /** Every other member. */
    data object All : Target

    data object Master : Target

    data class Player(val id: PlayerId) : Target

    data class Players(val ids: Set<PlayerId>) : Target

    data class Owner(val node: Node) : Target

    fun resolve(): Set<PlayerId> = when (this) {
        All -> Network.session.value?.connected.orEmpty() - Network.localPlayerId
        Master -> setOf(Network.master)
        is Player -> setOf(id)
        is Players -> ids
        is Owner -> setOf(Network.ownerOf(node))
    }
}
