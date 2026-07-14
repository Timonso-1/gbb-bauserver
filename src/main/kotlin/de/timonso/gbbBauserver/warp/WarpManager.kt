package de.timonso.gbbBauserver.warp

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID

class WarpManager(private val plugin: JavaPlugin) {

    private val file = File(plugin.dataFolder, "warps.yml")
    private val config = YamlConfiguration.loadConfiguration(file)
    private val warps = mutableMapOf<UUID, Warp>()

    init {
        loadWarps()
    }

    private fun loadWarps() {
        val warpsSection = config.getConfigurationSection("warps") ?: return
        for (key in warpsSection.getKeys(false)) {
            val section = warpsSection.getConfigurationSection(key) ?: continue
            runCatching {
                val warp = Warp(
                    id = UUID.fromString(key),
                    name = section.getString("name")!!,
                    worldId = UUID.fromString(section.getString("world")!!),
                    x = section.getDouble("x"),
                    y = section.getDouble("y"),
                    z = section.getDouble("z"),
                    yaw = section.getDouble("yaw").toFloat(),
                    pitch = section.getDouble("pitch").toFloat(),
                    creatorId = UUID.fromString(section.getString("creator")!!),
                    createdAt = section.getLong("created")
                )
                warps[warp.id] = warp
            }.onFailure {
                plugin.logger.warning("Warp-Eintrag \"$key\" in warps.yml konnte nicht geladen werden und wird übersprungen.")
            }
        }
    }

    fun createWarp(name: String, player: Player): Warp {
        val location = player.location
        val warp = Warp(
            id = UUID.randomUUID(),
            name = name,
            worldId = player.world.uid,
            x = location.x,
            y = location.y,
            z = location.z,
            yaw = location.yaw,
            pitch = location.pitch,
            creatorId = player.uniqueId,
            createdAt = System.currentTimeMillis()
        )
        warps[warp.id] = warp

        val section = config.createSection("warps.${warp.id}")
        section.set("name", warp.name)
        section.set("world", warp.worldId.toString())
        section.set("x", warp.x)
        section.set("y", warp.y)
        section.set("z", warp.z)
        section.set("yaw", warp.yaw)
        section.set("pitch", warp.pitch)
        section.set("creator", warp.creatorId.toString())
        section.set("created", warp.createdAt)
        config.save(file)

        return warp
    }

    fun deleteWarp(warp: Warp) {
        warps.remove(warp.id)
        config.set("warps.${warp.id}", null)
        config.save(file)
    }

    fun getWarpByName(name: String): Warp? =
        warps.values.find { it.name.equals(name, ignoreCase = true) }

    fun getWarps(): List<Warp> = warps.values.toList()
}
