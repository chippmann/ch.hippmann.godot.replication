package ch.hippmann.godot.replication.sample.scenario

import ch.hippmann.godot.replication.LeaveReason
import ch.hippmann.godot.replication.LobbyConfiguration
import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.NetworkEvent
import ch.hippmann.godot.replication.PlayerProfile
import kotlinx.coroutines.delay

/** One client leaves gracefully, the other crashes; the rest observe both with the right reasons. */
class ClientLeaveScenario : Scenario {
    override suspend fun run(runner: ScenarioRunner, context: ScenarioContext) {
        if (context.isHost) {
            Network.host(LobbyConfiguration("Mara's arena"), PlayerProfile(context.playerName), context.port)
            ScenarioLog.event("hosting", "port" to context.port)
        } else {
            Network.join(context.joinAddress, context.joinPort, PlayerProfile(context.playerName))
            ScenarioLog.event("joined")
        }
        runner.awaitConnected(context.expectedPlayers)
        ScenarioLog.event("mesh_complete")

        when (context.arguments["behavior"]) {
            "leave" -> {
                delay(500)
                Network.leave()
                ScenarioLog.event("left_gracefully")
            }
            "crash" -> {
                delay(1_500)
                ScenarioLog.event("crashing")
                runner.exitAbruptly()
            }
            else -> {
                val first = runner.awaitEvent<NetworkEvent.MemberLeft>()
                ScenarioLog.event("member_left", "left" to first.player.value, "reason" to first.reason.name)
                val second = runner.awaitEvent<NetworkEvent.MemberLeft>()
                ScenarioLog.event("member_left", "left" to second.player.value, "reason" to second.reason.name)
                runner.awaitConnected(1)
                scenarioCheck(Network.session.value?.members?.size == 1) { "only the observer should remain" }
                Network.leave()
            }
        }
    }
}
