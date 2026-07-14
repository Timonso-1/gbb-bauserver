package de.timonso.gbbBauserver.command

import de.timonso.gbbBauserver.warp.Warp
import de.timonso.gbbBauserver.warp.WarpManager
import dev.jorel.commandapi.arguments.Argument
import dev.jorel.commandapi.arguments.ArgumentSuggestions
import dev.jorel.commandapi.kotlindsl.commandTree
import dev.jorel.commandapi.kotlindsl.greedyStringArgument
import dev.jorel.commandapi.kotlindsl.literalArgument
import dev.jorel.commandapi.kotlindsl.playerExecutor
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player

fun warpCommand(warpManager: WarpManager) = commandTree("warp") {
    withPermission("gbbbauserver.command.warp")

    literalArgument("create") {
        greedyStringArgument("name") {
            playerExecutor { player, arguments ->
                val name = arguments["name"] as String

                if (warpManager.getWarpByName(name) != null) {
                    player.sendMessage(Component.text("Ein Warp mit dem Namen \"$name\" existiert bereits.", NamedTextColor.RED))
                    return@playerExecutor
                }

                val warp = warpManager.createWarp(name, player)
                player.sendMessage(Component.text("Warp \"${warp.name}\" wurde erstellt.", NamedTextColor.GREEN))
            }
        }
    }

    literalArgument("teleport") {
        warpNameArgument(warpManager) {
            withWarp(warpManager) { player, warp ->
                val world = Bukkit.getWorld(warp.worldId)
                if (world == null) {
                    player.sendMessage(Component.text("Die Welt des Warps \"${warp.name}\" ist nicht geladen.", NamedTextColor.RED))
                    return@withWarp
                }

                player.teleportAsync(Location(world, warp.x, warp.y, warp.z, warp.yaw, warp.pitch))
                player.sendMessage(Component.text("Du wurdest zum Warp \"${warp.name}\" teleportiert.", NamedTextColor.GREEN))
            }
        }
    }

    literalArgument("delete") {
        warpNameArgument(warpManager) {
            withWarp(warpManager) { player, warp ->
                warpManager.deleteWarp(warp)
                player.sendMessage(Component.text("Warp \"${warp.name}\" wurde gelöscht.", NamedTextColor.GREEN))
            }
        }
    }

    literalArgument("info") {
        warpNameArgument(warpManager) {
            withWarp(warpManager) { player, warp ->
                val worldName = Bukkit.getWorld(warp.worldId)?.name ?: warp.worldId.toString()
                val creatorName = Bukkit.getOfflinePlayer(warp.creatorId).name ?: warp.creatorId.toString()

                player.sendMessage(
                    Component.text()
                        .append(Component.text("--- Warp \"${warp.name}\" ---", NamedTextColor.GOLD))
                        .appendNewline()
                        .append(infoLine("ID", warp.id.toString()))
                        .appendNewline()
                        .append(infoLine("Welt", worldName))
                        .appendNewline()
                        .append(infoLine("Position", "%.1f, %.1f, %.1f".format(warp.x, warp.y, warp.z)))
                        .appendNewline()
                        .append(infoLine("Blickrichtung", "Yaw %.1f, Pitch %.1f".format(warp.yaw, warp.pitch)))
                        .appendNewline()
                        .append(infoLine("Erstellt von", creatorName))
                        .build()
                )
            }
        }
    }
}

private fun infoLine(label: String, value: String): Component =
    Component.text("$label: ", NamedTextColor.GRAY).append(Component.text(value, NamedTextColor.YELLOW))

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
            player.sendMessage(Component.text("Es gibt keinen Warp mit dem Namen \"$name\".", NamedTextColor.RED))
            return@playerExecutor
        }
        handler(player, warp)
    }
