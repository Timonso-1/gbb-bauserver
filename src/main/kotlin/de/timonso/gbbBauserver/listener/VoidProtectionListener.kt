package de.timonso.gbbBauserver.listener

import de.timonso.gbbBauserver.util.Messages
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.block.BlockFace
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID

private const val SEARCH_RADIUS = 32

class VoidProtectionListener : Listener {

    private val rescueInProgress = mutableSetOf<UUID>()

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        if (event.to.y >= player.world.minHeight) return
        if (!rescueInProgress.add(player.uniqueId)) return

        val target = findNearestSafeLocation(event.to) ?: player.world.spawnLocation

        player.teleportAsync(target).whenComplete { _, _ ->
            rescueInProgress.remove(player.uniqueId)
            player.fallDistance = 0f
            player.sendMessage(Messages.message(
                Component.text("Du bist in die Leere gefallen und wurdest auf sicheren Boden zurückgeholt.")
            ))
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        rescueInProgress.remove(event.player.uniqueId)
    }

    private fun findNearestSafeLocation(from: Location): Location? {
        val world = from.world
        for (radius in 0..SEARCH_RADIUS) {
            for ((x, z) in ringColumns(from.blockX, from.blockZ, radius)) {
                if (!world.isChunkLoaded(x shr 4, z shr 4)) continue

                val block = world.getHighestBlockAt(x, z)
                if (!block.isSolid) continue

                val above = block.getRelative(BlockFace.UP)
                if (!above.isPassable || above.isLiquid) continue
                val above2 = above.getRelative(BlockFace.UP)
                if (!above2.isPassable || above2.isLiquid) continue

                return Location(world, x + 0.5, block.y + 1.0, z + 0.5, from.yaw, from.pitch)
            }
        }
        return null
    }

    private fun ringColumns(centerX: Int, centerZ: Int, radius: Int): Sequence<Pair<Int, Int>> = sequence {
        if (radius == 0) {
            yield(centerX to centerZ)
            return@sequence
        }
        for (dx in -radius..radius) {
            yield(centerX + dx to centerZ - radius)
            yield(centerX + dx to centerZ + radius)
        }
        for (dz in -radius + 1 until radius) {
            yield(centerX - radius to centerZ + dz)
            yield(centerX + radius to centerZ + dz)
        }
    }
}
