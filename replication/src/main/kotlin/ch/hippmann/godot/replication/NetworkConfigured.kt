package ch.hippmann.godot.replication

import ch.hippmann.godot.replication.sync.InterestFilter

/** Optional per node settings; a networked node class implements this to configure itself without reflection. */
interface NetworkConfigured {
    fun configureNetwork(configuration: NodeNetworkConfiguration)
}

class NodeNetworkConfiguration internal constructor() {
    /** Applies to scene placed nodes; spawned nodes take their policies from [SpawnOptions]. */
    var ownerLeavePolicy: OwnerLeavePolicy = OwnerLeavePolicy.TransferToMaster
    var ownershipPolicy: OwnershipPolicy = OwnershipPolicy.Transferable
    var onOwnershipRequest: (requester: PlayerId) -> Boolean = { true }
    var interestFilter: InterestFilter = InterestFilter.Always
}
