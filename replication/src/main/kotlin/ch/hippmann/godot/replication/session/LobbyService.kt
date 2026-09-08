package ch.hippmann.godot.replication.session

import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.core.lobby.LobbyConfiguration
import ch.hippmann.godot.replication.core.lobby.LobbyPlayer
import ch.hippmann.godot.replication.core.lobby.LobbyState
import ch.hippmann.godot.replication.core.lobby.PlayerProfile
import ch.hippmann.godot.replication.core.session.PlayerId
import ch.hippmann.godot.replication.core.wire.Kick
import ch.hippmann.godot.replication.core.wire.LeaveReason
import ch.hippmann.godot.replication.core.wire.LobbyCommand
import ch.hippmann.godot.replication.core.wire.LobbyCommandPayload
import ch.hippmann.godot.replication.core.wire.LobbyUpdate
import ch.hippmann.godot.replication.core.wire.PlayerUpdate
import ch.hippmann.godot.replication.core.wire.WireMessage
import ch.hippmann.godot.replication.transport.EnetLink
import godot.coroutines.launch

/** Ready flags, profiles and the lobby configuration; every change is replicated so any member can take over. */
internal class LobbyService(private val runtime: SessionRuntime) {

    fun currentState(): LobbyState? {
        val membership = runtime.membership ?: return null
        return LobbyState(
            configuration = runtime.lobbyConfiguration.withoutPassword(),
            players = membership.members.values.sortedBy { member -> member.id.value }.map { member -> LobbyPlayer(member.id, member.profile, member.ready) },
            master = membership.master,
        )
    }

    fun setReady(ready: Boolean) {
        updateLocalRecord(runtime.localProfile, ready)
    }

    fun updateProfile(profile: PlayerProfile) {
        runtime.localProfile = profile
        val ready = runtime.membership?.members?.get(runtime.localPlayerId)?.ready ?: false
        updateLocalRecord(profile, ready)
    }

    fun updateLobby(configuration: LobbyConfiguration) {
        if (runtime.isMaster) {
            applyLobby(configuration.copy(password = runtime.lobbyConfiguration.password))
        } else {
            val master = runtime.membership?.master ?: return
            runtime.transport.sendMessage(master, LobbyCommand(LobbyCommandPayload.UpdateLobby(configuration)))
        }
    }

    fun kick(target: PlayerId, reason: String) {
        check(runtime.isMaster) { "Only the master can kick" }
        if (target == runtime.localPlayerId) return
        runtime.transport.broadcastMessage(Kick(target, reason))
        runtime.membershipHandler.removeMember(target, LeaveReason.KICKED)
        runtime.transport.linkFor(target)?.let { link -> runtime.transport.close(link, graceful = true) }
    }

    fun onMessage(link: EnetLink, message: WireMessage): Boolean {
        val sender = link.player ?: return false
        val membership = runtime.membership ?: return false
        when (message) {
            is PlayerUpdate -> if (sender == message.player) {
                membership.members[sender]?.let { record -> runtime.membership = membership.with(record.copy(profile = message.profile, ready = message.ready)) }
                runtime.publishSession()
            }
            is LobbyUpdate -> if (sender == membership.master && message.epoch.value >= membership.epoch.value) {
                runtime.lobbyConfiguration = message.configuration
                runtime.publishSession()
            }
            is LobbyCommand -> if (runtime.isMaster) onCommand(message.payload)
            is Kick -> if (sender == membership.master) onKick(message)
            else -> return false
        }
        return true
    }

    fun publish() {
        val state = currentState() ?: return
        runtime.transport.defer { Network.updateLobby(state) }
    }

    private fun onCommand(payload: LobbyCommandPayload) {
        when (payload) {
            is LobbyCommandPayload.UpdateLobby -> applyLobby(payload.configuration.copy(password = runtime.lobbyConfiguration.password))
            is LobbyCommandPayload.LoadLevel -> runtime.levelService.loadLevel(payload.scenePath, payload.policy)
        }
    }

    private fun onKick(kick: Kick) {
        if (kick.target == runtime.localPlayerId) {
            runtime.manager.launch { runtime.leave(LeaveReason.KICKED) }
        } else {
            runtime.membershipHandler.removeMember(kick.target, LeaveReason.KICKED)
        }
    }

    private fun applyLobby(configuration: LobbyConfiguration) {
        val membership = runtime.membership ?: return
        runtime.lobbyConfiguration = configuration
        runtime.transport.broadcastMessage(LobbyUpdate(membership.epoch, configuration.withoutPassword()))
        runtime.publishSession()
    }

    private fun updateLocalRecord(profile: PlayerProfile, ready: Boolean) {
        val membership = runtime.membership ?: return
        val record = membership.members[runtime.localPlayerId] ?: return
        runtime.membership = membership.with(record.copy(profile = profile, ready = ready))
        runtime.transport.broadcastMessage(PlayerUpdate(runtime.localPlayerId, profile, ready))
        runtime.publishSession()
    }
}
