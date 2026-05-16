package ch.hippmann.godot.replication.it.scenarios

import ch.hippmann.godot.replication.impl.SimpleReplicator
import ch.hippmann.godot.replication.it.TestContext
import ch.hippmann.godot.replication.it.TestScenario
import godot.api.PackedScene
import godot.api.ResourceLoader
import godot.core.VariantArray
import godot.core.variantArrayOf

class SpawnScenario : TestScenario {
    private val managedScenePath = "res://fixtures/cube.tscn"

    override suspend fun runAsServer(ctx: TestContext) {
        ctx.startServer()
        ctx.awaitClientsConnected(ctx.args.clientCount)

        val replicator = SimpleReplicator().apply { setName("Replicator") }
        ctx.runner.addChild(replicator)

        val packed = ResourceLoader.load(managedScenePath) as PackedScene
        replicator.managedScenes = variantArrayOf<PackedScene>(packed)

        val instance = packed.instantiate()!!.apply { setName("ManagedInstance") }
        replicator.addChild(instance)

        ctx.put("childCount", replicator.getChildCount().toInt())
        ctx.put("ok", true)

        // Hold the server up long enough for clients to observe & report.
        ctx.pollUntil(timeoutMs = 10_000) { ctx.multiplayer.getPeers().size == 0 }
    }

    override suspend fun runAsClient(ctx: TestContext) {
        ctx.connectToServer()
        ctx.awaitServerConnected()

        ctx.pollUntil(timeoutMs = 10_000) {
            val rep = ctx.runner.getNodeOrNull("Replicator")
            rep != null && rep.getChildCount() >= 1
        }

        val rep = ctx.runner.getNodeOrNull("Replicator")
        ctx.put("observedChildren", rep?.getChildCount()?.toInt() ?: -1)
    }
}
