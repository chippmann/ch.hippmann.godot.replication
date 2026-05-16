package ch.hippmann.godot.replication.integrationtests.fixtures

import ch.hippmann.godot.replication.syncConfig
import godot.annotation.RegisterClass
import godot.api.Node
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
import godot.core.asNodePath
import godot.core.asStringName

/**
 * One mutable property per godot.core type that the library's serializer registry
 * special-cases. The `roundTrip*` helpers run a setter → getter → reset → setter
 * cycle through the SyncConfig DSL (which routes through the library's
 * `serialize`/`deserialize` paths), so each call exercises that type's bespoke
 * `KSerializer`. Used by [SerializerRoundTripScenario].
 *
 * Has to live inside a `Node` subclass because the DSL's `property(...)` is an
 * extension on `OWNER : Node` — without the receiver in scope, the property
 * references won't resolve.
 */
@RegisterClass
class SerializerProbe : Node() {
    var aabb: AABB = AABB()
    var basis: Basis = Basis()
    var color: Color = Color()
    var nodePath: NodePath = "".asNodePath()
    var plane: Plane = Plane()
    var projection: Projection = Projection()
    var quaternion: Quaternion = Quaternion()
    var rect2: Rect2 = Rect2()
    var rect2i: Rect2i = Rect2i()
    var stringName: StringName = "".asStringName()
    var transform2D: Transform2D = Transform2D()
    var transform3D: Transform3D = Transform3D()
    var vector2: Vector2 = Vector2()
    var vector2i: Vector2i = Vector2i()
    var vector3: Vector3 = Vector3()
    var vector3i: Vector3i = Vector3i()
    var vector4: Vector4 = Vector4()
    var vector4i: Vector4i = Vector4i()

    /**
     * Run a round-trip through the DSL: set the property to [given], serialize via
     * the library's getter, reset, deserialize via the library's setter, return the
     * recovered value. Caller compares it to [given].
     */
    private inline fun <reified T : Any> roundTripVia(
        property: kotlin.reflect.KMutableProperty0<T>,
        given: T,
        reset: T,
    ): T {
        property.set(given)
        val config = syncConfig { property(property) }.values.single()
        val serialized = config.getter()
        property.set(reset)
        config.setter(serialized)
        return property.get()
    }

    fun runAll(): Map<String, Pair<Any, Any>> = buildMap {
        // For each type, build a non-default "given" value, reset to default, recover,
        // store (given, recovered) so callers can diff them. Equality assertion is the
        // caller's job — keeps this fixture independent of test framework.
        Vector2(1.5, -2.5).let { given ->
            put("Vector2", given to roundTripVia(::vector2, given, Vector2()))
        }
        Vector2i(7, -3).let { given ->
            put("Vector2i", given to roundTripVia(::vector2i, given, Vector2i()))
        }
        Vector3(1.5, -2.5, 100.0).let { given ->
            put("Vector3", given to roundTripVia(::vector3, given, Vector3()))
        }
        Vector3i(1, 2, 3).let { given ->
            put("Vector3i", given to roundTripVia(::vector3i, given, Vector3i()))
        }
        Vector4(1.0, 2.0, 3.0, 4.0).let { given ->
            put("Vector4", given to roundTripVia(::vector4, given, Vector4()))
        }
        Vector4i(5, 6, 7, 8).let { given ->
            put("Vector4i", given to roundTripVia(::vector4i, given, Vector4i()))
        }
        Color(0.25, 0.5, 0.75, 1.0).let { given ->
            put("Color", given to roundTripVia(::color, given, Color()))
        }
        Quaternion(0.1, 0.2, 0.3, 0.927).let { given ->
            put("Quaternion", given to roundTripVia(::quaternion, given, Quaternion()))
        }
        Basis(Vector3(1.0, 0.0, 0.0), Vector3(0.0, 1.0, 0.0), Vector3(0.0, 0.0, 1.0)).let { given ->
            put("Basis", given to roundTripVia(::basis, given, Basis()))
        }
        Transform2D(0.0, Vector2(10.0, 20.0)).let { given ->
            put("Transform2D", given to roundTripVia(::transform2D, given, Transform2D()))
        }
        Transform3D(
            Basis(Vector3(1.0, 0.0, 0.0), Vector3(0.0, 1.0, 0.0), Vector3(0.0, 0.0, 1.0)),
            Vector3(5.0, 10.0, 15.0),
        ).let { given ->
            put("Transform3D", given to roundTripVia(::transform3D, given, Transform3D()))
        }
        AABB(Vector3(1.0, 2.0, 3.0), Vector3(4.0, 5.0, 6.0)).let { given ->
            put("AABB", given to roundTripVia(::aabb, given, AABB()))
        }
        Plane(Vector3(0.0, 1.0, 0.0), 3.5).let { given ->
            put("Plane", given to roundTripVia(::plane, given, Plane()))
        }
        Rect2(Vector2(1.0, 2.0), Vector2(3.0, 4.0)).let { given ->
            put("Rect2", given to roundTripVia(::rect2, given, Rect2()))
        }
        Rect2i(Vector2i(1, 2), Vector2i(3, 4)).let { given ->
            put("Rect2i", given to roundTripVia(::rect2i, given, Rect2i()))
        }
        Projection().let { given ->
            put("Projection", given to roundTripVia(::projection, given, Projection()))
        }
        "level/player/head".asNodePath().let { given ->
            put("NodePath", given to roundTripVia(::nodePath, given, "".asNodePath()))
        }
        "some_string_name".asStringName().let { given ->
            put("StringName", given to roundTripVia(::stringName, given, "".asStringName()))
        }
    }
}
