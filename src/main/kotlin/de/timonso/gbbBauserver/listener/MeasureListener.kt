package de.timonso.gbbBauserver.listener

import de.timonso.gbbBauserver.measure.MeasureManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot

class MeasureListener(private val measureManager: MeasureManager) : Listener {

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        if (!measureManager.isTool(event.item)) return
        event.isCancelled = true

        val player = event.player
        when (event.action) {
            Action.LEFT_CLICK_BLOCK -> measureManager.setPoint(player, event.clickedBlock ?: return, first = true)
            Action.RIGHT_CLICK_BLOCK -> measureManager.setPoint(player, event.clickedBlock ?: return, first = false)
            Action.LEFT_CLICK_AIR, Action.RIGHT_CLICK_AIR ->
                if (player.isSneaking) measureManager.clearSelection(player)
            else -> {}
        }
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        if (measureManager.isTool(event.player.inventory.itemInMainHand)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        measureManager.clearPlayer(event.player)
    }
}
