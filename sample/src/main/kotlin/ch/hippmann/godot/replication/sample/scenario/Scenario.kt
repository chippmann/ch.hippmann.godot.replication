package ch.hippmann.godot.replication.sample.scenario

import ch.hippmann.godot.replication.sample.Arguments
import godot.api.Node

enum class Role {
    HOST,
    CLIENT,
}

class ScenarioContext(
    val role: Role,
    val port: Int,
    val joinAddress: String,
    val joinPort: Int,
    val playerName: String,
    val password: String?,
    val timeoutSeconds: Long,
    val startDelayMilliseconds: Long,
    val expectedPlayers: Int,
    val arguments: Arguments,
) {
    val isHost: Boolean
        get() = role == Role.HOST

    companion object {
        fun from(arguments: Arguments): ScenarioContext {
            val join = arguments["join"] ?: "127.0.0.1:7777"
            return ScenarioContext(
                role = if (arguments["role"] == "host") Role.HOST else Role.CLIENT,
                port = arguments.int("port", 0),
                joinAddress = join.substringBefore(":"),
                joinPort = join.substringAfter(":", "7777").toInt(),
                playerName = arguments["name"] ?: "Player",
                password = arguments["password"],
                timeoutSeconds = arguments.long("timeout-seconds", 60),
                startDelayMilliseconds = arguments.long("start-delay-milliseconds", 0),
                expectedPlayers = arguments.int("expected-players", 2),
                arguments = arguments,
            )
        }
    }
}

interface Scenario {
    suspend fun run(runner: ScenarioRunner, context: ScenarioContext)
}

class ScenarioFailure(message: String) : RuntimeException(message)

fun scenarioCheck(condition: Boolean, message: () -> String) {
    if (!condition) throw ScenarioFailure(message())
}
