package ch.hippmann.godot.replication.sample.lobby

import ch.hippmann.godot.replication.DiscoveredSession
import ch.hippmann.godot.replication.LobbyConfiguration
import ch.hippmann.godot.replication.LobbyState
import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.NetworkState
import ch.hippmann.godot.replication.PlayerProfile
import ch.hippmann.godot.replication.core.level.LevelPolicy
import ch.hippmann.godot.replication.sample.game.Hud
import ch.hippmann.godot.replication.sample.ui.SampleTheme
import godot.annotation.Script
import godot.api.Button
import godot.api.CenterContainer
import godot.api.Control
import godot.api.GridContainer
import godot.api.HBoxContainer
import godot.api.ItemList
import godot.api.Label
import godot.api.LineEdit
import godot.api.OptionButton
import godot.api.PanelContainer
import godot.api.VBoxContainer
import godot.core.Vector2
import godot.core.asStringName
import godot.core.lambdaCallable0
import godot.core.lambdaCallable1
import godot.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine

@Script
class LobbyScreen : Control() {
    val nameField = LineEdit().apply { text = "Mara"; placeholderText = "Player name" }
    val passwordField = LineEdit().apply { placeholderText = "Password (optional)"; secret = true }
    val addressField = LineEdit().apply { text = "127.0.0.1"; placeholderText = "Address" }
    val portField = LineEdit().apply { text = "7777"; placeholderText = "Port" }
    val hostButton = Button().apply { text = "Host on LAN" }
    val hostOnlineButton = Button().apply { text = "Host online" }
    val joinButton = Button().apply { text = "Join" }
    val discoverButton = Button().apply { text = "Discover on LAN" }
    val codeField = LineEdit().apply { placeholderText = "Session code"; maxLength = 6 }
    val joinCodeButton = Button().apply { text = "Join by code" }
    val codeLabel = Label().apply { modulate = SampleTheme.accent }
    val readyButton = Button().apply { text = "Ready"; toggleMode = true }
    val levelSelector = OptionButton()
    val startButton = Button().apply { text = "Start level" }
    val leaveButton = Button().apply { text = "Leave" }
    val players = ItemList()
    val sessions = ItemList()
    val status = Label()
    private var discovered: List<DiscoveredSession> = emptyList()

    override fun _ready() {
        theme = SampleTheme.create()
        setAnchorsAndOffsetsPreset(LayoutPreset.PRESET_FULL_RECT)
        val center = CenterContainer()
        addChild(center)
        center.setAnchorsAndOffsetsPreset(LayoutPreset.PRESET_FULL_RECT)
        val panel = PanelContainer().apply { customMinimumSize = Vector2(680, 0) }
        center.addChild(panel)
        val column = VBoxContainer().also { panel.addChild(it) }
        column.addThemeConstantOverride("separation".asStringName(), 6)
        column.addChild(Label().apply { text = "Replication sample"; addThemeFontSizeOverride("font_size".asStringName(), 22) })
        column.addChild(Label().apply { text = "Host a lobby or join one, get everybody ready, then start a level."; modulate = SampleTheme.muted })
        column.addChild(form())
        column.addChild(row(hostButton, joinButton, discoverButton))
        column.addChild(row(hostOnlineButton, codeField, joinCodeButton))
        column.addChild(codeLabel)
        column.addChild(Label().apply { text = "Players" })
        column.addChild(players.apply { customMinimumSize = Vector2(0, 84) })
        column.addChild(Label().apply { text = "Sessions on this network" })
        column.addChild(sessions.apply { customMinimumSize = Vector2(0, 56) })
        levelSelector.addItem("Arena")
        levelSelector.addItem("Hangar")
        column.addChild(row(readyButton, levelSelector, startButton, leaveButton))
        column.addChild(status.apply { modulate = SampleTheme.muted })
        connectControls()
        launch { combine(Network.state, Network.lobby) { state, lobby -> state to lobby }.collect { (state, lobby) -> render(state, lobby) } }
    }

    private fun connectControls() {
        hostButton.pressed.connect(lambdaCallable0<Unit> { host(online = false) })
        hostOnlineButton.pressed.connect(lambdaCallable0<Unit> { host(online = true) })
        joinButton.pressed.connect(lambdaCallable0<Unit> { join() })
        joinCodeButton.pressed.connect(lambdaCallable0<Unit> { joinByCode() })
        codeField.textSubmitted.connect(lambdaCallable1<Unit, String> { if (Network.state.value == NetworkState.Offline) joinByCode() })
        discoverButton.pressed.connect(lambdaCallable0<Unit> { discover() })
        leaveButton.pressed.connect(lambdaCallable0<Unit> { launch { Network.leave() } })
        startButton.pressed.connect(lambdaCallable0<Unit> { startLevel() })
        readyButton.toggled.connect(lambdaCallable1<Unit, Boolean> { ready -> if (Network.state.value == NetworkState.Connected) Network.setReady(ready) })
        sessions.itemSelected.connect(lambdaCallable1<Unit, Long> { index -> selectSession(index.toInt()) })
        for (field in listOf(nameField, passwordField, addressField, portField)) {
            field.textSubmitted.connect(lambdaCallable1<Unit, String> { if (Network.state.value == NetworkState.Offline) join() })
        }
    }

    private fun host(online: Boolean) = launch {
        runCatching {
            Network.host(LobbyConfiguration("${profile().name}'s lobby", password = passwordField.text.ifBlank { null }), profile(), portField.text.toInt(), online)
        }.onFailure { failure -> status.text = "Hosting failed: ${failure.message}" }
    }

    private fun joinByCode() = launch {
        runCatching {
            Network.joinByCode(codeField.text.trim(), profile(), passwordField.text.ifBlank { null })
        }.onFailure { failure -> status.text = "Join by code failed: ${failure.message}" }
    }

    private fun join() = launch {
        runCatching {
            Network.join(addressField.text, portField.text.toInt(), profile(), passwordField.text.ifBlank { null })
        }.onFailure { failure -> status.text = "Join failed: ${failure.message}" }
    }

    private fun discover() = launch {
        status.text = "Looking for sessions..."
        discovered = Network.discoverLocalSessions()
        sessions.clear()
        for (session in discovered) {
            val lock = if (session.passwordRequired) ", password" else ""
            sessions.addItem("${session.lobbyName} at ${session.address}:${session.port}, ${session.playerCount}/${session.maximumPlayers} players$lock")
        }
        status.text = "Found ${discovered.size} session(s)"
    }

    private fun startLevel() {
        val scene = if (levelSelector.selected == 1) Hud.HANGAR else Hud.ARENA
        Network.loadLevel(scene, LevelPolicy.WaitForAll())
    }

    private fun selectSession(index: Int) {
        val session = discovered.getOrNull(index) ?: return
        addressField.text = session.address
        portField.text = session.port.toString()
    }

    private fun render(state: NetworkState, lobby: LobbyState) {
        val connected = state == NetworkState.Connected
        val online = Network.configuration.rendezvousUrl != null
        hostButton.disabled = state != NetworkState.Offline
        hostOnlineButton.disabled = state != NetworkState.Offline || !online
        joinCodeButton.disabled = state != NetworkState.Offline || !online
        codeField.editable = state == NetworkState.Offline && online
        codeLabel.text = when {
            !online -> "No rendezvous service configured, online play is off"
            Network.sessionCode != null -> "Session code ${Network.sessionCode}: others join with it from anywhere"
            else -> ""
        }
        joinButton.disabled = state != NetworkState.Offline
        discoverButton.disabled = state != NetworkState.Offline
        leaveButton.disabled = !connected
        readyButton.disabled = !connected
        startButton.disabled = !(connected && Network.isMaster)
        levelSelector.disabled = !(connected && Network.isMaster)
        players.clear()
        for (player in lobby.players) {
            val role = if (player.id == lobby.master) ", master" else ""
            val ready = if (player.ready) ", ready" else ""
            players.addItem("${player.id.value}  ${player.profile.name}$role$ready")
        }
        status.text = when (state) {
            NetworkState.Offline -> "Offline"
            is NetworkState.Joining -> "Joining: ${state.step}"
            NetworkState.Connected -> "Connected as ${Network.localPlayerId.value} to ${lobby.configuration.name}, master is ${lobby.master.value}"
            NetworkState.Leaving -> "Leaving"
        }
    }

    private fun profile() = PlayerProfile(nameField.text.ifBlank { "Player" })

    private fun form(): GridContainer = GridContainer().apply {
        columns = 2
        addThemeConstantOverride("h_separation".asStringName(), 12)
        addThemeConstantOverride("v_separation".asStringName(), 4)
        for ((label, field) in listOf("Name" to nameField, "Password" to passwordField, "Address" to addressField, "Port" to portField)) {
            addChild(Label().apply { text = label })
            addChild(field.apply { sizeFlagsHorizontal = SizeFlags.EXPAND_FILL })
        }
    }

    private fun row(vararg children: Control): HBoxContainer = HBoxContainer().also { row ->
        row.addThemeConstantOverride("separation".asStringName(), 8)
        for (child in children) {
            child.sizeFlagsHorizontal = SizeFlags.EXPAND_FILL
            row.addChild(child)
        }
    }
}
