package ch.hippmann.godot.replication.sample.scenario

import ch.hippmann.godot.replication.LobbyConfiguration
import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.PlayerProfile
import ch.hippmann.godot.replication.core.level.LevelPolicy
import ch.hippmann.godot.replication.sample.world.Door
import ch.hippmann.godot.replication.sample.world.Hangar
import godot.extension.api.getNodeAs
import kotlinx.coroutines.delay

/**
 * The hangar's bay door is placed in the scene and replicates its open flag through the generated `@Synced` binding
 * alone: nothing but the library's node added hook creates its replica.
 */
class ScenePlacedDoorScenario : Scenario {
    override suspend fun run(runner: ScenarioRunner, context: ScenarioContext) {
        if (context.isHost) {
            Network.host(LobbyConfiguration("Mara's hangar"), PlayerProfile(context.playerName), context.port)
            ScenarioLog.event("hosting", "port" to context.port)
        } else {
            Network.join(context.joinAddress, context.joinPort, PlayerProfile(context.playerName))
            ScenarioLog.event("joined")
        }
        runner.awaitConnected(2)
        if (Network.isMaster) {
            delay(300)
            Network.loadLevel("res://scenes/hangar.tscn", LevelPolicy.StartWhenLoaded)
        }
        runner.awaitUntil { Network.levelNode is Hangar && Network.level.value.started }
        val door = checkNotNull(Network.levelNode?.getNodeAs<Door>("BayDoor")) { "the hangar has no bay door" }
        ScenarioLog.event("door_seen", "owner" to Network.ownerOf(door).value, "open" to door.open)

        if (Network.isMaster) {
            delay(300)
            door.open = true
        }
        runner.awaitUntil { door.open }
        ScenarioLog.event("door_open", "owner" to Network.ownerOf(door).value)
        if (Network.isMaster) runner.awaitConnected(1) else delay(500)
        Network.leave()
    }
}
