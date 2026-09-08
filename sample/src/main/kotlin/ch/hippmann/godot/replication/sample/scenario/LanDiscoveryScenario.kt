package ch.hippmann.godot.replication.sample.scenario

import ch.hippmann.godot.replication.LobbyConfiguration
import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.NetworkEvent
import ch.hippmann.godot.replication.PlayerProfile

class LanDiscoveryScenario : Scenario {
    override suspend fun run(runner: ScenarioRunner, context: ScenarioContext) {
        Network.configure { discoveryPort = context.arguments.int("discovery-port", 7778) }
        if (context.isHost) {
            Network.host(LobbyConfiguration("Mara's arena", maximumPlayers = 4, password = context.password), PlayerProfile(context.playerName), context.port)
            ScenarioLog.event("hosting", "port" to context.port)
            runner.awaitConnected(2)
            val left = runner.awaitEvent<NetworkEvent.MemberLeft>()
            ScenarioLog.event("member_left", "left" to left.player.value)
            Network.leave()
            return
        }

        val sessions = Network.discoverLocalSessions()
        ScenarioLog.event("discovered", "count" to sessions.size, "names" to sessions.map { session -> session.lobbyName })
        val session = sessions.firstOrNull { session -> session.port == context.joinPort }
            ?: throw ScenarioFailure("the host on port ${context.joinPort} was not discovered, found $sessions")
        scenarioCheck(session.lobbyName == "Mara's arena") { "unexpected lobby name ${session.lobbyName}" }
        scenarioCheck(session.passwordRequired == (context.password != null)) { "password flag mismatch" }
        scenarioCheck(session.playerCount == 1 && session.maximumPlayers == 4) { "unexpected player counts in $session" }

        Network.join(session.address, session.port, PlayerProfile(context.playerName), context.password)
        ScenarioLog.event("joined_discovered", "address" to session.address, "port" to session.port)
        Network.leave()
    }
}
