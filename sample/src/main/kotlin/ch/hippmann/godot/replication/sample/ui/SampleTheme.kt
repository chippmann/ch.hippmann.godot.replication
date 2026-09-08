package ch.hippmann.godot.replication.sample.ui

import godot.api.StyleBoxFlat
import godot.api.Theme
import godot.core.Color
import godot.core.asStringName

object SampleTheme {
    val background = Color(0.09, 0.1, 0.13)
    val panel = Color(0.15, 0.17, 0.21)
    val panelBorder = Color(0.28, 0.31, 0.38)
    val accent = Color(0.36, 0.62, 0.95)
    val accentPressed = Color(0.27, 0.48, 0.78)
    val field = Color(0.11, 0.12, 0.15)
    val muted = Color(0.62, 0.66, 0.72)
    val text = Color(0.94, 0.95, 0.97)
    val danger = Color(0.86, 0.34, 0.3)

    fun create(): Theme = Theme().apply {
        defaultFontSize = 16
        setStylebox("panel", "PanelContainer", box(panel, border = panelBorder, radius = 10, margin = 18))
        setStylebox("panel", "ItemList", box(field, border = panelBorder, radius = 6, margin = 6))
        setStylebox("normal", "LineEdit", box(field, border = panelBorder, radius = 6, margin = 8))
        setStylebox("focus", "LineEdit", box(field, border = accent, radius = 6, margin = 8))
        setStylebox("normal", "Button", box(Color(0.24, 0.27, 0.33), border = panelBorder, radius = 6, margin = 10))
        setStylebox("hover", "Button", box(Color(0.3, 0.34, 0.41), border = accent, radius = 6, margin = 10))
        setStylebox("pressed", "Button", box(accentPressed, border = accent, radius = 6, margin = 10))
        setStylebox("disabled", "Button", box(Color(0.17, 0.18, 0.22), border = Color(0.22, 0.24, 0.28), radius = 6, margin = 10))
        setStylebox("focus", "Button", box(Color(0.3, 0.34, 0.41), border = accent, radius = 6, margin = 10))
        setStylebox("normal", "OptionButton", box(Color(0.24, 0.27, 0.33), border = panelBorder, radius = 6, margin = 10))
        setStylebox("hover", "OptionButton", box(Color(0.3, 0.34, 0.41), border = accent, radius = 6, margin = 10))
        setStylebox("pressed", "OptionButton", box(accentPressed, border = accent, radius = 6, margin = 10))
        setStylebox("focus", "OptionButton", box(Color(0.3, 0.34, 0.41), border = accent, radius = 6, margin = 10))
        setColor("font_color", "Label", text)
        setColor("font_color", "Button", text)
        setColor("font_disabled_color", "Button", muted)
        setColor("font_color", "LineEdit", text)
        setColor("font_placeholder_color", "LineEdit", muted)
        setColor("font_color", "ItemList", text)
    }

    private fun Theme.setStylebox(name: String, type: String, box: StyleBoxFlat) = setStylebox(name.asStringName(), type.asStringName(), box)

    private fun Theme.setColor(name: String, type: String, color: Color) = setColor(name.asStringName(), type.asStringName(), color)

    private fun box(fill: Color, border: Color, radius: Int, margin: Int): StyleBoxFlat = StyleBoxFlat().apply {
        bgColor = fill
        borderColor = border
        setBorderWidthAll(1)
        setCornerRadiusAll(radius)
        setContentMarginAll(margin.toFloat())
    }
}
