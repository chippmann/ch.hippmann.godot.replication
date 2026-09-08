package ch.hippmann.godot.replication.sample.scenario

import ch.hippmann.godot.replication.LeaveReason
import ch.hippmann.godot.replication.LobbyConfiguration
import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.NetworkEvent
import ch.hippmann.godot.replication.PlayerId
import ch.hippmann.godot.replication.PlayerProfile
import kotlinx.coroutines.flow.first

/** Ready flags, profile and lobby changes replicate in both directions, then the master kicks the client. */
class LobbyFlowScenario : Scenario {
    override suspend fun run(runner: ScenarioRunner, context: ScenarioContext) {
        if (context.isHost) runHost(runner, context) else runClient(runner, context)
    }

    private suspend fun runHost(runner: ScenarioRunner, context: ScenarioContext) {
        Network.host(LobbyConfiguration("Mara's arena", settings = mapOf("mode" to "deathmatch")), PlayerProfile(context.playerName), context.port)
        ScenarioLog.event("hosting", "port" to context.port)
        runner.awaitConnected(2)

        val clientReady = Network.lobby.first { lobby -> lobby.player(PlayerId(3))?.ready == true }
        ScenarioLog.event("client_ready", "name" to clientReady.player(PlayerId(3))?.profile?.name)
        val renamed = Network.lobby.first { lobby -> lobby.player(PlayerId(3))?.profile?.name == "Tobias the Second" }
        ScenarioLog.event("client_renamed", "name" to renamed.player(PlayerId(3))?.profile?.name)

        Network.updateLobby { copy(name = "Mara's hangar", settings = settings + ("rounds" to "3")) }
        val requested = Network.lobby.first { lobby -> lobby.configuration.settings["mode"] == "capture" }
        ScenarioLog.event("client_requested_mode", "mode" to requested.configuration.settings["mode"])
        scenarioCheck(requested.configuration.name == "Mara's hangar") { "the master's rename must survive the client request" }

        Network.setReady(true)
        val allReady = Network.lobby.first { lobby -> lobby.allReady }
        ScenarioLog.event("all_ready", "players" to allReady.players.map { player -> player.profile.name })

        Network.kick(PlayerId(3), "making room")
        val left = runner.awaitEvent<NetworkEvent.MemberLeft>()
        ScenarioLog.event("member_left", "left" to left.player.value, "reason" to left.reason.name)
        runner.awaitConnected(1)
        Network.leave()
    }

    private suspend fun runClient(runner: ScenarioRunner, context: ScenarioContext) {
        Network.join(context.joinAddress, context.joinPort, PlayerProfile(context.playerName))
        ScenarioLog.event("joined")
        scenarioCheck(Network.lobby.value.configuration.settings["mode"] == "deathmatch") { "lobby settings must arrive with the admission" }

        Network.setReady(true)
        Network.updateProfile { copy(name = "Tobias the Second") }
        val renamedLobby = Network.lobby.first { lobby -> lobby.configuration.name == "Mara's hangar" }
        ScenarioLog.event("lobby_renamed", "name" to renamedLobby.configuration.name, "rounds" to renamedLobby.configuration.settings["rounds"])

        Network.requestLobbyUpdate { copy(settings = settings + ("mode" to "capture")) }
        Network.lobby.first { lobby -> lobby.configuration.settings["mode"] == "capture" }
        ScenarioLog.event("request_applied")

        val disconnected = runner.awaitEvent<NetworkEvent.Disconnected>()
        ScenarioLog.event("kicked", "reason" to disconnected.reason.name)
        scenarioCheck(disconnected.reason == LeaveReason.KICKED) { "expected a kick, got ${disconnected.reason}" }
    }
}
