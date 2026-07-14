package de.timonso.gbbBauserver

import de.timonso.gbbBauserver.command.warpCommand
import de.timonso.gbbBauserver.warp.WarpManager

class CommandManager(private val warpManager: WarpManager) {
    fun registerCommands() {

        warpCommand(warpManager)
    }
}