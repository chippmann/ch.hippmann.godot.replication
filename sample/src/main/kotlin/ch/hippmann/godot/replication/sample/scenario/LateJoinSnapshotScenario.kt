package ch.hippmann.godot.replication.sample.scenario

import ch.hippmann.godot.replication.core.level.LevelPolicy
import ch.hippmann.godot.replication.LobbyConfiguration
import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.NetworkEvent
import ch.hippmann.godot.replication.PlayerId
import ch.hippmann.godot.replication.PlayerProfile
import ch.hippmann.godot.replication.sample.world.Arena
import ch.hippmann.godot.replication.sample.world.Crate
import ch.hippmann.godot.replication.sample.world.Loadout
import ch.hippmann.godot.replication.sample.world.Player
import godot.api.Node
import godot.api.PackedScene
import godot.core.NodePath
import godot.global.GD
import kotlinx.coroutines.delay

/** Two members build a world; a third joins afterwards and must find the level, the players and the crate state. */
class LateJoinSnapshotScenario : Scenario {
    override suspend fun run(runner: ScenarioRunner, context: ScenarioContext) {
        if (context.arguments["behavior"] == "late") return runLateJoiner(runner, context)
        if (context.isHost) {
            Network.host(LobbyConfiguration("Mara's arena"), PlayerProfile(context.playerName), context.port)
            ScenarioLog.event("hosting", "port" to context.port)
        } else {
            Network.join(context.joinAddress, context.joinPort, PlayerProfile(context.playerName))
            ScenarioLog.event("joined")
        }
        runner.awaitConnected(2)
        if (Network.isMaster) Network.loadLevel("res://scenes/arena.tscn", LevelPolicy.StartWhenLoaded)
        runner.awaitUntil { Network.levelNode is Arena }
        val arena = Network.levelNode as Arena

        val scene = GD.load<PackedScene>("res://scenes/player.tscn") ?: throw ScenarioFailure("player scene missing")
        val spawns = arena.getNodeOrNull(NodePath("Spawns")) ?: throw ScenarioFailure("no spawn root")
        val player = Network.spawn<Player>(scene, spawns, spawnData = Loadout("lantern", Network.localPlayerId.value))
        player.displayName = context.playerName
        player.health = 50 + Network.localPlayerId.value
        if (Network.isMaster) {
            val crate = arena.getNodeOrNull(NodePath("SupplyCrate")) as Crate
            crate.pushes = 5
            crate.label = "pushed by master"
        }
        ScenarioLog.event("world_built")

        val lateJoiner = runner.awaitEvent<NetworkEvent.MemberJoined>()
        ScenarioLog.event("late_member_joined", "member" to lateJoiner.member.id.value)
        runner.awaitEvent<NetworkEvent.MemberLeft>()
        if (Network.isMaster) runner.awaitConnected(1) else delay(500)
        Network.leave()
    }

    private suspend fun runLateJoiner(runner: ScenarioRunner, context: ScenarioContext) {
        val view = Network.join(context.joinAddress, context.joinPort, PlayerProfile(context.playerName))
        ScenarioLog.event("joined", "members" to view.memberIds.map { id -> id.value })
        val level = Network.levelNode ?: throw ScenarioFailure("the level was not loaded before the join completed")
        val arena = level as Arena
        scenarioCheck(Network.level.value.started) { "the level must be started for a late joiner" }
        val players = arena.getNode(NodePath("Spawns"))?.getChildren()?.filterIsInstance<Player>().orEmpty()
        val crate = arena.getNodeOrNull(NodePath("SupplyCrate")) as Crate
        ScenarioLog.event(
            "snapshot_seen",
            "players" to players.map { player -> player.displayName }.sorted(),
            "healths" to players.map { player -> player.health }.sorted(),
            "credits" to players.map { player -> player.loadout.credits }.sorted(),
            "crate_pushes" to crate.pushes,
            "crate_label" to crate.label,
            "crate_owner" to Network.ownerOf(crate).value,
        )
        scenarioCheck(players.size == 2) { "expected two players in the snapshot, saw ${players.size}" }
        scenarioCheck(crate.pushes == 5 && Network.ownerOf(crate) == PlayerId(2)) { "the scene placed crate must carry the master's state" }
        delay(300)
        Network.leave()
    }
}
