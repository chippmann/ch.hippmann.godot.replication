package ch.hippmann.godot.replication.integrationtests.scenarios

import ch.hippmann.godot.replication.integrationtests.TestContext
import ch.hippmann.godot.replication.integrationtests.TestScenario
import ch.hippmann.godot.replication.integrationtests.TestScene
import ch.hippmann.godot.replication.integrationtests.fixtures.IntegrationTestSynchronized
import godot.core.Vector3
import kotlinx.coroutines.delay

/**
 * Pins bug #3: a non-authority peer must not be able to push property updates to
 * another peer by directly invoking the `replicateForSynchronizedReliable` RPC.
 *
 * Layout: server is authority, two clients connect. Server sets `customPosition.x`
 * to a legitimate value (42). Each client waits for the legitimate value to
 * propagate, then **forges** an update with value 999 and sends it via `rpcId` to
 * every other client. After a settling delay, each client reports its observed
 * customPosition.x. Without the authority check the forgery would land and the
 * value would jump to 999. With the fix, the receiver's `replicate(...)` rejects
 * the call because `multiplayer.getRemoteSenderId()` is not the node's authority.
 */
@TestScene("res://scenes/synchronized_basic.tscn")
class ForgedSyncScenario : TestScenario {
    private val authoritativeX = 42.0
    private val forgedX = 999.0
    private val propertyFullyQualifiedName =
        "ch.hippmann.godot.replication.integrationtests.fixtures.IntegrationTestSynchronized::customPosition"

    private fun TestContext.synchronizedNode(): IntegrationTestSynchronized =
        runner.getNodeOrNull("Synced") as? IntegrationTestSynchronized
            ?: error("synchronized_basic.tscn must contain a 'Synced' node of type IntegrationTestSynchronized")

    override suspend fun runAsServer(context: TestContext) {
        context.startServer()
        context.awaitClientsConnected(context.args.expectedClientCount)

        val synced = context.synchronizedNode()
        // Wait for the WithRemoteListeners handshake so server has all subscribers.
        context.pollUntil(timeoutMs = 5_000) {
            synced.listeningPeers.size == context.args.expectedClientCount
        }

        synced.customPosition = Vector3(authoritativeX, 0.0, 0.0)
        // Let the legitimate value propagate, then hold while clients run their attack.
        delay(2_000)
        context.put("serverX", synced.customPosition.x.toInt())
        context.awaitAllClientsDisconnected()
    }

    override suspend fun runAsClient(context: TestContext) {
        context.connectToServer()
        context.awaitServerConnected()
        val synced = context.synchronizedNode()

        // Wait for the legitimate authority-driven sync to land.
        context.pollUntil(timeoutMs = 10_000) {
            synced.customPosition.x >= authoritativeX - 0.5
        }
        context.put("legitimateX", synced.customPosition.x.toInt())

        // Forge: send a fake reliable sync RPC to every OTHER client (not the server,
        // not ourselves). Without the bug #3 fix, the receiver would apply forgedX
        // to its local customPosition.
        val ourPeerId = context.multiplayer.getUniqueId().toLong()
        val otherClientPeerIds = context.multiplayer.getPeers().toList()
            .map { it.toLong() }
            .filter { it != ourPeerId && it != 1L /* server */ }
        val forgedData = """{"x":$forgedX,"y":0.0,"z":0.0}"""
        otherClientPeerIds.forEach { peerId ->
            synced.rpcId(peerId, synced::replicateForSynchronizedReliable, propertyFullyQualifiedName, forgedData)
        }
        context.put("forgedRecipients", otherClientPeerIds.size)

        // Give the attack time to be processed (or rejected) on the recipients.
        delay(2_000)

        // Our local value should still be the authoritative one — we didn't apply it
        // ourselves, and no other client successfully pushed forgedX to us (assuming
        // every client behaves the same way).
        context.put("finalX", synced.customPosition.x.toInt())
    }
}
