package ch.hippmann.godot.replication.integrationtests

import ch.hippmann.godot.replication.integrationtests.scenarios.CustomSerializerScenario
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.jsonPrimitive

class CustomSerializerTest : FunSpec({
    test("DSL serializer/deserializer override actually runs on both encode and decode") {
        val orchestrator = ProcessOrchestrator(
            godotBinary = MultiPeerTestSupport.godotBinary,
            godotProjectDir = MultiPeerTestSupport.godotProjectDir,
        )
        try {
            orchestrator.launch(
                scenarioClass = CustomSerializerScenario::class,
                role = Role.SERVER,
                peerName = "probe",
            )

            val results = orchestrator.awaitAll(timeoutSeconds = 30)
            results.assertAllPassed()

            val data = results.serverResult().data
            val encoderUsed = data["encoderUsed"]?.jsonPrimitive?.content?.toBoolean() == true
            val decoderUsed = data["decoderUsed"]?.jsonPrimitive?.content?.toBoolean() == true
            val serialized = data["serialized"]?.jsonPrimitive?.content
            val recovered = data["recovered"]?.jsonPrimitive?.content
            if (!encoderUsed || !decoderUsed) fail(
                "custom (de)serializer not used: encoderUsed=$encoderUsed (serialized=$serialized), " +
                    "decoderUsed=$decoderUsed (recovered=$recovered)\n${renderMultiPeerFailure(results)}",
            )
        } finally {
            orchestrator.cleanup()
        }
    }
})
