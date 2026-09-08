package ch.hippmann.godot.replication.sample.game

import kotlinx.serialization.Serializable

/** Sent by a projectile's owner to the owner of the player it hit; only the owner changes its own health. */
@Serializable
data class HitMessage(val damage: Int, val shooter: Int)
