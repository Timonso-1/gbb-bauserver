package de.timonso.gbbBauserver.command

import de.timonso.gbbBauserver.util.Messages
import de.timonso.gbbBauserver.warp.Warp
import de.timonso.gbbBauserver.warp.WarpManager
import dev.jorel.commandapi.arguments.Argument
import dev.jorel.commandapi.arguments.ArgumentSuggestions
import dev.jorel.commandapi.kotlindsl.commandTree
import dev.jorel.commandapi.kotlindsl.greedyStringArgument
import dev.jorel.commandapi.kotlindsl.literalArgument
import dev.jorel.commandapi.kotlindsl.playerExecutor
import dev.jorel.commandapi.kotlindsl.textArgument
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private const val MAX_NAME_LENGTH = 32
private val NAME_REGEX = Regex("[\\p{L}\\p{N} _-]+")
private const val DELETE_CONFIRM_TIMEOUT_MS = 30_000L

private val pendingDeletions = mutableMapOf<UUID, Pair<UUID, Long>>()
private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())

fun warpCommand(warpManager: WarpManager) = commandTree("warp") {
    withPermission("gbbbauserver.command.warp")

    literalArgument("create") {
        greedyStringArgument("name") {
            playerExecutor { player, arguments ->
                val name = (arguments["name"] as String).trim()

                val error = validateName(name, warpManager)
                if (error != null) {
                    player.sendMessage(Messages.error(error))
                    return@playerExecutor
                }

                val warp = warpManager.createWarp(name, player)
                player.sendMessage(Messages.message(
                    Component.text("Der Warp ")
                        .append(Messages.highlight(warp.name))
                        .append(Component.text(" wurde erstellt."))
                ))
            }
        }
    }

    literalArgument("teleport") {
        warpNameArgument(warpManager) {
            withWarp(warpManager) { player, warp ->
                val world = Bukkit.getWorld(warp.worldId)
                if (world == null) {
                    player.sendMessage(Messages.error(
                        Component.text("Die Welt des Warps ")
                            .append(Messages.highlight(warp.name))
                            .append(Component.text(" ist nicht geladen."))
                    ))
                    return@withWarp
                }

                player.teleportAsync(Location(world, warp.x, warp.y, warp.z, warp.yaw, warp.pitch)).thenAccept { success ->
                    if (!success) return@thenAccept
                    player.playSound(player.location, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f)
                    player.sendMessage(Messages.message(
                        Component.text("Du wurdest zum Warp ")
                            .append(Messages.highlight(warp.name))
                            .append(Component.text(" teleportiert."))
                    ))
                }
            }
        }
    }

    literalArgument("delete") {
        withPermission("gbbbauserver.command.warp.delete")
        warpNameArgument(warpManager) {
            withWarp(warpManager) { player, warp ->
                val pending = pendingDeletions[player.uniqueId]
                val confirmed = pending != null &&
                    pending.first == warp.id &&
                    System.currentTimeMillis() - pending.second <= DELETE_CONFIRM_TIMEOUT_MS

                if (!confirmed) {
                    pendingDeletions[player.uniqueId] = warp.id to System.currentTimeMillis()
                    player.sendMessage(Messages.message(
                        Component.text("Möchtest du den Warp ")
                            .append(Messages.highlight(warp.name))
                            .append(Component.text(" wirklich löschen? "))
                            .append(
                                Component.text("[Bestätigen]", NamedTextColor.RED)
                                    .hoverEvent(HoverEvent.showText(Component.text("Klicke, um den Warp endgültig zu löschen", Messages.TEXT)))
                                    .clickEvent(ClickEvent.runCommand("/warp delete ${warp.name}"))
                            )
                    ))
                    return@withWarp
                }

                pendingDeletions.remove(player.uniqueId)
                warpManager.deleteWarp(warp)
                player.sendMessage(Messages.message(
                    Component.text("Der Warp ")
                        .append(Messages.highlight(warp.name))
                        .append(Component.text(" wurde gelöscht."))
                ))
            }
        }
    }

    literalArgument("move") {
        warpNameArgument(warpManager) {
            withWarp(warpManager) { player, warp ->
                warpManager.moveWarp(warp, player)
                player.sendMessage(Messages.message(
                    Component.text("Der Warp ")
                        .append(Messages.highlight(warp.name))
                        .append(Component.text(" wurde zu deiner Position verschoben."))
                ))
            }
        }
    }

    literalArgument("rename") {
        textArgument("warp") {
            replaceSuggestions(ArgumentSuggestions.strings { _ ->
                warpManager.getWarps()
                    .map { if (it.name.contains(' ')) "\"${it.name}\"" else it.name }
                    .toTypedArray()
            })
            greedyStringArgument("name") {
                playerExecutor { player, arguments ->
                    val warpName = arguments["warp"] as String
                    val newName = (arguments["name"] as String).trim()

                    val warp = warpManager.getWarpByName(warpName)
                    if (warp == null) {
                        player.sendMessage(Messages.error(
                            Component.text("Es gibt keinen Warp mit dem Namen ")
                                .append(Messages.highlight(warpName))
                                .append(Component.text("."))
                        ))
                        return@playerExecutor
                    }

                    val error = validateName(newName, warpManager, exclude = warp)
                    if (error != null) {
                        player.sendMessage(Messages.error(error))
                        return@playerExecutor
                    }

                    val oldName = warp.name
                    warpManager.renameWarp(warp, newName)
                    player.sendMessage(Messages.message(
                        Component.text("Der Warp ")
                            .append(Messages.highlight(oldName))
                            .append(Component.text(" heißt jetzt "))
                            .append(Messages.highlight(newName))
                            .append(Component.text("."))
                    ))
                }
            }
        }
    }

    literalArgument("info") {
        warpNameArgument(warpManager) {
            withWarp(warpManager) { player, warp ->
                val worldName = Bukkit.getWorld(warp.worldId)?.name ?: warp.worldId.toString()
                val creatorName = Bukkit.getOfflinePlayer(warp.creatorId).name ?: warp.creatorId.toString()
                val createdAt = if (warp.createdAt > 0) dateFormat.format(Instant.ofEpochMilli(warp.createdAt)) else "Unbekannt"

                player.sendMessage(
                    Component.text()
                        .append(Messages.header("Warp: ${warp.name}"))
                        .appendNewline()
                        .append(Messages.line("ID", warp.id.toString()))
                        .appendNewline()
                        .append(Messages.line("Welt", worldName))
                        .appendNewline()
                        .append(Messages.line("Position", "%.1f, %.1f, %.1f".format(warp.x, warp.y, warp.z)))
                        .appendNewline()
                        .append(Messages.line("Blickrichtung", "Yaw %.1f, Pitch %.1f".format(warp.yaw, warp.pitch)))
                        .appendNewline()
                        .append(Messages.line("Erstellt von", creatorName))
                        .appendNewline()
                        .append(Messages.line("Erstellt am", createdAt))
                        .appendNewline()
                        .append(
                            Component.text(" [Teleportieren]", NamedTextColor.GREEN)
                                .hoverEvent(HoverEvent.showText(Component.text("Klicke, um dich zu diesem Warp zu teleportieren", Messages.TEXT)))
                                .clickEvent(ClickEvent.runCommand("/warp teleport ${warp.name}"))
                        )
                        .build()
                )
            }
        }
    }

    literalArgument("list") {
        playerExecutor { player, _ ->
            val warps = warpManager.getWarps().sortedBy { it.name.lowercase() }
            if (warps.isEmpty()) {
                player.sendMessage(Messages.message(Component.text("Es wurden noch keine Warps erstellt.")))
                return@playerExecutor
            }

            val message = Component.text().append(Messages.header("Warps (${warps.size})"))
            for (warp in warps) {
                val worldName = Bukkit.getWorld(warp.worldId)?.name ?: "unbekannte Welt"
                message.appendNewline().append(
                    Component.text(" ● ", NamedTextColor.DARK_GRAY).append(
                        Component.text(warp.name, Messages.HIGHLIGHT)
                            .hoverEvent(HoverEvent.showText(
                                Component.text("$worldName · %.0f, %.0f, %.0f".format(warp.x, warp.y, warp.z), Messages.TEXT)
                                    .appendNewline()
                                    .append(Component.text("Klicke zum Teleportieren", NamedTextColor.GREEN))
                            ))
                            .clickEvent(ClickEvent.runCommand("/warp teleport ${warp.name}"))
                    )
                )
            }
            player.sendMessage(message.build())
        }
    }
}

private fun validateName(name: String, warpManager: WarpManager, exclude: Warp? = null): Component? = when {
    name.isEmpty() ->
        Component.text("Der Warp-Name darf nicht leer sein.")
    name.length > MAX_NAME_LENGTH ->
        Component.text("Der Warp-Name darf höchstens $MAX_NAME_LENGTH Zeichen lang sein.")
    !NAME_REGEX.matches(name) ->
        Component.text("Der Warp-Name darf nur Buchstaben, Zahlen, Leerzeichen, \"-\" und \"_\" enthalten.")
    warpManager.getWarpByName(name)?.takeIf { it.id != exclude?.id } != null ->
        Component.text("Ein Warp mit dem Namen ")
            .append(Messages.highlight(name))
            .append(Component.text(" existiert bereits."))
    else -> null
}

private fun Argument<*>.warpNameArgument(
    warpManager: WarpManager,
    block: Argument<*>.() -> Unit
) = greedyStringArgument("name") {
    replaceSuggestions(ArgumentSuggestions.strings { _ ->
        warpManager.getWarps().map { it.name }.toTypedArray()
    })
    block()
}

private fun Argument<*>.withWarp(warpManager: WarpManager, handler: (Player, Warp) -> Unit) =
    playerExecutor { player, arguments ->
        val name = arguments["name"] as String
        val warp = warpManager.getWarpByName(name)
        if (warp == null) {
            player.sendMessage(Messages.error(
                Component.text("Es gibt keinen Warp mit dem Namen ")
                    .append(Messages.highlight(name))
                    .append(Component.text("."))
            ))
            return@playerExecutor
        }
        handler(player, warp)
    }
