package de.timonso.gbbBauserver

import de.timonso.gbbBauserver.command.measureCommand
import de.timonso.gbbBauserver.command.warpCommand
import de.timonso.gbbBauserver.measure.MeasureManager
import de.timonso.gbbBauserver.warp.WarpManager

class CommandManager(
    private val warpManager: WarpManager,
    private val measureManager: MeasureManager
) {
    fun registerCommands() {

        warpCommand(warpManager)
        measureCommand(measureManager)
    }
}