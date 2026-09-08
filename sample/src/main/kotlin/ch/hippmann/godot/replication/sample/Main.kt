package ch.hippmann.godot.replication.sample

import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.sample.game.GameSession
import ch.hippmann.godot.replication.sample.lobby.LobbyScreen
import ch.hippmann.godot.replication.sample.scenario.ScenarioContext
import ch.hippmann.godot.replication.sample.scenario.ScenarioRunner
import ch.hippmann.godot.replication.sample.scenario.Scenarios
import ch.hippmann.godot.replication.sample.tour.UiTour
import godot.annotation.Script
import godot.api.Node
import godot.api.OS
import godot.coroutines.awaitNextProcess
import godot.coroutines.launch
import godot.global.GD
import kotlinx.coroutines.flow.collect

@Script
class Main : Node() {

    override fun _ready() {
        Network.ensureManager()
        val arguments = Arguments.parse(OS.getCmdlineUserArgs().toList())
        val scenarioName = arguments["scenario"]
        if (scenarioName != null) {
            val runner = ScenarioRunner()
            runner.scenario = Scenarios.create(scenarioName)
            runner.context = ScenarioContext.from(arguments)
            addChild(runner)
            return
        }
        val lobby = LobbyScreen()
        val session = GameSession()
        addChild(lobby)
        addChild(session)
        launch {
            awaitNextProcess()
            GD.print("SAMPLE_STARTED manager=${Network.isManagerInstalled} state=${Network.state.value}")
        }
        launch { Network.events.collect { lobby.visible = !session.inLevel } }
        launch { Network.state.collect { lobby.visible = !session.inLevel } }
        if (arguments["tour"] != null) {
            addChild(UiTour().also { tour -> tour.lobby = lobby; tour.session = session; tour.arguments = arguments })
        }
    }
}
