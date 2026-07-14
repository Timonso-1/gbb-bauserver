package de.timonso.gbbBauserver.command

import de.timonso.gbbBauserver.measure.MeasureManager
import de.timonso.gbbBauserver.util.Messages
import dev.jorel.commandapi.kotlindsl.commandTree
import dev.jorel.commandapi.kotlindsl.literalArgument
import dev.jorel.commandapi.kotlindsl.playerExecutor
import net.kyori.adventure.text.Component

fun measureCommand(measureManager: MeasureManager) = commandTree("measure") {
    withPermission("gbbbauserver.command.measure")
    withAliases("massband")

    literalArgument("materials") {
        playerExecutor { player, _ ->
            measureManager.analyzeSelection(player)
        }
    }

    playerExecutor { player, _ ->
        player.inventory.addItem(measureManager.createTool())
        player.sendMessage(Messages.message(
            Component.text("Du hast das ")
                .append(Messages.highlight("Maßband"))
                .append(Component.text(" erhalten."))
        ))
    }
}
