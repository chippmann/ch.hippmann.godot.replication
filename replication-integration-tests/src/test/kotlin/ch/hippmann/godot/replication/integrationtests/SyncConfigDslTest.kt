package ch.hippmann.godot.replication.integrationtests

import ch.hippmann.godot.replication.integrationtests.scenarios.SyncConfigDslScenario
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.jsonPrimitive

class SyncConfigDslTest : FunSpec({
    test("SyncConfigDsl propagates syncMethod and syncChannel into the produced SyncConfig") {
        val orchestrator = ProcessOrchestrator(
            godotBinary = MultiPeerTestSupport.godotBinary,
            godotProjectDir = MultiPeerTestSupport.godotProjectDir,
        )
        try {
            orchestrator.launch(
                scenarioClass = SyncConfigDslScenario::class,
                role = Role.SERVER,
                peerName = "solo",
                expectedClientCount = 0,
            )

            val results = orchestrator.awaitAll(timeoutSeconds = 30)
            results.assertAllPassed()

            val solo = results.serverResult()
            val expectations = listOf(
                "reliable_default" to ("RELIABLE" to "CHANNEL_0"),
                "unreliable_default" to ("UNRELIABLE" to "CHANNEL_0"),
                "ordered_channel_3" to ("UNRELIABLE_ORDERED" to "CHANNEL_3"),
                "ordered_channel_9" to ("UNRELIABLE_ORDERED" to "CHANNEL_9"),
            )
            for ((label, expected) in expectations) {
                val (expectedMethod, expectedChannel) = expected
                val method = solo.data["${label}_method"]?.jsonPrimitive?.content
                val channel = solo.data["${label}_channel"]?.jsonPrimitive?.content
                if (method != expectedMethod || channel != expectedChannel) fail(
                    "case $label: expected ($expectedMethod, $expectedChannel) got ($method, $channel)\n" +
                        renderMultiPeerFailure(results),
                )
            }

            val toggleExpectations = listOf(
                "spawn_on_tick_on" to (true to true),
                "spawn_on_tick_off" to (true to false),
                "spawn_off_tick_on" to (false to true),
                "spawn_off_tick_off" to (false to false),
            )
            for ((label, expected) in toggleExpectations) {
                val (expectedSpawn, expectedTick) = expected
                val spawn = solo.data["${label}_spawn"]?.jsonPrimitive?.content?.toBoolean()
                val tick = solo.data["${label}_tick"]?.jsonPrimitive?.content?.toBoolean()
                if (spawn != expectedSpawn || tick != expectedTick) fail(
                    "case $label: expected (spawn=$expectedSpawn, tick=$expectedTick) got (spawn=$spawn, tick=$tick)\n" +
                        renderMultiPeerFailure(results),
                )
            }
        } finally {
            orchestrator.cleanup()
        }
    }
})
