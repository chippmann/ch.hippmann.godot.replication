package ch.hippmann.godot.replication.sample.world

import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.rpc.Target
import ch.hippmann.godot.replication.sample.game.HitMessage
import ch.hippmann.godot.replication.sync.spawnData
import ch.hippmann.godot.replication.sync.synced
import godot.annotation.Script
import godot.api.Node3D
import godot.core.Vector3

@Script
class Projectile : Node3D() {
    var velocity by synced(Vector3.ZERO)
    var shooter by synced(0)
    val motion = synced(::position) { unreliable(); continuous(); interpolate() }
    val shot by spawnData<Shot>()

    private var age = 0.0

    override fun _ready() {
        position = shot.origin
        if (Network.isOwner(this) && shot.direction != Vector3.ZERO) {
            velocity = shot.direction.normalized() * SPEED
            shooter = Network.localPlayerId.value
        }
    }

    override fun _physicsProcess(delta: Double) {
        if (!Network.isOwner(this)) return
        position += velocity * delta
        age += delta
        val victim = Player.byOwner.values.firstOrNull { player -> Network.ownerOf(player).value != shooter && player.position.distanceTo(position) < HIT_RADIUS }
        if (victim != null) {
            Network.send(HitMessage.serializer(), HitMessage(DAMAGE, shooter), Target.Owner(victim), reliable = true)
        }
        if (victim != null || age > LIFETIME_SECONDS) Network.despawn(this)
    }

    companion object {
        const val SPEED = 16.0
        const val DAMAGE = 25
        const val HIT_RADIUS = 1.0
        const val LIFETIME_SECONDS = 2.5
    }
}
