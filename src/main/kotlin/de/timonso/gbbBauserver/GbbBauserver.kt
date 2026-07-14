package de.timonso.gbbBauserver

import de.timonso.gbbBauserver.listener.MeasureListener
import de.timonso.gbbBauserver.listener.VoidProtectionListener
import de.timonso.gbbBauserver.measure.MeasureManager
import de.timonso.gbbBauserver.warp.WarpManager
import org.bukkit.plugin.java.JavaPlugin

class GbbBauserver : JavaPlugin() {

    lateinit var warpManager: WarpManager
        private set
    lateinit var measureManager: MeasureManager
        private set

    override fun onEnable() {
        warpManager = WarpManager(this)
        measureManager = MeasureManager(this)
        CommandManager(warpManager, measureManager).registerCommands()
        server.pluginManager.registerEvents(VoidProtectionListener(), this)
        server.pluginManager.registerEvents(MeasureListener(measureManager), this)

    }

    override fun onDisable() {

    }
}
