package ch.hippmann.godot.replication

import godot.api.Node

sealed interface OwnerLeavePolicy {
    data object Despawn : OwnerLeavePolicy

    data object TransferToMaster : OwnerLeavePolicy

    /** [select] must be a pure function of its arguments: the master evaluates it and broadcasts the result. */
    data class TransferTo(val select: (candidates: List<PlayerId>, node: Node) -> PlayerId) : OwnerLeavePolicy
}

enum class OwnershipPolicy {
    Fixed,
    Transferable,
    RequestRequired,
}

data class SpawnOptions(
    val ownerLeavePolicy: OwnerLeavePolicy = OwnerLeavePolicy.Despawn,
    val ownershipPolicy: OwnershipPolicy = OwnershipPolicy.Transferable,
    val name: String? = null,
)
