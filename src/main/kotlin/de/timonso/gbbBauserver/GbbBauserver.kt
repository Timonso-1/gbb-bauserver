package de.timonso.gbbBauserver

import de.timonso.gbbBauserver.warp.WarpManager
import org.bukkit.plugin.java.JavaPlugin

class GbbBauserver : JavaPlugin() {

    lateinit var warpManager: WarpManager
        private set

    override fun onEnable() {
        warpManager = WarpManager(this)
        CommandManager(warpManager).registerCommands()

    }

    override fun onDisable() {

    }
}
