package ch.hippmann.godot.replication.sample.tour

import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.NetworkState
import ch.hippmann.godot.replication.sample.Arguments
import ch.hippmann.godot.replication.sample.game.GameSession
import ch.hippmann.godot.replication.sample.game.Hud
import ch.hippmann.godot.replication.sample.lobby.LobbyScreen
import ch.hippmann.godot.replication.sample.world.Crate
import ch.hippmann.godot.replication.sample.world.Player
import godot.annotation.Script
import godot.api.Node
import godot.core.Key
import godot.core.Vector2
import godot.coroutines.awaitNextFrame
import godot.coroutines.launch
import godot.global.GD
import kotlinx.coroutines.withTimeout

/**
 * Drives the sample the way a person would, through real mouse and keyboard events, and saves screenshots
 * along the way. Started with `--tour=host` or `--tour=join` and `--screenshot-dir=<absolute directory>`.
 */
@Script
class UiTour : Node() {
    lateinit var lobby: LobbyScreen
    lateinit var session: GameSession
    lateinit var arguments: Arguments
    private lateinit var input: TourInput

    override fun _ready() {
        input = TourInput(this, arguments["tour"] ?: "host", arguments["screenshot-dir"] ?: "user://")
        launch {
            val outcome = runCatching { withTimeout(TOUR_TIMEOUT_MILLISECONDS) { run(arguments["tour"] ?: "host") } }
            outcome.onFailure { failure -> GD.printErr("TOUR_FAILED ${failure::class.simpleName}: ${failure.message}") }
            GD.print(if (outcome.isSuccess) "TOUR_RESULT PASS" else "TOUR_RESULT FAIL")
            awaitNextFrame()
            getTree()?.quit(if (outcome.isSuccess) 0 else 1)
        }
    }

    private suspend fun run(role: String) {
        input.waitFrames(20)
        input.typeInto(lobby.nameField, arguments["name"] ?: if (role == "host") "Mara" else "Tobias")
        arguments["port"]?.let { port -> input.typeInto(lobby.portField, port) }
        input.screenshot("1-lobby-form")
        when (role) {
            "host" -> hostTour()
            "host-online" -> hostOnlineTour()
            "join-code" -> joinCodeTour()
            "late" -> lateTour()
            "discover" -> discoverTour()
            else -> joinTour()
        }
    }

    private suspend fun hostTour() {
        input.click(lobby.hostButton)
        input.await("hosting") { Network.state.value == NetworkState.Connected }
        input.screenshot("2-lobby-hosting")
        input.await("a second player") { Network.lobby.value.players.size >= 2 }
        input.click(lobby.readyButton)
        input.waitFrames(10)
        input.screenshot("3-lobby-two-players")
        input.click(lobby.startButton)
        input.await("the arena") { session.inLevel }
        input.waitSeconds(1.5)
        input.screenshot("4-arena")
        input.walk("move_forward", 1.0)
        input.walk("move_right", 0.6)
        input.key(Key.SPACE)
        input.waitSeconds(0.4)
        input.key(Key.E)
        input.screenshot("5a-crate-mid-push")
        input.waitSeconds(1.0)
        input.screenshot("5-arena-played")
        if (arguments["expect-late"] == "true") {
            input.await("the late joiner") { Network.lobby.value.players.size >= 3 && Player.byOwner.size >= 3 }
            input.waitSeconds(1.0)
            input.screenshot("5b-late-joiner-arrived")
        }
        input.click(session.hud.switchLevelButton)
        input.await("the hangar") { Network.level.value.scenePath == Hud.HANGAR && Network.level.value.started }
        input.waitSeconds(1.5)
        input.screenshot("6-hangar")
        input.key(Key.ESCAPE)
        input.await("leaving") { Network.state.value == NetworkState.Offline }
        input.waitFrames(10)
        input.screenshot("7-lobby-after-leave")
    }

    private suspend fun joinTour() {
        input.click(lobby.joinButton)
        input.await("joining") { Network.state.value == NetworkState.Connected }
        input.waitFrames(10)
        input.screenshot("2-lobby-joined")
        input.click(lobby.readyButton)
        input.await("the arena") { session.inLevel }
        input.waitSeconds(1.5)
        input.screenshot("4-arena")
        input.walk("move_left", 0.8)
        input.key(Key.SPACE)
        input.await("the host's crate push") { Crate.all.any { crate -> crate.pushes > 0 } }
        input.screenshot("5a-crate-mid-push")
        input.waitSeconds(1.0)
        input.screenshot("5-arena-played")
        input.await("the hangar") { Network.level.value.scenePath == Hud.HANGAR && Network.level.value.started }
        input.waitSeconds(1.5)
        input.screenshot("6-hangar")
        input.await("becoming master") { Network.isMaster }
        input.waitSeconds(0.5)
        input.screenshot("7-hangar-as-master")
        input.key(Key.ESCAPE)
        input.await("leaving") { Network.state.value == NetworkState.Offline }
    }

    /** Joins while the others already play, must land in the running level, and stays until somebody found it by discovery. */
    private suspend fun lateTour() {
        input.waitSeconds(arguments.long("join-after-seconds", 8).toDouble())
        input.click(lobby.joinButton)
        input.await("joining a running level") { Network.state.value == NetworkState.Connected }
        input.await("the running level") { session.inLevel }
        input.waitSeconds(1.5)
        input.screenshot("2-joined-running-level")
        input.walk("move_forward", 0.6)
        input.await("the hangar") { Network.level.value.scenePath == Hud.HANGAR && Network.level.value.started }
        input.waitSeconds(1.5)
        input.screenshot("3-hangar")
        input.await("becoming master") { Network.isMaster }
        input.waitSeconds(0.5)
        input.screenshot("4-master-after-two-left")
        input.await("a player who found this session") { Network.lobby.value.players.size >= 2 && Player.byOwner.size >= 2 }
        input.waitSeconds(1.0)
        input.screenshot("5-discovered-joiner-arrived")
        input.key(Key.ESCAPE)
        input.await("leaving") { Network.state.value == NetworkState.Offline }
    }

    /** Finds the session through LAN discovery after the original host is gone and joins the promoted master. */
    private suspend fun discoverTour() {
        input.waitSeconds(arguments.long("join-after-seconds", 20).toDouble())
        val deadline = System.currentTimeMillis() + DISCOVERY_TIMEOUT_MILLISECONDS
        while (lobby.sessions.itemCount == 0) {
            check(System.currentTimeMillis() < deadline) { "No session was discovered after the master changed" }
            input.click(lobby.discoverButton)
            input.await("the discovery answer") { !lobby.status.text.startsWith("Looking") }
        }
        input.screenshot("2-discovered-promoted-master")
        input.clickInside(lobby.sessions, Vector2(24, 14))
        input.waitFrames(5)
        input.click(lobby.joinButton)
        input.await("joining the promoted master") { Network.state.value == NetworkState.Connected }
        input.await("the running level") { session.inLevel }
        input.waitSeconds(1.5)
        input.screenshot("3-joined-after-master-change")
        input.key(Key.ESCAPE)
        input.await("leaving") { Network.state.value == NetworkState.Offline }
    }

    /** Hosts through the rendezvous service, writes the code to `--code-file` for the other tour, then plays as usual. */
    private suspend fun hostOnlineTour() {
        input.click(lobby.hostOnlineButton)
        input.await("hosting online") { Network.state.value == NetworkState.Connected && Network.sessionCode != null }
        input.waitFrames(10)
        input.screenshot("2-hosting-online-with-code")
        arguments["code-file"]?.let { path -> java.io.File(path).writeText(Network.sessionCode.orEmpty()) }
        input.await("a player who joined by code") { Network.lobby.value.players.size >= 2 }
        input.click(lobby.readyButton)
        input.waitFrames(10)
        input.screenshot("3-lobby-two-players")
        input.click(lobby.startButton)
        input.await("the arena") { session.inLevel }
        input.waitSeconds(1.5)
        input.screenshot("4-arena")
        input.walk("move_forward", 1.0)
        input.waitSeconds(1.0)
        input.screenshot("5-arena-played")
        input.key(Key.ESCAPE)
        input.await("leaving") { Network.state.value == NetworkState.Offline }
    }

    /** Waits for the code the host wrote, types it by keyboard and joins through the service. */
    private suspend fun joinCodeTour() {
        val codeFile = java.io.File(arguments["code-file"] ?: error("--code-file is required"))
        input.await("the host's code") { codeFile.isFile && codeFile.readText().length == 6 }
        input.typeInto(lobby.codeField, codeFile.readText().trim())
        input.screenshot("2-code-typed")
        input.click(lobby.joinCodeButton)
        input.await("joining by code") { Network.state.value == NetworkState.Connected }
        input.waitFrames(10)
        input.screenshot("3-joined-by-code")
        input.click(lobby.readyButton)
        input.await("the arena") { session.inLevel }
        input.waitSeconds(1.5)
        input.screenshot("4-arena")
        input.walk("move_left", 0.8)
        input.waitSeconds(1.0)
        input.screenshot("5-arena-played")
        input.await("the host leaving") { Network.isMaster }
        input.key(Key.ESCAPE)
        input.await("leaving") { Network.state.value == NetworkState.Offline }
    }

    private companion object {
        const val TOUR_TIMEOUT_MILLISECONDS = 150_000L
        const val DISCOVERY_TIMEOUT_MILLISECONDS = 40_000L
    }
}
