package ch.hippmann.godot.replication.sample.tour

import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.NetworkState
import ch.hippmann.godot.replication.sample.Arguments
import ch.hippmann.godot.replication.sample.game.GameSession
import ch.hippmann.godot.replication.sample.game.Hud
import ch.hippmann.godot.replication.sample.lobby.LobbyScreen
import godot.annotation.Script
import godot.api.Node
import godot.core.Key
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
        if (role == "host") hostTour() else joinTour()
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
        input.waitSeconds(1.0)
        input.screenshot("5-arena-played")
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

    private companion object {
        const val TOUR_TIMEOUT_MILLISECONDS = 150_000L
    }
}
