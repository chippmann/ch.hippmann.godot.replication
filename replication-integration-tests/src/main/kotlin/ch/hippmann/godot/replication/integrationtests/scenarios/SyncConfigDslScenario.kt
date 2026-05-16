package ch.hippmann.godot.replication.integrationtests.scenarios

import ch.hippmann.godot.replication.SyncConfig
import ch.hippmann.godot.replication.integrationtests.TestContext
import ch.hippmann.godot.replication.integrationtests.TestRunner
import ch.hippmann.godot.replication.integrationtests.TestScenario
import ch.hippmann.godot.replication.integrationtests.TestScene

/**
 * Pins bug #1: `PropertyConfig.syncChannel` used to not exist, so every config built
 * via the DSL silently defaulted to `CHANNEL_0` no matter what — making the 10
 * `replicateForSynchronizedUnreliableOrderedChannel*` RPC overloads dead code in the
 * normal DSL-driven path.
 *
 * Single-peer scenario. Constructs four DSL configs with distinct transfer modes /
 * channels and reports each resulting `(syncMethod, syncChannel)` pair.
 */
@TestScene("res://scenes/empty.tscn")
class SyncConfigDslScenario : TestScenario {

    override suspend fun runAsServer(context: TestContext) {
        val runner = context.runner as TestRunner

        val cases = listOf(
            "reliable_default" to (SyncConfig.SyncMethod.RELIABLE to SyncConfig.SyncChannel.CHANNEL_0),
            "unreliable_default" to (SyncConfig.SyncMethod.UNRELIABLE to SyncConfig.SyncChannel.CHANNEL_0),
            "ordered_channel_3" to (SyncConfig.SyncMethod.UNRELIABLE_ORDERED to SyncConfig.SyncChannel.CHANNEL_3),
            "ordered_channel_9" to (SyncConfig.SyncMethod.UNRELIABLE_ORDERED to SyncConfig.SyncChannel.CHANNEL_9),
        )

        for ((label, modeAndChannel) in cases) {
            val (method, channel) = modeAndChannel
            val configs = runner.buildProbeSyncConfig(method = method, channel = channel)
            val produced = configs.values.single()
            context.put("${label}_method", produced.syncMethod.name)
            context.put("${label}_channel", produced.syncChannel.name)
        }

        // Toggle cases: verify syncOnSpawn / syncOnTick both flow through the DSL too.
        val toggleCases = listOf(
            "spawn_on_tick_on" to (true to true),
            "spawn_on_tick_off" to (true to false),
            "spawn_off_tick_on" to (false to true),
            "spawn_off_tick_off" to (false to false),
        )
        for ((label, spawnAndTick) in toggleCases) {
            val (spawn, tick) = spawnAndTick
            val configs = runner.buildProbeSyncConfigFull(
                method = SyncConfig.SyncMethod.RELIABLE,
                channel = SyncConfig.SyncChannel.CHANNEL_0,
                spawn = spawn,
                tick = tick,
            )
            val produced = configs.values.single()
            context.put("${label}_spawn", produced.syncOnSpawn)
            context.put("${label}_tick", produced.syncOnTick)
        }
    }

    override suspend fun runAsClient(context: TestContext) {
        error("SyncConfigDslScenario is single-peer; no client role")
    }
}
