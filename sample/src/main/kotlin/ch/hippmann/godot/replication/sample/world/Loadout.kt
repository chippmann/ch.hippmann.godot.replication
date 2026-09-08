package ch.hippmann.godot.replication.sample.world

import kotlinx.serialization.Serializable

@Serializable
data class Loadout(val weapon: String, val credits: Int, val startX: Double = 0.0)
