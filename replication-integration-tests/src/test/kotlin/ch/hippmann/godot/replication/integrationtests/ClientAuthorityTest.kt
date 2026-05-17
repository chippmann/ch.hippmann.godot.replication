package ch.hippmann.godot.replication.integrationtests

import ch.hippmann.godot.replication.integrationtests.scenarios.ClientAuthorityScenario
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.jsonPrimitive

class ClientAuthorityTest : FunSpec({
    test("client-authority Synchronized: mutation on the client flows back to the server") {
        val orchestrator = ProcessOrchestrator(
            godotBinary = MultiPeerTestSupport.godotBinary,
            godotProjectDir = MultiPeerTestSupport.godotProjectDir,
        )
        try {
            orchestrator.launch(
                scenarioClass = ClientAuthorityScenario::class,
                role = Role.SERVER,
                peerName = "server",
                expectedClientCount = 1,
            )
            Thread.sleep(300)
            orchestrator.launch(
                scenarioClass = ClientAuthorityScenario::class,
                role = Role.CLIENT,
                peerName = "client",
            )

            val results = orchestrator.awaitAll(timeoutSeconds = 60)
            results.assertAllPassed()

            val server = results.serverResult()
            val client = results.clientsOnly().single()

            val serverSawClientAuth = server.data["serverSawAuthorityAsClient"]?.jsonPrimitive?.content?.toBoolean() == true
            val clientIsAuth = client.data["clientIsAuthority"]?.jsonPrimitive?.content?.toBoolean() == true
            val serverObservedX = server.data["serverObservedX"]?.jsonPrimitive?.content?.toInt() ?: -1
            val clientFinalX = client.data["clientFinalX"]?.jsonPrimitive?.content?.toInt() ?: -1

            if (!serverSawClientAuth || !clientIsAuth) fail(
                "authority routing broke: serverSawClientAuth=$serverSawClientAuth, clientIsAuthority=$clientIsAuth\n" +
                    renderMultiPeerFailure(results),
            )
            if (clientFinalX != 77 || serverObservedX != 77) fail(
                "expected client=77 server=77, got client=$clientFinalX, server=$serverObservedX\n" +
                    renderMultiPeerFailure(results),
            )
        } finally {
            orchestrator.cleanup()
        }
    }
})
