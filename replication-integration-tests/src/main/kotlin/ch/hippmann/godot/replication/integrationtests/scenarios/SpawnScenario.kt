package ch.hippmann.godot.replication.it.scenarios

import ch.hippmann.godot.replication.it.TestContext
import ch.hippmann.godot.replication.it.TestScenario
import ch.hippmann.godot.replication.it.fixtures.ITReplicator
import godot.api.PackedScene
import godot.api.ResourceLoader
import godot.core.variantArrayOf

class SpawnScenario : TestScenario {
    private val managedScenePath = "res://fixtures/cube.tscn"

    private fun addReplicator(ctx: TestContext): ITReplicator {
        val packed = ResourceLoader.load(managedScenePath) as PackedScene
        val replicator = ITReplicator().apply { setName("Replicator") }
        ctx.runner.addChild(replicator)
        replicator.managedScenes = variantArrayOf<PackedScene>(packed)
        return replicator
    }

    override suspend fun runAsServer(ctx: TestContext) {
        ctx.startServer()
        val replicator = addReplicator(ctx)
        ctx.awaitClientsConnected(ctx.args.clientCount)

        val packed = ResourceLoader.load(managedScenePath) as PackedScene
        val instance = packed.instantiate()!!.apply { setName("ManagedInstance") }
        replicator.addChild(instance)

        ctx.put("childCount", replicator.getChildCount().toInt())
        ctx.put("ok", true)

        // Hold the server up long enough for clients to observe & report.
        ctx.pollUntil(timeoutMs = 10_000) { ctx.multiplayer.getPeers().size == 0 }
    }

    override suspend fun runAsClient(ctx: TestContext) {
        // Connect multiplayer BEFORE adding the Replicator. The library's autoload-based
        // ready handshake RPCs through multiplayer the moment the Replicator becomes _ready;
        // if the peer isn't connected yet, that RPC is silently dropped.
        ctx.connectToServer()
        ctx.awaitServerConnected()
        val replicator = addReplicator(ctx)

        ctx.pollUntil(timeoutMs = 10_000) { replicator.getChildCount() >= 1 }
        ctx.put("observedChildren", replicator.getChildCount().toInt())
    }
}
