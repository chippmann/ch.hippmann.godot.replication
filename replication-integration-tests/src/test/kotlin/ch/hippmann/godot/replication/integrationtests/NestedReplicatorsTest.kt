package ch.hippmann.godot.replication.integrationtests

import ch.hippmann.godot.replication.integrationtests.scenarios.NestedReplicatorsScenario
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.jsonPrimitive

class NestedReplicatorsTest : FunSpec({
    test("Replicator under Replicator — both spawn chains propagate to the client") {
        val orchestrator = ProcessOrchestrator(
            godotBinary = MultiPeerTestSupport.godotBinary,
            godotProjectDir = MultiPeerTestSupport.godotProjectDir,
        )
        try {
            orchestrator.launch(
                scenarioClass = NestedReplicatorsScenario::class,
                role = Role.SERVER,
                peerName = "server",
                expectedClientCount = 1,
            )
            Thread.sleep(300)
            orchestrator.launch(
                scenarioClass = NestedReplicatorsScenario::class,
                role = Role.CLIENT,
                peerName = "client",
            )

            val results = orchestrator.awaitAll(timeoutSeconds = 60)
            results.assertAllPassed()

            val client = results.clientsOnly().single()
            val outer = client.data["observedOuterChildCount"]?.jsonPrimitive?.content?.toInt() ?: -1
            val inner = client.data["observedInnerChildCount"]?.jsonPrimitive?.content?.toInt() ?: -1
            val hasCube = client.data["observedInnerHasCube"]?.jsonPrimitive?.content?.toBoolean() == true
            if (outer != 1 || inner != 1 || !hasCube) fail(
                "expected outer=1, inner=1, hasCube=true; got outer=$outer, inner=$inner, hasCube=$hasCube\n" +
                    renderMultiPeerFailure(results),
            )
        } finally {
            orchestrator.cleanup()
        }
    }
})
