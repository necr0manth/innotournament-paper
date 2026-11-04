package dev.necr0manthre.innotournament.tournament.environment

import org.bukkit.Location
import org.bukkit.World

/** A point in one concrete world. No mutable Location is retained or shared. */
data class WorldPoint(
    val world: World,
    val x: Double,
    val y: Double,
    val z: Double
) {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite()) { "World point coordinates must be finite" }
    }

    fun toLocation(): Location = Location(world, x, y, z)
}
