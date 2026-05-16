package ch.hippmann.godot.replication.integrationtests

import ch.hippmann.godot.replication.integrationtests.scenarios.DespawnScenario
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.jsonPrimitive

class DespawnTest : FunSpec({
    test("server-removed managed child disappears on all clients") {
        val orchestrator = ProcessOrchestrator(
            godotBinary = MultiPeerTestSupport.godotBinary,
            godotProjectDir = MultiPeerTestSupport.godotProjectDir,
        )
        try {
            val expectedClientCount = 2
            orchestrator.launch(
                scenarioClass = DespawnScenario::class,
                role = Role.SERVER,
                peerName = "server",
                expectedClientCount = expectedClientCount,
            )
            repeat(expectedClientCount) { index ->
                Thread.sleep(300)
                orchestrator.launch(
                    scenarioClass = DespawnScenario::class,
                    role = Role.CLIENT,
                    peerName = "client-${index + 1}",
                )
            }

            val results = orchestrator.awaitAll(timeoutSeconds = 60)
            results.assertAllPassed()

            for (client in results.clientsOnly()) {
                val afterSpawn = client.data["childrenAfterSpawn"]?.jsonPrimitive?.content?.toInt() ?: -1
                val afterDespawn = client.data["childrenAfterDespawn"]?.jsonPrimitive?.content?.toInt() ?: -1
                if (afterSpawn != 1 || afterDespawn != 0) fail(
                    "client ${client.peerName} saw spawn=$afterSpawn / despawn=$afterDespawn (expected 1 / 0)\n" +
                        renderMultiPeerFailure(results),
                )
            }
        } finally {
            orchestrator.cleanup()
        }
    }
})
