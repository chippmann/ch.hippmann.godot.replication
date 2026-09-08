package ch.hippmann.godot.replication.sample.game

import godot.global.GD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The last few things worth telling the player, shown by the HUD. */
object GameLog {
    private val mutableLines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = mutableLines

    fun note(message: String) {
        GD.print("GAME_LOG $message")
        mutableLines.value = (mutableLines.value + message).takeLast(MAXIMUM_LINES)
    }

    fun clear() {
        mutableLines.value = emptyList()
    }

    private const val MAXIMUM_LINES = 5
}
