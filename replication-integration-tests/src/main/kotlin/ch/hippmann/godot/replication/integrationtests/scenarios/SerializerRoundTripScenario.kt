package ch.hippmann.godot.replication.integrationtests.scenarios

import ch.hippmann.godot.replication.integrationtests.TestContext
import ch.hippmann.godot.replication.integrationtests.TestScenario
import ch.hippmann.godot.replication.integrationtests.TestScene
import ch.hippmann.godot.replication.integrationtests.fixtures.SerializerProbe

/**
 * Single-peer scenario that pins the library's 18 special-cased serializers
 * (every godot.core.* type the `serialize()`/`deserialize()` `when` branches
 * cover). Instantiates a [SerializerProbe], runs each type through a set→
 * serialize→reset→deserialize cycle via the SyncConfig DSL, and reports per
 * type whether the recovered value equals the original.
 *
 * A failure here means a wire-format bug in that one type's `KSerializer`
 * (e.g., descriptor element rename mid-flight, missing field, type mismatch).
 */
@TestScene("res://scenes/empty.tscn")
class SerializerRoundTripScenario : TestScenario {
    override suspend fun runAsServer(context: TestContext) {
        val probe = SerializerProbe()
        context.runner.addChild(probe)

        val results = probe.runAll()
        for ((typeName, pair) in results) {
            val (given, recovered) = pair
            // StringName lacks an `equals()` override (inherits identity from
            // NativeCoreType), so direct `==` always fails even when the contents
            // match. Compare via toString() for the string-wrapper types.
            val ok = when (typeName) {
                "StringName", "NodePath" -> given.toString() == recovered.toString()
                else -> given == recovered
            }
            context.put("${typeName}_ok", ok)
            if (!ok) {
                context.put("${typeName}_given", given.toString())
                context.put("${typeName}_recovered", recovered.toString())
            }
        }
        context.put("typeCount", results.size)
    }

    override suspend fun runAsClient(context: TestContext) {
        error("SerializerRoundTripScenario is single-peer; no client role")
    }
}
