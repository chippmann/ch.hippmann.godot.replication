package ch.hippmann.godot.replication.integrationtests

import ch.hippmann.godot.replication.integrationtests.scenarios.ManagedScenesReassignmentScenario
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.jsonPrimitive

class ManagedScenesReassignmentTest : FunSpec({
    test("reassigning managedScenes drops the previous entries — old scenes no longer replicate") {
        val orchestrator = ProcessOrchestrator(
            godotBinary = MultiPeerTestSupport.godotBinary,
            godotProjectDir = MultiPeerTestSupport.godotProjectDir,
        )
        try {
            val expectedClientCount = 1
            orchestrator.launch(
                scenarioClass = ManagedScenesReassignmentScenario::class,
                role = Role.SERVER,
                peerName = "server",
                expectedClientCount = expectedClientCount,
            )
            Thread.sleep(300)
            orchestrator.launch(
                scenarioClass = ManagedScenesReassignmentScenario::class,
                role = Role.CLIENT,
                peerName = "client",
            )

            val results = orchestrator.awaitAll(timeoutSeconds = 60)
            results.assertAllPassed()

            val client = results.clientsOnly().single()
            val observedTotal = client.data["observedChildren"]?.jsonPrimitive?.content?.toInt() ?: -1
            val observedStaleCube = client.data["observedStaleCube"]?.jsonPrimitive?.content?.toInt() ?: -1
            val observedFreshAlt = client.data["observedFreshAlt"]?.jsonPrimitive?.content?.toInt() ?: -1

            if (observedStaleCube != 0 || observedFreshAlt != 1 || observedTotal != 1) fail(
                "client expected only the fresh cube_alt; got total=$observedTotal " +
                    "(staleCube=$observedStaleCube, freshAlt=$observedFreshAlt)\n" +
                    renderMultiPeerFailure(results),
            )
        } finally {
            orchestrator.cleanup()
        }
    }
})
