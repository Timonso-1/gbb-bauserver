package de.timonso.gbbBauserver.warp

import java.util.UUID

data class Warp(
    val id: UUID,
    val name: String,
    val worldId: UUID,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
    val creatorId: UUID,
    val createdAt: Long
)
