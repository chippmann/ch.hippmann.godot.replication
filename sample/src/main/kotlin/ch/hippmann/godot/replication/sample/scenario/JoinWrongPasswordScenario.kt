package ch.hippmann.godot.replication.sample.scenario

import ch.hippmann.godot.replication.JoinFailure
import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.NetworkEvent
import ch.hippmann.godot.replication.NetworkState
import ch.hippmann.godot.replication.core.lobby.LobbyConfiguration
import ch.hippmann.godot.replication.core.lobby.PlayerProfile
import ch.hippmann.godot.replication.core.wire.RejectReason

class JoinWrongPasswordScenario : Scenario {
    override suspend fun run(runner: ScenarioRunner, context: ScenarioContext) {
        if (context.isHost) runHost(runner, context) else runClient(context)
    }

    private suspend fun runHost(runner: ScenarioRunner, context: ScenarioContext) {
        Network.host(LobbyConfiguration("Mara's arena", password = context.password), PlayerProfile(context.playerName), context.port)
        ScenarioLog.event("hosting", "port" to context.port)
        val rejected = runner.awaitEvent<NetworkEvent.JoinAttemptRejected>()
        ScenarioLog.event("join_rejected", "reason" to rejected.reason.name)
        scenarioCheck(Network.session.value?.members?.size == 1) { "the rejected joiner must not become a member" }
        Network.leave()
    }

    private suspend fun runClient(context: ScenarioContext) {
        val failure = try {
            Network.join(context.joinAddress, context.joinPort, PlayerProfile(context.playerName), context.password)
            null
        } catch (failure: JoinFailure) {
            failure
        }
        scenarioCheck(failure is JoinFailure.Rejected && failure.reason == RejectReason.WRONG_PASSWORD) { "expected a wrong password rejection, got $failure" }
        ScenarioLog.event("join_rejected", "reason" to (failure as JoinFailure.Rejected).reason.name)
        scenarioCheck(Network.state.value == NetworkState.Offline) { "expected Offline after the rejection" }
    }
}
