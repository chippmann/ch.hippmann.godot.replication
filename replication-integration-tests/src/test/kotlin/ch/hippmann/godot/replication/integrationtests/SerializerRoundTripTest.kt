package ch.hippmann.godot.replication.integrationtests

import ch.hippmann.godot.replication.integrationtests.scenarios.SerializerRoundTripScenario
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class SerializerRoundTripTest : FunSpec({
    test("every special-cased godot.core type round-trips through the SyncConfig serializer registry") {
        val orchestrator = ProcessOrchestrator(
            godotBinary = MultiPeerTestSupport.godotBinary,
            godotProjectDir = MultiPeerTestSupport.godotProjectDir,
        )
        try {
            orchestrator.launch(
                scenarioClass = SerializerRoundTripScenario::class,
                role = Role.SERVER,
                peerName = "probe",
            )

            val results = orchestrator.awaitAll(timeoutSeconds = 30)
            results.assertAllPassed()

            val data: JsonObject = results.serverResult().data
            val typeCount = data["typeCount"]?.jsonPrimitive?.content?.toInt() ?: -1
            // 18 special-cased types declared in the SerializerProbe.
            if (typeCount != 18) fail("expected 18 types, scenario reported $typeCount")

            val failures = data.entries
                .filter { it.key.endsWith("_ok") }
                .filter { it.value.jsonPrimitive.content.toBoolean().not() }
                .map { it.key.removeSuffix("_ok") }

            if (failures.isNotEmpty()) {
                val diagnostics = failures.joinToString("\n") { typeName ->
                    val given = data["${typeName}_given"]?.jsonPrimitive?.content
                    val recovered = data["${typeName}_recovered"]?.jsonPrimitive?.content
                    "  $typeName: given=$given recovered=$recovered"
                }
                fail("Serializer round-trip failed for:\n$diagnostics\n${renderMultiPeerFailure(results)}")
            }
        } finally {
            orchestrator.cleanup()
        }
    }
})
