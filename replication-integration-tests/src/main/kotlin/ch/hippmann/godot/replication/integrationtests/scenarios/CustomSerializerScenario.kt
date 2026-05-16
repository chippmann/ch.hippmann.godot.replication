package ch.hippmann.godot.replication.integrationtests.scenarios

import ch.hippmann.godot.replication.integrationtests.TestContext
import ch.hippmann.godot.replication.integrationtests.TestRunner
import ch.hippmann.godot.replication.integrationtests.TestScenario
import ch.hippmann.godot.replication.integrationtests.TestScene

/**
 * Pins the DSL's per-property `serializer` / `deserializer` override surface.
 *
 * The runner's `buildCustomSerializerSyncConfig` registers a String property
 * with serializer `{ "__CUSTOM__:$this" }` and matching strip-the-prefix
 * deserializer. Scenario:
 *   - sets probeLabel = "hello"
 *   - calls the config's getter → result MUST start with "__CUSTOM__:" (proves
 *     the custom encoder ran, not the default JSON which would quote-wrap)
 *   - resets probeLabel and feeds the encoded value back through the setter →
 *     the property MUST recover to "hello" (proves the custom decoder ran)
 */
@TestScene("res://scenes/empty.tscn")
class CustomSerializerScenario : TestScenario {
    override suspend fun runAsServer(context: TestContext) {
        val runner = context.runner as TestRunner

        runner.probeLabel = "hello"
        val config = runner.buildCustomSerializerSyncConfig().values.single()
        val serialized = config.getter()
        context.put("serialized", serialized)
        context.put("encoderUsed", serialized.startsWith("__CUSTOM__:"))

        runner.probeLabel = "default_pre_set"
        config.setter(serialized)
        context.put("recovered", runner.probeLabel)
        context.put("decoderUsed", runner.probeLabel == "hello")
    }

    override suspend fun runAsClient(context: TestContext) {
        error("CustomSerializerScenario is single-peer; no client role")
    }
}
