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

// Stateless serializers; hoisted as `@PublishedApi internal val`s so the inline
// serialize()/deserialize() bodies don't allocate a fresh instance per property tick.
@PublishedApi internal val nodePathSerializer = NodePathSerializer()
@PublishedApi internal val stringNameSerializer = StringNameSerializer()
@PublishedApi internal val aabbSerializer = AABBSerializer()
@PublishedApi internal val basisSerializer = BasisSerializer()
@PublishedApi internal val colorSerializer = ColorSerializer()
@PublishedApi internal val planeSerializer = PlaneSerializer()
@PublishedApi internal val projectionSerializer = ProjectionSerializer()
@PublishedApi internal val quaternionSerializer = QuaternionSerializer()
@PublishedApi internal val rect2iSerializer = Rect2iSerializer()
@PublishedApi internal val rect2Serializer = Rect2Serializer()
@PublishedApi internal val transform2DSerializer = Transform2DSerializer()
@PublishedApi internal val transform3DSerializer = Transform3DSerializer()
@PublishedApi internal val vector2iSerializer = Vector2iSerializer()
@PublishedApi internal val vector2Serializer = Vector2Serializer()
@PublishedApi internal val vector3iSerializer = Vector3iSerializer()
@PublishedApi internal val vector3Serializer = Vector3Serializer()
@PublishedApi internal val vector4iSerializer = Vector4iSerializer()
@PublishedApi internal val vector4Serializer = Vector4Serializer()

@PublishedApi
internal inline fun <reified T> T.serialize(): SerializedData = when (T::class) {
    NodePath::class -> json.encodeToString(nodePathSerializer, this as NodePath)
    StringName::class -> json.encodeToString(stringNameSerializer, this as StringName)
    AABB::class -> json.encodeToString(aabbSerializer, this as AABB)
    Basis::class -> json.encodeToString(basisSerializer, this as Basis)
    Color::class -> json.encodeToString(colorSerializer, this as Color)
    Plane::class -> json.encodeToString(planeSerializer, this as Plane)
    Projection::class -> json.encodeToString(projectionSerializer, this as Projection)
    Quaternion::class -> json.encodeToString(quaternionSerializer, this as Quaternion)
    Rect2i::class -> json.encodeToString(rect2iSerializer, this as Rect2i)
    Rect2::class -> json.encodeToString(rect2Serializer, this as Rect2)
    Transform2D::class -> json.encodeToString(transform2DSerializer, this as Transform2D)
    Transform3D::class -> json.encodeToString(transform3DSerializer, this as Transform3D)
    Vector2i::class -> json.encodeToString(vector2iSerializer, this as Vector2i)
    Vector2::class -> json.encodeToString(vector2Serializer, this as Vector2)
    Vector3i::class -> json.encodeToString(vector3iSerializer, this as Vector3i)
    Vector3::class -> json.encodeToString(vector3Serializer, this as Vector3)
    Vector4i::class -> json.encodeToString(vector4iSerializer, this as Vector4i)
    Vector4::class -> json.encodeToString(vector4Serializer, this as Vector4)
    else -> json.encodeToString<T>(this)
}

@PublishedApi
internal inline fun <reified T> SerializedData.deserialize(): T = when (T::class) {
    NodePath::class -> json.decodeFromString(nodePathSerializer, this) as T
    StringName::class -> json.decodeFromString(stringNameSerializer, this) as T
    AABB::class -> json.decodeFromString(aabbSerializer, this) as T
    Basis::class -> json.decodeFromString(basisSerializer, this) as T
    Color::class -> json.decodeFromString(colorSerializer, this) as T
    Plane::class -> json.decodeFromString(planeSerializer, this) as T
    Projection::class -> json.decodeFromString(projectionSerializer, this) as T
    Quaternion::class -> json.decodeFromString(quaternionSerializer, this) as T
    Rect2i::class -> json.decodeFromString(rect2iSerializer, this) as T
    Rect2::class -> json.decodeFromString(rect2Serializer, this) as T
    Transform2D::class -> json.decodeFromString(transform2DSerializer, this) as T
    Transform3D::class -> json.decodeFromString(transform3DSerializer, this) as T
    Vector2i::class -> json.decodeFromString(vector2iSerializer, this) as T
    Vector2::class -> json.decodeFromString(vector2Serializer, this) as T
    Vector3i::class -> json.decodeFromString(vector3iSerializer, this) as T
    Vector3::class -> json.decodeFromString(vector3Serializer, this) as T
    Vector4i::class -> json.decodeFromString(vector4iSerializer, this) as T
    Vector4::class -> json.decodeFromString(vector4Serializer, this) as T
    else -> json.decodeFromString<T>(this)
}