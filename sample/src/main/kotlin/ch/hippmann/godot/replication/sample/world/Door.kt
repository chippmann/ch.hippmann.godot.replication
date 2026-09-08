package ch.hippmann.godot.replication.sample.world

import ch.hippmann.godot.replication.NetworkConfigured
import ch.hippmann.godot.replication.NodeNetworkConfiguration
import ch.hippmann.godot.replication.OwnershipPolicy
import ch.hippmann.godot.replication.PlayerId
import ch.hippmann.godot.replication.sync.synced
import godot.annotation.Script
import godot.api.Node3D

/** Only players listed in [allowedRequesters] may take the door over; everybody else is refused. */
@Script
class Door : Node3D(), NetworkConfigured {
    var open by synced(false)
    var allowedRequesters: Set<Int> = emptySet()

    override fun configureNetwork(configuration: NodeNetworkConfiguration) {
        configuration.ownershipPolicy = OwnershipPolicy.RequestRequired
        configuration.onOwnershipRequest = { requester: PlayerId -> requester.value in allowedRequesters }
    }
}
