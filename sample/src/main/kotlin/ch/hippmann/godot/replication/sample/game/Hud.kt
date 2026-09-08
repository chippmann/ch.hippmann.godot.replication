package ch.hippmann.godot.replication.sample.game

import ch.hippmann.godot.replication.Network
import ch.hippmann.godot.replication.core.level.LevelPolicy
import ch.hippmann.godot.replication.sample.ui.SampleTheme
import ch.hippmann.godot.replication.sample.world.Player
import godot.annotation.Script
import godot.api.Button
import godot.api.CanvasLayer
import godot.api.Control
import godot.api.HBoxContainer
import godot.api.Label
import godot.api.MarginContainer
import godot.api.PanelContainer
import godot.api.VBoxContainer
import godot.core.asStringName
import godot.core.lambdaCallable0
import godot.coroutines.launch
import kotlinx.coroutines.flow.collect

@Script
class Hud : CanvasLayer() {
    private val sessionLabel = Label()
    private val playersLabel = Label()
    private val statisticsLabel = Label()
    private val logLabel = Label().apply { modulate = SampleTheme.muted }
    val switchLevelButton = Button().apply { text = "Switch level" }
    val leaveButton = Button().apply { text = "Leave (Esc)" }

    override fun _ready() {
        val root = MarginContainer().apply {
            theme = SampleTheme.create()
            mouseFilter = Control.MouseFilter.IGNORE
            for (side in listOf("margin_left", "margin_top", "margin_right", "margin_bottom")) addThemeConstantOverride(side.asStringName(), 16)
        }
        root.setAnchorsAndOffsetsPreset(Control.LayoutPreset.PRESET_FULL_RECT)
        addChild(root)

        val rows = VBoxContainer().apply { mouseFilter = Control.MouseFilter.IGNORE }
        root.addChild(rows)
        rows.addChild(HBoxContainer().apply {
            mouseFilter = Control.MouseFilter.IGNORE
            addChild(panel(sessionLabel, playersLabel, logLabel))
            addChild(spacer())
            addChild(panel(statisticsLabel))
        })
        rows.addChild(spacer().apply { sizeFlagsVertical = Control.SizeFlags.EXPAND_FILL })
        val hint = Label().apply { text = "WASD move    click or space shoot    E push a crate or open a door    Esc leave"; modulate = SampleTheme.muted }
        rows.addChild(HBoxContainer().apply {
            mouseFilter = Control.MouseFilter.IGNORE
            addChild(panel(hint))
            addChild(spacer())
            addChild(panel(HBoxContainer().apply { addChild(switchLevelButton); addChild(leaveButton) }))
        })

        switchLevelButton.pressed.connect(lambdaCallable0<Unit> { switchLevel() })
        leaveButton.pressed.connect(lambdaCallable0<Unit> { launch { Network.leave() } })
        launch { GameLog.lines.collect { lines -> logLabel.text = lines.joinToString("\n") } }
    }

    override fun _process(delta: Double) {
        if (visible) render()
    }

    private fun render() {
        val lobby = Network.lobby.value
        val statistics = Network.statistics.value
        val level = Network.level.value.scenePath?.substringAfterLast('/')?.removeSuffix(".tscn") ?: "no level"
        sessionLabel.text = "${lobby.configuration.name}, playing $level\nYou are ${Network.localPlayerId.value}, master is ${Network.master.value}"
        playersLabel.text = lobby.players.joinToString("\n") { player ->
            val health = Player.byOwner[player.id.value]?.health?.let { " health $it" } ?: ""
            val roundTrip = statistics.roundTripMilliseconds[player.id]?.let { "  ${it.toInt()} ms" } ?: ""
            "${player.id.value}  ${player.profile.name}$health$roundTrip"
        }
        statisticsLabel.text = "out ${statistics.packetsOut} packets, ${statistics.bytesOut / 1024} KB per second\n" +
            "in ${statistics.packetsIn} packets, ${statistics.bytesIn / 1024} KB per second\n" +
            "${statistics.activeReplicas} networked nodes, tick ${statistics.ticks}, library ${"%.1f".format(statistics.processingMilliseconds)} ms per second"
        switchLevelButton.visible = Network.isMaster
    }

    private fun switchLevel() {
        val next = if (Network.level.value.scenePath == ARENA) HANGAR else ARENA
        GameLog.note("Loading ${next.substringAfterLast('/')}")
        Network.loadLevel(next, LevelPolicy.WaitForAll())
    }

    private fun panel(vararg children: Control): PanelContainer = PanelContainer().apply {
        val column = VBoxContainer()
        for (child in children) column.addChild(child)
        addChild(column)
        sizeFlagsVertical = Control.SizeFlags.SHRINK_BEGIN
    }

    private fun spacer(): Control = Control().apply {
        mouseFilter = Control.MouseFilter.IGNORE
        sizeFlagsHorizontal = Control.SizeFlags.EXPAND_FILL
    }

    companion object {
        const val ARENA = "res://scenes/arena.tscn"
        const val HANGAR = "res://scenes/hangar.tscn"
    }
}
