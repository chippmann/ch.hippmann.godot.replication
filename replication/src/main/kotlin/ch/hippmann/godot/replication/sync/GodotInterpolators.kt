package ch.hippmann.godot.replication.sync

import ch.hippmann.godot.replication.core.replication.Interpolator
import godot.core.Basis
import godot.core.Color
import godot.core.Quaternion
import godot.core.Transform2D
import godot.core.Transform3D
import godot.core.Vector2
import godot.core.Vector3
import godot.core.Vector4
import kotlin.math.roundToInt
import kotlin.reflect.KClass

object GodotInterpolators {
    val float: Interpolator<Float> = Interpolator { from, to, weight -> (from + (to - from) * weight).toFloat() }
    val double: Interpolator<Double> = Interpolator { from, to, weight -> from + (to - from) * weight }
    val int: Interpolator<Int> = Interpolator { from, to, weight -> (from + (to - from) * weight).roundToInt() }
    val vector2: Interpolator<Vector2> = Interpolator { from, to, weight -> from.lerp(to, weight) }
    val vector3: Interpolator<Vector3> = Interpolator { from, to, weight -> from.lerp(to, weight) }
    val vector4: Interpolator<Vector4> = Interpolator { from, to, weight -> from.lerp(to, weight) }
    val color: Interpolator<Color> = Interpolator { from, to, weight -> from.lerp(to, weight) }
    val quaternion: Interpolator<Quaternion> = Interpolator { from, to, weight -> from.slerp(to, weight.coerceIn(0.0, 1.0)) }
    val basis: Interpolator<Basis> = Interpolator { from, to, weight -> from.slerp(to, weight.coerceIn(0.0, 1.0)) }
    val transform3D: Interpolator<Transform3D> = Interpolator { from, to, weight ->
        Transform3D(from.basis.slerp(to.basis, weight.coerceIn(0.0, 1.0)), from.origin.lerp(to.origin, weight))
    }
    val transform2D: Interpolator<Transform2D> = Interpolator { from, to, weight -> from.interpolateWith(to, weight.coerceIn(0.0, 1.0)) }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> forClass(type: KClass<T>): Interpolator<T>? = when (type) {
        Float::class -> float
        Double::class -> double
        Int::class -> int
        Vector2::class -> vector2
        Vector3::class -> vector3
        Vector4::class -> vector4
        Color::class -> color
        Quaternion::class -> quaternion
        Basis::class -> basis
        Transform3D::class -> transform3D
        Transform2D::class -> transform2D
        else -> null
    } as Interpolator<T>?
}
