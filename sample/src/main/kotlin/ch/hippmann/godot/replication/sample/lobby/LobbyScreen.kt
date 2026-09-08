package ch.hippmann.godot.replication.sample.lobby

import ch.hippmann.godot.replication.DiscoveredSession
import ch.hippmann.godot.replication.LobbyConfiguration
import ch.hippmann.godot.replication.LobbyState
import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.NetworkState
import ch.hippmann.godot.replication.PlayerProfile
import godot.annotation.Script
import godot.api.Button
import godot.api.Control
import godot.api.HBoxContainer
import godot.api.ItemList
import godot.api.Label
import godot.api.LineEdit
import godot.api.VBoxContainer
import godot.core.asStringName
import godot.core.lambdaCallable0
import godot.core.lambdaCallable1
import godot.coroutines.launch
import godot.global.GD
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine

@Script
class LobbyScreen : Control() {
    private val nameField = LineEdit().apply { text = "Mara"; placeholderText = "Player name" }
    private val addressField = LineEdit().apply { text = "127.0.0.1"; placeholderText = "Address" }
    private val portField = LineEdit().apply { text = "7777"; placeholderText = "Port" }
    private val passwordField = LineEdit().apply { placeholderText = "Password"; secret = true }
    private val hostButton = Button().apply { text = "Host" }
    private val joinButton = Button().apply { text = "Join" }
    private val discoverButton = Button().apply { text = "Discover" }
    private val leaveButton = Button().apply { text = "Leave" }
    private val readyButton = Button().apply { text = "Ready"; toggleMode = true }
    private val players = ItemList()
    private val sessions = ItemList()
    private val status = Label()
    private var discovered: List<DiscoveredSession> = emptyList()

    override fun _ready() {
        setAnchorsPreset(LayoutPreset.PRESET_FULL_RECT)
        val column = VBoxContainer().also { addChild(it) }
        column.setAnchorsPreset(LayoutPreset.PRESET_FULL_RECT)
        column.addChild(row(nameField, passwordField))
        column.addChild(row(addressField, portField, joinButton, hostButton, discoverButton))
        column.addChild(row(readyButton, leaveButton))
        column.addChild(Label().apply { text = "Players" })
        column.addChild(players.apply { customMinimumSize = godot.core.Vector2(0, 120) })
        column.addChild(Label().apply { text = "Sessions on this network" })
        column.addChild(sessions.apply { customMinimumSize = godot.core.Vector2(0, 120) })
        column.addChild(status)

        hostButton.pressed.connect(lambdaCallable0<Unit> { host() })
        joinButton.pressed.connect(lambdaCallable0<Unit> { join() })
        discoverButton.pressed.connect(lambdaCallable0<Unit> { discover() })
        leaveButton.pressed.connect(lambdaCallable0<Unit> { launch { Network.leave() } })
        readyButton.toggled.connect(lambdaCallable1<Unit, Boolean> { ready -> if (Network.state.value == NetworkState.Connected) Network.setReady(ready) })
        sessions.itemSelected.connect(lambdaCallable1<Unit, Long> { index -> selectSession(index.toInt()) })

        launch {
            combine(Network.state, Network.lobby) { state, lobby -> state to lobby }.collect { (state, lobby) -> render(state, lobby) }
        }
    }

    private fun host() = launch {
        runCatching {
            Network.host(LobbyConfiguration("${nameField.text}'s lobby", password = passwordField.text.ifBlank { null }), profile(), portField.text.toInt())
        }.onFailure { failure -> status.text = "Hosting failed: ${failure.message}" }
    }

    private fun join() = launch {
        runCatching {
            Network.join(addressField.text, portField.text.toInt(), profile(), passwordField.text.ifBlank { null })
        }.onFailure { failure -> status.text = "Join failed: ${failure.message}" }
    }

    private fun discover() = launch {
        status.text = "Discovering..."
        discovered = Network.discoverLocalSessions()
        sessions.clear()
        for (session in discovered) {
            val lock = if (session.passwordRequired) " (password)" else ""
            sessions.addItem("${session.lobbyName} at ${session.address}:${session.port} ${session.playerCount}/${session.maximumPlayers}$lock")
        }
        status.text = "Found ${discovered.size} session(s)"
    }

    private fun selectSession(index: Int) {
        val session = discovered.getOrNull(index) ?: return
        addressField.text = session.address
        portField.text = session.port.toString()
    }

    private fun render(state: NetworkState, lobby: LobbyState) {
        val connected = state == NetworkState.Connected
        hostButton.disabled = state != NetworkState.Offline
        joinButton.disabled = state != NetworkState.Offline
        leaveButton.disabled = !connected
        readyButton.disabled = !connected
        players.clear()
        for (player in lobby.players) {
            val role = if (player.id == lobby.master) " (master)" else ""
            val ready = if (player.ready) " ready" else ""
            players.addItem("${player.id.value}: ${player.profile.name}$role$ready")
        }
        status.text = when (state) {
            NetworkState.Offline -> "Offline"
            is NetworkState.Joining -> "Joining: ${state.step}"
            NetworkState.Connected -> "Connected as ${Network.localPlayerId.value} to ${lobby.configuration.name}, master ${lobby.master.value}"
            NetworkState.Leaving -> "Leaving"
        }
    }

    private fun profile() = PlayerProfile(nameField.text.ifBlank { "Player" })

    private fun row(vararg children: Control): HBoxContainer = HBoxContainer().also { row ->
        for (child in children) {
            child.sizeFlagsHorizontal = SizeFlags.EXPAND_FILL
            row.addChild(child)
        }
        row.name = "Row${children.first().name}".asStringName()
    }
}
