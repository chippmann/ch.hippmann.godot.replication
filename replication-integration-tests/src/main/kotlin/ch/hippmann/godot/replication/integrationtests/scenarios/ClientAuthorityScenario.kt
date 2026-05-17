package ch.hippmann.godot.replication.integrationtests.scenarios

import ch.hippmann.godot.replication.integrationtests.TestContext
import ch.hippmann.godot.replication.integrationtests.TestScenario
import ch.hippmann.godot.replication.integrationtests.TestScene
import ch.hippmann.godot.replication.integrationtests.fixtures.IntegrationTestSynchronized
import godot.core.Vector3
import kotlinx.coroutines.delay

/**
 * Inverts the usual authority direction. The default is server (peer id 1) =
 * authority; here we let the CLIENT (peer id 2) be authority of a managed
 * Synchronized child. Mutations on the client flow to the server.
 *
 * Server flow:
 *   - start, await client, look up the client's peer id from getPeers()
 *   - instantiate the managed scene, setMultiplayerAuthority(clientId) BEFORE addChild
 *   - addChild — spawn RPC carries authority=clientId; client instantiates with same
 *   - poll until the locally-spawned node's customPosition catches the client's value
 *
 * Client flow:
 *   - connect, observe spawn, verify authority == own unique id
 *   - wait for handshake (server in our listeningPeers), set customPosition
 *   - report own unique id + final value
 *
 * Pins the part of the handshake / authority-routing that works WITHOUT assuming
 * authority == SERVER_PEER_ID.
 */
@TestScene("res://scenes/replication_synced_managed.tscn")
class ClientAuthorityScenario : TestScenario {
    private val authoritativeValue = 77.0

    private fun TestContext.synced(): IntegrationTestSynchronized =
        replicator.getNodeOrNull("ClientAuthSynced") as? IntegrationTestSynchronized
            ?: error("expected ClientAuthSynced under Replicator after spawn")

    override suspend fun runAsServer(context: TestContext) {
        context.startServer()
        context.awaitClientsConnected(context.args.expectedClientCount)

        val replicator = context.replicator
        // Wait for the OUTER Replicator's WithRemoteListeners handshake before spawning —
        // otherwise the spawn RPC fires before the client is subscribed and is lost.
        context.pollUntil(timeoutMs = 5_000) {
            replicator.listeningPeers.size == context.args.expectedClientCount
        }

        val clientPeerId = context.multiplayer.getPeers().toList().first().toInt()
        context.put("clientPeerId", clientPeerId)

        val packed = replicator.managedScenes.first()
        val instance = packed.instantiate() as IntegrationTestSynchronized
        instance.setName("ClientAuthSynced")
        // Critical: set authority BEFORE addChild. Replicator's spawn RPC reads the
        // authority off the child at addChild time and propagates it in SpawnNodeData;
        // peers apply the same authority to their local instantiation.
        instance.setMultiplayerAuthority(clientPeerId)
        replicator.addChild(instance)
        context.put("serverSawAuthorityAsClient", instance.getMultiplayerAuthority() == clientPeerId)

        // Wait for the client to mutate customPosition and the sync to flow BACK to us.
        // We're the peer for this node; the client is authority; client's tick fires
        // and rpc's the new value to us.
        context.pollUntil(timeoutMs = 15_000) { instance.customPosition.x >= authoritativeValue - 0.5 }
        context.put("serverObservedX", instance.customPosition.x.toInt())
    }

    override suspend fun runAsClient(context: TestContext) {
        context.connectToServer()
        context.awaitServerConnected()

        val replicator = context.replicator
        context.pollUntil(timeoutMs = 10_000) { replicator.getChildCount() >= 1 }
        val synced = context.synced()

        val ownPeerId = context.multiplayer.getUniqueId()
        context.put("clientUniqueId", ownPeerId)
        context.put("clientIsAuthority", synced.getMultiplayerAuthority() == ownPeerId)

        // Wait for the inner Synchronized's WithRemoteListeners handshake — server
        // needs to be in OUR listeningPeers before we mutate so our tick sends reach it.
        context.pollUntil(timeoutMs = 5_000) { synced.listeningPeers.isNotEmpty() }

        synced.customPosition = Vector3(authoritativeValue, 0.0, 0.0)
        delay(2_000) // let our ticker fire the send
        context.put("clientFinalX", synced.customPosition.x.toInt())
    }
}
