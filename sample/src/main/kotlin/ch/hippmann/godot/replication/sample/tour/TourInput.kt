package ch.hippmann.godot.replication.sample.tour

import godot.api.Control
import godot.api.Input
import godot.api.InputEventKey
import godot.api.InputEventMouseButton
import godot.api.InputEventMouseMotion
import godot.api.LineEdit
import godot.api.Node
import godot.core.Key
import godot.core.MouseButton
import godot.core.Vector2
import godot.coroutines.awaitNextFrame
import godot.global.GD
import kotlinx.coroutines.delay

/** Mouse and keyboard events fed through Godot's input pipeline, plus screenshots of the drawn viewport. */
class TourInput(private val node: Node, private val role: String, private val screenshotDirectory: String) {

    suspend fun click(control: Control) {
        val center = control.getGlobalRect().getCenter()
        Input.parseInputEvent(InputEventMouseMotion().apply { position = center; globalPosition = center })
        awaitNextFrame()
        Input.parseInputEvent(mouseButton(center, pressed = true))
        awaitNextFrame()
        Input.parseInputEvent(mouseButton(center, pressed = false))
        awaitNextFrame()
    }

    suspend fun typeInto(field: LineEdit, text: String) {
        click(field)
        field.selectAll()
        for (character in text) key(keyOf(character), character.code.toLong())
    }

    suspend fun key(key: Key, unicode: Long = 0) {
        Input.parseInputEvent(InputEventKey().apply { keycode = key; physicalKeycode = key; this.unicode = unicode; pressed = true })
        awaitNextFrame()
        Input.parseInputEvent(InputEventKey().apply { keycode = key; physicalKeycode = key; this.unicode = unicode; pressed = false })
        awaitNextFrame()
    }

    suspend fun walk(action: String, seconds: Double) {
        Input.actionPress(action)
        waitSeconds(seconds)
        Input.actionRelease(action)
        awaitNextFrame()
    }

    suspend fun waitFrames(count: Int) {
        repeat(count) { awaitNextFrame() }
    }

    suspend fun waitSeconds(seconds: Double) {
        delay((seconds * 1000).toLong())
    }

    suspend fun await(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + STEP_TIMEOUT_MILLISECONDS
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Waited too long for $what" }
            awaitNextFrame()
        }
    }

    suspend fun screenshot(name: String) {
        awaitNextFrame()
        awaitNextFrame()
        val image = node.getViewport()?.getTexture()?.getImage() ?: return
        val path = "$screenshotDirectory/$role-$name.png"
        GD.print("SCREENSHOT $path ${image.savePng(path)}")
    }

    private fun mouseButton(at: Vector2, pressed: Boolean) = InputEventMouseButton().apply {
        position = at
        globalPosition = at
        buttonIndex = MouseButton.LEFT
        this.pressed = pressed
    }

    private fun keyOf(character: Char): Key = when {
        character.isLetter() -> Key.valueOf(character.uppercaseChar().toString())
        character.isDigit() -> Key.valueOf("KEY_$character")
        character == '.' -> Key.PERIOD
        character == ':' -> Key.COLON
        character == ' ' -> Key.SPACE
        else -> Key.NONE
    }

    private companion object {
        const val STEP_TIMEOUT_MILLISECONDS = 40_000L
    }
}
