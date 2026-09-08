package ch.hippmann.godot.replication.sample.world

import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.NetworkConfigured
import ch.hippmann.godot.replication.NodeNetworkConfiguration
import ch.hippmann.godot.replication.OwnershipPolicy
import ch.hippmann.godot.replication.PlayerId
import ch.hippmann.godot.replication.sync.synced
import godot.annotation.Script
import godot.api.Label3D
import godot.api.Node3D
import godot.core.Vector3
import godot.extension.api.getNodeAs

/** Taking the door over needs the owner's consent: nobody by default, everyone when [allowedRequesters] is null. */
@Script
class Door : Node3D(), NetworkConfigured {
    var open by synced(false)
    var allowedRequesters: Set<Int>? = emptySet()

    private var panel: Node3D? = null
    private var tag: Label3D? = null

    override fun configureNetwork(configuration: NodeNetworkConfiguration) {
        configuration.ownershipPolicy = OwnershipPolicy.RequestRequired
        configuration.onOwnershipRequest = { requester: PlayerId -> allowedRequesters?.contains(requester.value) ?: true }
    }

    override fun _ready() {
        panel = getNodeAs<Node3D>("Panel")
        tag = getNodeAs<Label3D>("Tag")
        all += this
    }

    override fun _exitTree() {
        all -= this
    }

    override fun _process(delta: Double) {
        val panel = panel ?: return
        val target = if (open) OPEN_HEIGHT else CLOSED_HEIGHT
        panel.position = Vector3(0.0, panel.position.y + (target - panel.position.y) * minOf(1.0, delta * SLIDE_SPEED), 0.0)
        tag?.text = "${if (open) "open" else "closed"}, owner ${Network.ownerOf(this).value}"
    }

    companion object {
        const val CLOSED_HEIGHT = 1.8
        const val OPEN_HEIGHT = 5.4
        const val SLIDE_SPEED = 4.0
        val all = HashSet<Door>()
    }
}
