package ch.hippmann.godot.replication.endtoend

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

data class ScenarioEvent(val name: String, val player: Int, val fields: JsonObject) {
    fun string(key: String): String? = (fields[key] as? JsonPrimitive)?.contentOrNull

    fun int(key: String): Int? = (fields[key] as? JsonPrimitive)?.intOrNull

    fun ints(key: String): List<Int> = fields[key]?.jsonArray?.mapNotNull { element -> element.jsonPrimitive.intOrNull } ?: emptyList()
}

sealed interface ScenarioResult {
    data object Pass : ScenarioResult

    data class Fail(val reason: String) : ScenarioResult
}

object ScenarioMarkers {
    private const val EVENT_PREFIX = "SCENARIO_EVENT "
    private const val RESULT_PREFIX = "SCENARIO_RESULT "

    fun parseEvent(line: String): ScenarioEvent? {
        if (!line.startsWith(EVENT_PREFIX)) return null
        val json = Json.parseToJsonElement(line.removePrefix(EVENT_PREFIX)) as JsonObject
        return ScenarioEvent(
            name = json["event"]!!.jsonPrimitive.content,
            player = json["player"]?.jsonPrimitive?.intOrNull ?: 0,
            fields = json,
        )
    }

    fun parseResult(line: String): ScenarioResult? {
        if (!line.startsWith(RESULT_PREFIX)) return null
        val body = line.removePrefix(RESULT_PREFIX)
        return if (body == "PASS") ScenarioResult.Pass else ScenarioResult.Fail(body.removePrefix("FAIL").trim())
    }
}
