package ch.hippmann.godot.replication.sample.world

import ch.hippmann.godot.replication.sync.Vector3Serializer
import godot.core.Vector3
import kotlinx.serialization.Serializable

@Serializable
data class Shot(
    @Serializable(with = Vector3Serializer::class) val origin: Vector3,
    @Serializable(with = Vector3Serializer::class) val direction: Vector3,
)
