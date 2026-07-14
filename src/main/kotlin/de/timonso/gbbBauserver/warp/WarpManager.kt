package de.timonso.gbbBauserver.warp

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID

class WarpManager(plugin: JavaPlugin) {

    private val file = File(plugin.dataFolder, "warps.yml")
    private val config = YamlConfiguration.loadConfiguration(file)

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
            creatorId = player.uniqueId
        )

        val section = config.createSection("warps.${warp.id}")
        section.set("name", warp.name)
        section.set("world", warp.worldId.toString())
        section.set("x", warp.x)
        section.set("y", warp.y)
        section.set("z", warp.z)
        section.set("yaw", warp.yaw)
        section.set("pitch", warp.pitch)
        section.set("creator", warp.creatorId.toString())
        config.save(file)

        return warp
    }

    fun deleteWarp(warp: Warp) {
        config.set("warps.${warp.id}", null)
        config.save(file)
    }

    fun getWarpByName(name: String): Warp? =
        getWarps().find { it.name.equals(name, ignoreCase = true) }

    fun getWarps(): List<Warp> {
        val warpsSection = config.getConfigurationSection("warps") ?: return emptyList()
        return warpsSection.getKeys(false).mapNotNull { key ->
            val section = warpsSection.getConfigurationSection(key) ?: return@mapNotNull null
            Warp(
                id = UUID.fromString(key),
                name = section.getString("name") ?: return@mapNotNull null,
                worldId = UUID.fromString(section.getString("world") ?: return@mapNotNull null),
                x = section.getDouble("x"),
                y = section.getDouble("y"),
                z = section.getDouble("z"),
                yaw = section.getDouble("yaw").toFloat(),
                pitch = section.getDouble("pitch").toFloat(),
                creatorId = UUID.fromString(section.getString("creator") ?: return@mapNotNull null)
            )
        }
    }
}
