package dev.necr0manthre.innotournament.presentation.map

import dev.necr0manthre.innotournament.generators.GeneratorKey
import dev.necr0manthre.innotournament.generators.GeneratorPosition
import dev.necr0manthre.innotournament.generators.GeneratorType
import java.util.UUID

/** Identity of one immutable 128×128 map area. */
data class MapAreaKey(val worldId: UUID, val centerX: Int, val centerZ: Int, val scale: Int) {
    init {
        require(scale in 0..MAX_SCALE) { "Map scale must be in 0..$MAX_SCALE" }
    }

    companion object {
        const val MAX_SCALE = 4
    }
}

/** Closest vanilla map scale that covers the border side, capped at 2048 blocks. */
fun mapScaleForBorderSide(sideLength: Double): Int {
    require(sideLength.isFinite() && sideLength >= 0.0) { "Border side must be finite and non-negative" }
    var scale = 0
    while (scale < MapAreaKey.MAX_SCALE && sideLength > (128 shl scale).toDouble()) scale++
    return scale
}

/** A native map icon positioned relative to the center of its map. */
data class GeneratorMapIcon(val x: Int, val z: Int, val type: GeneratorType)

/** Pure coordinate and icon calculations; it never reads a world or loads a chunk. */
object GeneratorMapProjection {
    private const val MAP_CENTER = 64

    /** Returns a map cursor coordinate, or null for another world or a point outside this map. */
    fun cursor(position: GeneratorPosition, area: MapAreaKey): Pair<Int, Int>? {
        if (position.worldId != area.worldId) return null
        val blockScale = 1 shl area.scale
        val x = Math.floorDiv(position.x, blockScale).toLong() - area.centerX / blockScale + MAP_CENTER
        val z = Math.floorDiv(position.z, blockScale).toLong() - area.centerZ / blockScale + MAP_CENTER
        return if (x in 0L..127L && z in 0L..127L) (x.toInt() - MAP_CENTER) * 2 to (z.toInt() - MAP_CENTER) * 2 else null
    }

    /** Vanilla's Nether map cursor conversion, including its float rounding stages. */
    fun netherCursor(position: Double, center: Int, scale: Int): Int {
        require(scale in 0..MapAreaKey.MAX_SCALE) { "Map scale must be in 0..${MapAreaKey.MAX_SCALE}" }
        val relative = (position - center).toFloat() / (1 shl scale)
        return when {
            relative <= -63f -> -128
            relative >= 63f -> 127
            else -> (relative * 2f + 0.5).toInt()
        }
    }

    /** Projects each placement to a centered native icon coordinate, retaining coincident types. */
    fun icons(keys: Iterable<GeneratorKey>, area: MapAreaKey): List<GeneratorMapIcon> =
        keys.sortedWith(compareBy<GeneratorKey>({ it.position.z }, { it.position.x }, { it.position.y }, { it.type.ordinal }))
            .mapNotNull { key ->
                val position = cursor(key.position, area) ?: return@mapNotNull null
                GeneratorMapIcon(position.first, position.second, key.type)
            }
            .distinct()
}
