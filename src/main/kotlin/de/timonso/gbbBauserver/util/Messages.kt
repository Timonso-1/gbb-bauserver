package de.timonso.gbbBauserver.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration

object Messages {

    val PRIMARY: TextColor = TextColor.color(0xF2A93B)
    val HIGHLIGHT: TextColor = TextColor.color(0x5FD7FF)
    val TEXT: TextColor = TextColor.color(0xBFBFBF)
    val ERROR: TextColor = TextColor.color(0xFF5555)
    val SUCCESS: TextColor = TextColor.color(0x66E07A)

    private val prefix: Component = Component.text()
        .append(Component.text("GBB", PRIMARY, TextDecoration.BOLD))
        .append(Component.text(" >> ", NamedTextColor.DARK_GRAY))
        .build()

    fun message(message: Component): Component =
        prefix.append(message.colorIfAbsent(TEXT))

    fun error(message: Component): Component =
        prefix.append(message.colorIfAbsent(ERROR))

    fun highlight(text: String): Component =
        Component.text(text, HIGHLIGHT)

    fun header(title: String): Component =
        Component.text()
            .append(Component.text("─── ", NamedTextColor.DARK_GRAY))
            .append(Component.text(title, PRIMARY, TextDecoration.BOLD))
            .append(Component.text(" ───", NamedTextColor.DARK_GRAY))
            .build()

    fun line(label: String, value: String): Component =
        Component.text()
            .append(Component.text(" ● ", NamedTextColor.DARK_GRAY))
            .append(Component.text("$label: ", TEXT))
            .append(Component.text(value, HIGHLIGHT))
            .build()
}
