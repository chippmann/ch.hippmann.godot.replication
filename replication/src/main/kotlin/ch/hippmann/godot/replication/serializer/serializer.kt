package ch.hippmann.godot.replication.serializer

import ch.hippmann.godot.replication.SerializedData
import ch.hippmann.godot.replication.serializer.bridge.NodePathSerializer
import ch.hippmann.godot.replication.serializer.bridge.StringNameSerializer
import ch.hippmann.godot.replication.serializer.math.AABBSerializer
import ch.hippmann.godot.replication.serializer.math.BasisSerializer
import ch.hippmann.godot.replication.serializer.math.ColorSerializer
import ch.hippmann.godot.replication.serializer.math.PlaneSerializer
import ch.hippmann.godot.replication.serializer.math.ProjectionSerializer
import ch.hippmann.godot.replication.serializer.math.QuaternionSerializer
import ch.hippmann.godot.replication.serializer.math.Rect2Serializer
import ch.hippmann.godot.replication.serializer.math.Rect2iSerializer
import ch.hippmann.godot.replication.serializer.math.Transform2DSerializer
import ch.hippmann.godot.replication.serializer.math.Transform3DSerializer
import ch.hippmann.godot.replication.serializer.math.Vector2Serializer
import ch.hippmann.godot.replication.serializer.math.Vector2iSerializer
import ch.hippmann.godot.replication.serializer.math.Vector3Serializer
import ch.hippmann.godot.replication.serializer.math.Vector3iSerializer
import ch.hippmann.godot.replication.serializer.math.Vector4Serializer
import ch.hippmann.godot.replication.serializer.math.Vector4iSerializer
import godot.core.AABB
import godot.core.Basis
import godot.core.Color
import godot.core.NodePath
import godot.core.Plane
import godot.core.Projection
import godot.core.Quaternion
import godot.core.Rect2
import godot.core.Rect2i
import godot.core.StringName
import godot.core.Transform2D
import godot.core.Transform3D
import godot.core.Vector2
import godot.core.Vector2i
import godot.core.Vector3
import godot.core.Vector3i
import godot.core.Vector4
import godot.core.Vector4i
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@PublishedApi
internal val json = Json {
    isLenient = true
    ignoreUnknownKeys = false
}

@PublishedApi
internal inline fun <reified T> T.serialize(): SerializedData = when (T::class) {
    NodePath::class -> json.encodeToString(NodePathSerializer(), this as NodePath)
    StringName::class -> json.encodeToString(StringNameSerializer(), this as StringName)
    AABB::class -> json.encodeToString(AABBSerializer(), this as AABB)
    Basis::class -> json.encodeToString(BasisSerializer(), this as Basis)
    Color::class -> json.encodeToString(ColorSerializer(), this as Color)
    Plane::class -> json.encodeToString(PlaneSerializer(), this as Plane)
    Projection::class -> json.encodeToString(ProjectionSerializer(), this as Projection)
    Quaternion::class -> json.encodeToString(QuaternionSerializer(), this as Quaternion)
    Rect2i::class -> json.encodeToString(Rect2iSerializer(), this as Rect2i)
    Rect2::class -> json.encodeToString(Rect2Serializer(), this as Rect2)
    Transform2D::class -> json.encodeToString(Transform2DSerializer(), this as Transform2D)
    Transform3D::class -> json.encodeToString(Transform3DSerializer(), this as Transform3D)
    Vector2i::class -> json.encodeToString(Vector2iSerializer(), this as Vector2i)
    Vector2::class -> json.encodeToString(Vector2Serializer(), this as Vector2)
    Vector3i::class -> json.encodeToString(Vector3iSerializer(), this as Vector3i)
    Vector3::class -> json.encodeToString(Vector3Serializer(), this as Vector3)
    Vector4i::class -> json.encodeToString(Vector4iSerializer(), this as Vector4i)
    Vector4::class -> json.encodeToString(Vector4Serializer(), this as Vector4)
    else -> json.encodeToString<T>(this)
}

@PublishedApi
internal inline fun <reified T> SerializedData.deserialize(): T = when (T::class) {
    NodePath::class -> json.decodeFromString(NodePathSerializer(), this) as T
    StringName::class -> json.decodeFromString(StringNameSerializer(), this) as T
    AABB::class -> json.decodeFromString(AABBSerializer(), this) as T
    Basis::class -> json.decodeFromString(BasisSerializer(), this) as T
    Color::class -> json.decodeFromString(ColorSerializer(), this) as T
    Plane::class -> json.decodeFromString(PlaneSerializer(), this) as T
    Projection::class -> json.decodeFromString(ProjectionSerializer(), this) as T
    Quaternion::class -> json.decodeFromString(QuaternionSerializer(), this) as T
    Rect2i::class -> json.decodeFromString(Rect2iSerializer(), this) as T
    Rect2::class -> json.decodeFromString(Rect2Serializer(), this) as T
    Transform2D::class -> json.decodeFromString(Transform2DSerializer(), this) as T
    Transform3D::class -> json.decodeFromString(Transform3DSerializer(), this) as T
    Vector2i::class -> json.decodeFromString(Vector2iSerializer(), this) as T
    Vector2::class -> json.decodeFromString(Vector2Serializer(), this) as T
    Vector3i::class -> json.decodeFromString(Vector3iSerializer(), this) as T
    Vector3::class -> json.decodeFromString(Vector3Serializer(), this) as T
    Vector4i::class -> json.decodeFromString(Vector4iSerializer(), this) as T
    Vector4::class -> json.decodeFromString(Vector4Serializer(), this) as T
    else -> json.decodeFromString<T>(this)
}