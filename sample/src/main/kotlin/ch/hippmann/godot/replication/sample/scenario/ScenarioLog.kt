package ch.hippmann.godot.replication.sample.scenario

import ch.hippmann.godot.replication.Network
import godot.global.GD

/** Machine readable markers the end-to-end harness parses from standard output. */
object ScenarioLog {
    fun event(name: String, vararg fields: Pair<String, Any?>) {
        val json = buildString {
            append("{\"event\":").append(quote(name))
            append(",\"player\":").append(Network.localPlayerId.value)
            for ((key, value) in fields) {
                append(',').append(quote(key)).append(':').append(jsonValue(value))
            }
            append('}')
        }
        GD.print("SCENARIO_EVENT $json")
    }

    fun pass() {
        GD.print("SCENARIO_RESULT PASS")
    }

    fun fail(reason: String) {
        GD.print("SCENARIO_RESULT FAIL $reason")
    }

    private fun jsonValue(value: Any?): String = when (value) {
        null -> "null"
        is Number, is Boolean -> value.toString()
        is Collection<*> -> value.joinToString(",", "[", "]") { element -> jsonValue(element) }
        else -> quote(value.toString())
    }

    private fun quote(text: String): String = buildString {
        append('"')
        for (character in text) {
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                else -> append(character)
            }
        }
        append('"')
    }
}
