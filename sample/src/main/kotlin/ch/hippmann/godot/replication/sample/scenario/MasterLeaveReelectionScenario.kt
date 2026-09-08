package ch.hippmann.godot.replication.sample.scenario

import ch.hippmann.godot.replication.LobbyConfiguration
import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.NetworkEvent
import ch.hippmann.godot.replication.PlayerId
import ch.hippmann.godot.replication.PlayerProfile
import kotlinx.coroutines.delay

/**
 * The host leaves, the remaining members elect the lowest id as master, and a late joiner still gets in by
 * dialing a non master member, which redirects it.
 */
class MasterLeaveReelectionScenario : Scenario {
    override suspend fun run(runner: ScenarioRunner, context: ScenarioContext) {
        when (context.arguments["behavior"]) {
            "late" -> runLateJoiner(runner, context)
            else -> if (context.isHost) runHost(runner, context) else runMember(runner, context)
        }
    }

    private suspend fun runHost(runner: ScenarioRunner, context: ScenarioContext) {
        Network.host(LobbyConfiguration("Mara's arena", password = context.password), PlayerProfile(context.playerName), context.port)
        ScenarioLog.event("hosting", "port" to context.port)
        runner.awaitConnected(context.expectedPlayers)
        ScenarioLog.event("mesh_complete")
        delay(500)
        Network.leave()
        ScenarioLog.event("host_left")
    }

    private suspend fun runMember(runner: ScenarioRunner, context: ScenarioContext) {
        Network.join(context.joinAddress, context.joinPort, PlayerProfile(context.playerName), context.password)
        ScenarioLog.event("joined")
        runner.awaitConnected(context.expectedPlayers)
        ScenarioLog.event("mesh_complete")

        val changed = runner.awaitEvent<NetworkEvent.MasterChanged>()
        ScenarioLog.event("master_changed", "master" to changed.master.value, "epoch" to changed.epoch.value, "is_master" to Network.isMaster)
        scenarioCheck(changed.master == PlayerId(3)) { "the lowest remaining id must become master, got ${changed.master}" }

        val lateJoiner = runner.awaitEvent<NetworkEvent.MemberJoined>()
        ScenarioLog.event("late_member_joined", "member" to lateJoiner.member.id.value, "epoch" to Network.session.value?.epoch?.value)
        runner.awaitConnected(3)
        ScenarioLog.event("late_mesh_complete", "connected" to Network.session.value?.connected?.map { id -> id.value }?.sorted())
        if (Network.isMaster) {
            runner.awaitConnected(1)
        } else {
            delay(1_000)
        }
        Network.leave()
    }

    private suspend fun runLateJoiner(runner: ScenarioRunner, context: ScenarioContext) {
        val view = Network.join(context.joinAddress, context.joinPort, PlayerProfile(context.playerName), context.password)
        ScenarioLog.event("joined", "master" to view.master.value, "epoch" to view.epoch.value, "members" to view.memberIds.map { id -> id.value })
        scenarioCheck(view.master == PlayerId(3)) { "the late joiner must see the re-elected master" }
        scenarioCheck(view.epoch.value == 2) { "the late joiner must see epoch 2, saw ${view.epoch.value}" }
        runner.awaitConnected(3)
        delay(500)
        Network.leave()
    }
}
