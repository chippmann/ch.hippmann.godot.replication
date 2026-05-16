package ch.hippmann.godot.replication.integrationtests.scenarios

import ch.hippmann.godot.replication.SyncConfig
import ch.hippmann.godot.replication.integrationtests.TestContext
import ch.hippmann.godot.replication.integrationtests.TestRunner
import ch.hippmann.godot.replication.integrationtests.TestScenario

/**
 * Pins bug #1: `PropertyConfig.syncChannel` used to not exist, so every config built
 * via the DSL silently defaulted to `CHANNEL_0` no matter what — making the 10
 * `replicateForSynchronizedUnreliableOrderedChannel*` RPC overloads dead code in the
 * normal DSL-driven path.
 *
 * Single-peer scenario. Constructs four DSL configs with distinct transfer modes /
 * channels and reports each resulting `(syncMethod, syncChannel)` pair.
 */
class SyncConfigDslScenario : TestScenario {
    override val scenePath: String = "res://scenes/empty.tscn"

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
    }

    override suspend fun runAsClient(context: TestContext) {
        error("SyncConfigDslScenario is single-peer; no client role")
    }
}
