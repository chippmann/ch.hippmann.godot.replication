package ch.hippmann.godot.replication.sample.scenario

import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.NetworkEvent
import ch.hippmann.godot.replication.NetworkState
import ch.hippmann.godot.replication.SessionView
import godot.annotation.Script
import godot.api.Node
import godot.api.OS
import godot.coroutines.awaitNextFrame
import godot.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

@Script
class ScenarioRunner : Node() {
    lateinit var scenario: Scenario
    lateinit var context: ScenarioContext

    override fun _ready() {
        launch {
            val exitCode = try {
                awaitNextFrame()
                Network.configure {
                    if (!context.isHost) joinPort = context.port
                    verboseTransportLogging = context.arguments["verbose-transport"] == "true"
                    val preparationMilliseconds = context.arguments.long("level-preparation-milliseconds", 0)
                    if (preparationMilliseconds > 0) levelPreparation = { delay(preparationMilliseconds) }
                }
                delay(context.startDelayMilliseconds)
                withTimeout(context.timeoutSeconds * 1000) { scenario.run(this@ScenarioRunner, context) }
                ScenarioLog.pass()
                0
            } catch (failure: Throwable) {
                ScenarioLog.fail("${failure::class.simpleName}: ${failure.message}")
                1
            }
            if (Network.state.value != NetworkState.Offline) {
                runCatching { Network.leave() }
            }
            getTree()?.quit(exitCode)
        }
    }

    suspend fun awaitMembers(count: Int): SessionView =
        Network.session.first { view -> view != null && view.members.size == count }!!

    suspend fun awaitConnected(count: Int): SessionView =
        Network.session.first { view -> view != null && view.connected.size == count }!!

    suspend inline fun <reified T : NetworkEvent> awaitEvent(): T = Network.events.filterIsInstance<T>().first()

    suspend fun awaitUntil(condition: () -> Boolean) {
        while (!condition()) awaitNextFrame()
    }

    /** Simulates a crash: no leave message, no graceful disconnect, no exit code 0. */
    fun exitAbruptly() {
        ScenarioLog.pass()
        OS.kill(OS.getProcessId())
    }
}
