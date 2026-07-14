package de.timonso.gbbBauserver.measure

import de.timonso.gbbBauserver.util.Messages
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

private const val PARTICLE_INTERVAL_TICKS = 10L
private const val PARTICLE_STEP = 0.5
private const val MAX_ANALYZE_VOLUME = 10_000_000_000_000L

class MeasureManager(private val plugin: JavaPlugin) {

    private val toolKey = NamespacedKey(plugin, "measure_tool")
    private val selections = mutableMapOf<UUID, Selection>()
    private val particleTasks = mutableMapOf<UUID, BukkitTask>()
    private val dustOptions = Particle.DustOptions(Color.fromRGB(0xF2A93B), 1.0f)

    private data class Selection(val worldId: UUID, val pointA: Vector?, val pointB: Vector?)

    fun createTool(): ItemStack {
        val item = ItemStack(Material.STICK)
        item.editMeta { meta ->
            meta.displayName(
                Component.text("Maßband", Messages.PRIMARY, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false)
            )
            meta.lore(listOf(
                loreLine("Linksklick auf Block: Punkt A setzen"),
                loreLine("Rechtsklick auf Block: Punkt B setzen"),
                loreLine("Schleichen + Klick in die Luft: zurücksetzen")
            ))
            meta.setEnchantmentGlintOverride(true)
            meta.persistentDataContainer.set(toolKey, PersistentDataType.BYTE, 1)
        }
        return item
    }

    private fun loreLine(text: String): Component =
        Component.text(text, Messages.TEXT).decoration(TextDecoration.ITALIC, false)

    fun isTool(item: ItemStack?): Boolean =
        item != null && item.hasItemMeta() &&
            item.itemMeta.persistentDataContainer.has(toolKey, PersistentDataType.BYTE)

    fun setPoint(player: Player, block: Block, first: Boolean) {
        val current = selections[player.uniqueId]?.takeIf { it.worldId == block.world.uid }
        val point = Vector(block.x, block.y, block.z)
        val updated =
            if (first) Selection(block.world.uid, point, current?.pointB)
            else Selection(block.world.uid, current?.pointA, point)
        selections[player.uniqueId] = updated

        val label = if (first) "A" else "B"
        player.sendMessage(Messages.message(
            Component.text("Punkt $label gesetzt: ")
                .append(Messages.highlight("${block.x}, ${block.y}, ${block.z}"))
        ))

        if (updated.pointA != null && updated.pointB != null) {
            sendReport(player, updated.pointA, updated.pointB)
        }
        startParticles(player, updated)
    }

    fun clearSelection(player: Player) {
        if (selections.remove(player.uniqueId) == null) return
        particleTasks.remove(player.uniqueId)?.cancel()
        player.sendMessage(Messages.message(Component.text("Maßband-Auswahl zurückgesetzt.")))
    }

    fun clearPlayer(player: Player) {
        selections.remove(player.uniqueId)
        particleTasks.remove(player.uniqueId)?.cancel()
    }

    private fun sendReport(player: Player, a: Vector, b: Vector) {
        val sizeX = abs(a.blockX - b.blockX) + 1
        val sizeY = abs(a.blockY - b.blockY) + 1
        val sizeZ = abs(a.blockZ - b.blockZ) + 1
        val volume = sizeX.toLong() * sizeY * sizeZ
        val diagonal = sqrt((sizeX.toDouble() * sizeX + sizeY.toDouble() * sizeY + sizeZ.toDouble() * sizeZ))

        player.sendMessage(
            Component.text()
                .append(Messages.header("Maßband"))
                .appendNewline()
                .append(Messages.line("Punkt A", "${a.blockX}, ${a.blockY}, ${a.blockZ}"))
                .appendNewline()
                .append(Messages.line("Punkt B", "${b.blockX}, ${b.blockY}, ${b.blockZ}"))
                .appendNewline()
                .append(Messages.line("Maße (X × Y × Z)", "$sizeX × $sizeY × $sizeZ Blöcke"))
                .appendNewline()
                .append(Messages.line("Volumen", "$volume Blöcke"))
                .appendNewline()
                .append(Messages.line("Diagonale", "%.1f Blöcke".format(diagonal)))
                .appendNewline()
                .append(
                    Component.text(" [Materialien]", NamedTextColor.GREEN)
                        .hoverEvent(HoverEvent.showText(Component.text("Zeigt alle Materialien im ausgewählten Bereich", Messages.TEXT)))
                        .clickEvent(ClickEvent.runCommand("/measure materials"))
                )
                .build()
        )
    }

    fun analyzeSelection(player: Player) {
        val selection = selections[player.uniqueId]
        val a = selection?.pointA
        val b = selection?.pointB
        if (selection == null || a == null || b == null) {
            player.sendMessage(Messages.error(Component.text("Setze zuerst Punkt A und Punkt B mit dem Maßband.")))
            return
        }

        val world = Bukkit.getWorld(selection.worldId)
        if (world == null) {
            player.sendMessage(Messages.error(Component.text("Die Welt der Auswahl ist nicht geladen.")))
            return
        }

        val sizeX = abs(a.blockX - b.blockX) + 1
        val sizeY = abs(a.blockY - b.blockY) + 1
        val sizeZ = abs(a.blockZ - b.blockZ) + 1
        val volume = sizeX.toLong() * sizeY * sizeZ
        if (volume > MAX_ANALYZE_VOLUME) {
            player.sendMessage(Messages.error(
                Component.text("Der Bereich ist zu groß (%,d Blöcke, Maximum %,d).".format(volume, MAX_ANALYZE_VOLUME))
            ))
            return
        }

        val counts = mutableMapOf<Material, Int>()
        for (x in minOf(a.blockX, b.blockX)..maxOf(a.blockX, b.blockX)) {
            for (y in minOf(a.blockY, b.blockY)..maxOf(a.blockY, b.blockY)) {
                for (z in minOf(a.blockZ, b.blockZ)..maxOf(a.blockZ, b.blockZ)) {
                    val type = world.getBlockAt(x, y, z).type
                    if (type.isAir) continue
                    counts.merge(type, 1, Int::plus)
                }
            }
        }

        if (counts.isEmpty()) {
            player.sendMessage(Messages.message(Component.text("Der Bereich enthält keine Blöcke.")))
            return
        }

        val sorted = counts.entries.sortedByDescending { it.value }
        val blockCount = counts.values.sumOf { it.toLong() }
        val message = Component.text()
            .append(Messages.header("Materialien (%,d von %,d Blöcken)".format(blockCount, volume)))
        for ((material, count) in sorted) {
            message.appendNewline().append(
                Component.text(" ● ", NamedTextColor.DARK_GRAY)
                    .append(Component.text("%,d× ".format(count), Messages.HIGHLIGHT))
                    .append(Component.translatable(material.translationKey(), Messages.TEXT))
            )
        }
        player.sendMessage(message.build())
    }

    private fun startParticles(player: Player, selection: Selection) {
        particleTasks.remove(player.uniqueId)?.cancel()
        val task = object : BukkitRunnable() {
            override fun run() {
                if (!player.isOnline) {
                    particleTasks.remove(player.uniqueId)
                    cancel()
                    return
                }
                val holdingTool = isTool(player.inventory.itemInMainHand) || isTool(player.inventory.itemInOffHand)
                if (!holdingTool || player.world.uid != selection.worldId) return

                drawSelection(player, selection)
            }
        }.runTaskTimer(plugin, 0L, PARTICLE_INTERVAL_TICKS)
        particleTasks[player.uniqueId] = task
    }

    private fun drawSelection(player: Player, selection: Selection) {
        val points = listOfNotNull(selection.pointA, selection.pointB)
        if (points.isEmpty()) return

        val minX = points.minOf { it.blockX }.toDouble()
        val minY = points.minOf { it.blockY }.toDouble()
        val minZ = points.minOf { it.blockZ }.toDouble()
        val maxX = points.maxOf { it.blockX } + 1.0
        val maxY = points.maxOf { it.blockY } + 1.0
        val maxZ = points.maxOf { it.blockZ } + 1.0

        val xs = doubleArrayOf(minX, maxX)
        val ys = doubleArrayOf(minY, maxY)
        val zs = doubleArrayOf(minZ, maxZ)

        for (y in ys) for (z in zs) {
            var x = minX
            while (x <= maxX) {
                spawnDust(player, x, y, z)
                x += PARTICLE_STEP
            }
        }
        for (x in xs) for (z in zs) {
            var y = minY
            while (y <= maxY) {
                spawnDust(player, x, y, z)
                y += PARTICLE_STEP
            }
        }
        for (x in xs) for (y in ys) {
            var z = minZ
            while (z <= maxZ) {
                spawnDust(player, x, y, z)
                z += PARTICLE_STEP
            }
        }
    }

    private fun spawnDust(player: Player, x: Double, y: Double, z: Double) {
        player.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, dustOptions)
    }
}
