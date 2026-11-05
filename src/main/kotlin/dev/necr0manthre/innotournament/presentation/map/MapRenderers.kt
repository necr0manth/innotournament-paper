package dev.necr0manthre.innotournament.presentation.map

import dev.necr0manthre.innotournament.generators.GeneratorType
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.map.MapCanvas
import org.bukkit.map.MapCursor
import org.bukkit.map.MapRenderer
import org.bukkit.map.MapView
import java.awt.Color
import java.util.WeakHashMap

/** Draws native type-specific generator icons without reading the world or loading chunks. */
class GeneratorMapIconRenderer : MapRenderer(true) {
    private var icons: List<GeneratorMapIcon> = emptyList()
    private val renderedByCanvas = WeakHashMap<MapCanvas, Map<GeneratorMapIcon, MapCursor>>()

    /** Replaces the icon layer with a detached copy of the supplied ready snapshot. */
    fun update(icons: Iterable<GeneratorMapIcon>) {
        val snapshot = icons.toList()
        require(snapshot.all { it.x in -128..127 && it.z in -128..127 }) {
            "Generator map cursors must be within -128..127"
        }
        this.icons = snapshot
    }

    override fun render(map: MapView, canvas: MapCanvas, player: Player) {
        val current = icons.associateBy { it }
        val previous = renderedByCanvas[canvas].orEmpty()
        val cursors = canvas.cursors

        (previous.keys - current.keys).forEach { icon ->
            cursors.removeCursor(previous.getValue(icon))
        }
        val rendered = current.mapValues { (icon, _) ->
            previous[icon] ?: cursors.addCursor(
                MapCursor(icon.x.toByte(), icon.z.toByte(), 0, icon.type.cursorType(), true)
            )
        }
        renderedByCanvas[canvas] = rendered
    }

    private fun GeneratorType.cursorType(): MapCursor.Type = when (this) {
        GeneratorType.ITEM_GENERATOR -> MapCursor.Type.BANNER_YELLOW
        GeneratorType.BOOK_GENERATOR -> MapCursor.Type.BANNER_CYAN
        GeneratorType.NETHER_GENERATOR -> MapCursor.Type.BANNER_RED
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
