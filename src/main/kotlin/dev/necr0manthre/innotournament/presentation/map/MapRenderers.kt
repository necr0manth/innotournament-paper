package dev.necr0manthre.innotournament.presentation.map

import org.bukkit.Location
import org.bukkit.map.MapCanvas
import org.bukkit.map.MapCursor
import org.bukkit.map.MapRenderer
import org.bukkit.map.MapView
import org.bukkit.entity.Player
import java.awt.Color
import java.util.WeakHashMap

/** Draws one ready marker snapshot without reading the world or loading chunks. */
class GeneratorMapMarkerRenderer : MapRenderer(true) {
    private var pixels: List<GeneratorMapPixel> = emptyList()
    private val renderedByCanvas = WeakHashMap<MapCanvas, Map<Int, GeneratorPointColor>>()

    /** Replaces the layer with a detached copy of the supplied ready snapshot. */
    fun update(pixels: Iterable<GeneratorMapPixel>) {
        val snapshot = pixels.toList()
        require(snapshot.all { it.x in 0..127 && it.z in 0..127 }) {
            "Generator map pixels must be within 0..127"
        }
        this.pixels = snapshot
    }

    override fun render(map: MapView, canvas: MapCanvas, player: Player) {
        val current = pixels.associate { pixel -> pixel.z * 128 + pixel.x to pixel.color }
        val previous = renderedByCanvas[canvas].orEmpty()

        (previous.keys - current.keys).forEach { index ->
            canvas.setPixelColor(index % 128, index / 128, null)
        }
        current.forEach { (index, color) ->
            if (previous[index] != color) {
                canvas.setPixelColor(index % 128, index / 128, color.mapColor())
            }
        }
        renderedByCanvas[canvas] = current
    }

    private fun GeneratorPointColor.mapColor(): Color = when (this) {
        GeneratorPointColor.YELLOW -> Color.YELLOW
        GeneratorPointColor.CYAN -> Color.CYAN
        GeneratorPointColor.RED -> Color.RED
        GeneratorPointColor.WHITE -> Color.WHITE
    }
}

/** A quiet opaque lower layer for Nether maps. */
class NetherMapBackgroundRenderer : MapRenderer() {
    private val renderedCanvases = WeakHashMap<MapCanvas, Boolean>()

    override fun render(map: MapView, canvas: MapCanvas, player: Player) {
        if (renderedCanvases.containsKey(canvas)) return
        val background = Color(0x404040)
        for (z in 0..127) for (x in 0..127) {
            canvas.setPixelColor(x, z, background)
        }
        renderedCanvases[canvas] = true
    }
}

/** Adds a per-viewer Nether position cursor while leaving other renderer cursors alone. */
class NetherPlayerCursorRenderer(private val area: MapAreaKey) : MapRenderer(true) {
    private val cursorsByCanvas = WeakHashMap<MapCanvas, MapCursor>()

    override fun render(map: MapView, canvas: MapCanvas, player: Player) {
        val cursors = canvas.cursors
        val previous = cursorsByCanvas[canvas]
        if (player.world.uid != area.worldId) {
            if (previous != null) {
                cursors.removeCursor(previous)
                cursorsByCanvas.remove(canvas)
            }
            return
        }

        val location: Location = player.location
        val x = GeneratorMapProjection.netherCursor(location.x, area.centerX, area.scale).toByte()
        val z = GeneratorMapProjection.netherCursor(location.z, area.centerZ, area.scale).toByte()
        if (previous == null) {
            cursorsByCanvas[canvas] = cursors.addCursor(MapCursor(x, z, 0, MapCursor.Type.PLAYER, true))
        } else {
            previous.x = x
            previous.y = z
            previous.isVisible = true
        }
    }
}
