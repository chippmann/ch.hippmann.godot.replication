package ch.hippmann.godot.replication.sample.scenario

import ch.hippmann.godot.replication.core.level.LevelPolicy
import ch.hippmann.godot.replication.LobbyConfiguration
import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.PlayerProfile
import ch.hippmann.godot.replication.sample.world.Arena
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** The master loads the arena; the slow member (see `--level-preparation-milliseconds`) decides when everyone starts. */
class LevelPolicyScenario(private val policy: LevelPolicy) : Scenario {
    override suspend fun run(runner: ScenarioRunner, context: ScenarioContext) {
        if (context.isHost) {
            Network.host(LobbyConfiguration("Mara's arena"), PlayerProfile(context.playerName), context.port)
            ScenarioLog.event("hosting", "port" to context.port)
        } else {
            Network.join(context.joinAddress, context.joinPort, PlayerProfile(context.playerName))
            ScenarioLog.event("joined")
        }
        runner.awaitConnected(3)
        if (Network.isMaster) {
            delay(300)
            Network.loadLevel("res://scenes/arena.tscn", policy)
        }

        runner.awaitUntil { Network.levelNode is Arena }
        val arena = Network.levelNode as Arena
        val startedAtLoad = Network.level.value.started
        ScenarioLog.event("level_loaded", "sequence" to Network.level.value.sequence, "started" to startedAtLoad, "frames" to arena.readyFrames)
        if (!startedAtLoad) {
            delay(1_000)
            ScenarioLog.event("still_waiting", "started" to Network.level.value.started, "frames" to arena.readyFrames)
            Network.level.first { level -> level.started }
        }
        delay(300)
        ScenarioLog.event("level_started", "frames" to arena.readyFrames, "loaded" to Network.level.value.loaded.map { id -> id.value }.sorted())
        scenarioCheck(arena.readyFrames > 0) { "the level must process once started" }
        if (Network.isMaster) runner.awaitConnected(1) else delay(500)
        Network.leave()
    }
}
