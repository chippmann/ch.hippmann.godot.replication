package ch.hippmann.godot.replication.integrationtests

import ch.hippmann.godot.replication.integrationtests.scenarios.ListenerLeakScenario
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.jsonPrimitive

class ListenerLeakTest : FunSpec({
    test("RemoteListenerReadyRedirector.listeners drops back to baseline after Replicators leave the tree") {
        val orchestrator = ProcessOrchestrator(
            godotBinary = MultiPeerTestSupport.godotBinary,
            godotProjectDir = MultiPeerTestSupport.godotProjectDir,
        )
        try {
            orchestrator.launch(
                scenarioClass = ListenerLeakScenario::class,
                role = Role.SERVER,
                peerName = "solo",
                expectedClientCount = 0,
            )

            val results = orchestrator.awaitAll(timeoutSeconds = 30)
            results.assertAllPassed()

            val solo = results.serverResult()
            val initial = solo.data["initialListenerCount"]?.jsonPrimitive?.content?.toInt() ?: -1
            val peak = solo.data["peakListenerCount"]?.jsonPrimitive?.content?.toInt() ?: -1
            val final = solo.data["finalListenerCount"]?.jsonPrimitive?.content?.toInt() ?: -1

            // Peak should have grown by the number of added Replicators.
            if (peak != initial + 5) fail(
                "expected peak listenerCount to be initial+5 ($initial+5), got $peak\n" +
                    renderMultiPeerFailure(results),
            )
            // Final should be back to baseline.
            if (final != initial) fail(
                "expected listenerCount to return to baseline ($initial) after queueFree, got $final\n" +
                    renderMultiPeerFailure(results),
            )
        } finally {
            orchestrator.cleanup()
        }
    }
})
