package ch.hippmann.godot.replication.sample.world

import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.rpc.Target
import ch.hippmann.godot.replication.sample.game.HitMessage
import ch.hippmann.godot.replication.sync.spawnData
import ch.hippmann.godot.replication.sync.synced
import godot.annotation.Script
import godot.api.Node3D
import godot.core.Vector3

/** Flies a straight line every peer computes from the spawn data alone; only the owner judges hits and despawns it. */
@Script
class Projectile : Node3D() {
    var velocity by synced(Vector3.ZERO)
    var shooter by synced(0)
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
        age += delta
        if (shot.direction != Vector3.ZERO) position = shot.origin + shot.direction.normalized() * (SPEED * age)
        if (!Network.isOwner(this)) return
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
