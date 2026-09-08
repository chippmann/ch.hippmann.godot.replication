package ch.hippmann.godot.replication.sync

import godot.core.AABB
import godot.core.Basis
import godot.core.Color
import godot.core.Plane
import godot.core.Quaternion
import godot.core.Rect2
import godot.core.Rect2i
import godot.core.Transform2D
import godot.core.Transform3D
import godot.core.Vector2
import godot.core.Vector2i
import godot.core.Vector3
import godot.core.Vector3i
import godot.core.Vector4
import godot.core.Vector4i
import kotlinx.serialization.modules.SerializersModule

object Vector2Serializer : FloatTupleSerializer<Vector2>("Vector2", 2) {
    override fun components(value: Vector2) = doubleArrayOf(value.x, value.y)
    override fun build(components: DoubleArray) = Vector2(components[0], components[1])
}

object Vector3Serializer : FloatTupleSerializer<Vector3>("Vector3", 3) {
    override fun components(value: Vector3) = doubleArrayOf(value.x, value.y, value.z)
    override fun build(components: DoubleArray) = Vector3(components[0], components[1], components[2])
}

object Vector4Serializer : FloatTupleSerializer<Vector4>("Vector4", 4) {
    override fun components(value: Vector4) = doubleArrayOf(value.x, value.y, value.z, value.w)
    override fun build(components: DoubleArray) = Vector4(components[0], components[1], components[2], components[3])
}

object QuaternionSerializer : FloatTupleSerializer<Quaternion>("Quaternion", 4) {
    override fun components(value: Quaternion) = doubleArrayOf(value.x, value.y, value.z, value.w)
    override fun build(components: DoubleArray) = Quaternion(components[0], components[1], components[2], components[3])
}

object ColorSerializer : FloatTupleSerializer<Color>("Color", 4) {
    override fun components(value: Color) = doubleArrayOf(value.r, value.g, value.b, value.a)
    override fun build(components: DoubleArray) = Color(components[0], components[1], components[2], components[3])
}

object PlaneSerializer : FloatTupleSerializer<Plane>("Plane", 4) {
    override fun components(value: Plane) = doubleArrayOf(value.normal.x, value.normal.y, value.normal.z, value.d)
    override fun build(components: DoubleArray) = Plane(Vector3(components[0], components[1], components[2]), components[3])
}

object Rect2Serializer : FloatTupleSerializer<Rect2>("Rect2", 4) {
    override fun components(value: Rect2) = doubleArrayOf(value.position.x, value.position.y, value.size.x, value.size.y)
    override fun build(components: DoubleArray) = Rect2(Vector2(components[0], components[1]), Vector2(components[2], components[3]))
}

object AABBSerializer : FloatTupleSerializer<AABB>("AABB", 6) {
    override fun components(value: AABB) =
        doubleArrayOf(value.position.x, value.position.y, value.position.z, value.size.x, value.size.y, value.size.z)

    override fun build(components: DoubleArray) =
        AABB(Vector3(components[0], components[1], components[2]), Vector3(components[3], components[4], components[5]))
}

object BasisSerializer : FloatTupleSerializer<Basis>("Basis", 9) {
    override fun components(value: Basis) =
        doubleArrayOf(value.x.x, value.x.y, value.x.z, value.y.x, value.y.y, value.y.z, value.z.x, value.z.y, value.z.z)

    override fun build(components: DoubleArray) = Basis(
        Vector3(components[0], components[1], components[2]),
        Vector3(components[3], components[4], components[5]),
        Vector3(components[6], components[7], components[8]),
    )
}

object Transform2DSerializer : FloatTupleSerializer<Transform2D>("Transform2D", 6) {
    override fun components(value: Transform2D) =
        doubleArrayOf(value.x.x, value.x.y, value.y.x, value.y.y, value.origin.x, value.origin.y)

    override fun build(components: DoubleArray) = Transform2D(
        Vector2(components[0], components[1]),
        Vector2(components[2], components[3]),
        Vector2(components[4], components[5]),
    )
}

object Transform3DSerializer : FloatTupleSerializer<Transform3D>("Transform3D", 12) {
    override fun components(value: Transform3D) = BasisSerializer.components(value.basis) +
        doubleArrayOf(value.origin.x, value.origin.y, value.origin.z)

    override fun build(components: DoubleArray) = Transform3D(
        BasisSerializer.build(components.copyOfRange(0, 9)),
        Vector3(components[9], components[10], components[11]),
    )
}

object Vector2iSerializer : IntTupleSerializer<Vector2i>("Vector2i", 2) {
    override fun components(value: Vector2i) = intArrayOf(value.x, value.y)
    override fun build(components: IntArray) = Vector2i(components[0], components[1])
}

object Vector3iSerializer : IntTupleSerializer<Vector3i>("Vector3i", 3) {
    override fun components(value: Vector3i) = intArrayOf(value.x, value.y, value.z)
    override fun build(components: IntArray) = Vector3i(components[0], components[1], components[2])
}

object Vector4iSerializer : IntTupleSerializer<Vector4i>("Vector4i", 4) {
    override fun components(value: Vector4i) = intArrayOf(value.x, value.y, value.z, value.w)
    override fun build(components: IntArray) = Vector4i(components[0], components[1], components[2], components[3])
}

object Rect2iSerializer : IntTupleSerializer<Rect2i>("Rect2i", 4) {
    override fun components(value: Rect2i) = intArrayOf(value.position.x, value.position.y, value.size.x, value.size.y)
    override fun build(components: IntArray) = Rect2i(Vector2i(components[0], components[1]), Vector2i(components[2], components[3]))
}

/** Register with `@Contextual` or `@file:UseContextualSerialization(Vector3::class)` in user types. */
val GodotSerializersModule: SerializersModule = SerializersModule {
    contextual(Vector2::class, Vector2Serializer)
    contextual(Vector3::class, Vector3Serializer)
    contextual(Vector4::class, Vector4Serializer)
    contextual(Quaternion::class, QuaternionSerializer)
    contextual(Color::class, ColorSerializer)
    contextual(Plane::class, PlaneSerializer)
    contextual(Rect2::class, Rect2Serializer)
    contextual(AABB::class, AABBSerializer)
    contextual(Basis::class, BasisSerializer)
    contextual(Transform2D::class, Transform2DSerializer)
    contextual(Transform3D::class, Transform3DSerializer)
    contextual(Vector2i::class, Vector2iSerializer)
    contextual(Vector3i::class, Vector3iSerializer)
    contextual(Vector4i::class, Vector4iSerializer)
    contextual(Rect2i::class, Rect2iSerializer)
}
