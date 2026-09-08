package ch.hippmann.godot.replication.sample.scenario

import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.NetworkEvent
import ch.hippmann.godot.replication.NetworkState
import ch.hippmann.godot.replication.core.lobby.LobbyConfiguration
import ch.hippmann.godot.replication.core.lobby.PlayerProfile
import kotlinx.coroutines.delay

class HostJoinPasswordScenario : Scenario {
    override suspend fun run(runner: ScenarioRunner, context: ScenarioContext) {
        if (context.isHost) runHost(runner, context) else runClient(runner, context)
    }

    private suspend fun runHost(runner: ScenarioRunner, context: ScenarioContext) {
        Network.host(LobbyConfiguration("Mara's arena", password = context.password), PlayerProfile(context.playerName), context.port)
        ScenarioLog.event("hosting", "port" to context.port, "master" to Network.master.value)
        val view = runner.awaitMembers(2)
        ScenarioLog.event("member_joined", "members" to view.memberIds.map { id -> id.value })
        val left = runner.awaitEvent<NetworkEvent.MemberLeft>()
        ScenarioLog.event("member_left", "left" to left.player.value, "reason" to left.reason.name)
        Network.leave()
    }

    private suspend fun runClient(runner: ScenarioRunner, context: ScenarioContext) {
        val view = Network.join(context.joinAddress, context.joinPort, PlayerProfile(context.playerName), context.password)
        ScenarioLog.event("joined", "master" to view.master.value, "members" to view.memberIds.map { id -> id.value })
        scenarioCheck(Network.state.value == NetworkState.Connected) { "expected Connected, was ${Network.state.value}" }
        scenarioCheck(view.members.size == 2) { "expected two members, saw ${view.members.size}" }
        scenarioCheck(!view.isMaster) { "the joiner must not be the master" }
        delay(500)
        Network.leave()
        scenarioCheck(Network.state.value == NetworkState.Offline) { "expected Offline after leave" }
    }
}
