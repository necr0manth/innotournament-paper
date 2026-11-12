package dev.necr0manthre.innotournament.presentation.map

import dev.necr0manthre.innotournament.generators.GeneratorType
import dev.necr0manthre.innotournament.commands.PaperCommandTestBootstrap
import org.bukkit.entity.Player
import org.bukkit.map.MapCanvas
import org.bukkit.map.MapCursor
import org.bukkit.map.MapCursorCollection
import org.bukkit.map.MapView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class MapRenderersTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrapRegistries() = PaperCommandTestBootstrap.ensure()
    }

    @Test
    fun `icon renderer adds native type icons and removes icons absent from an update`() {
        val renderer = GeneratorMapIconRenderer()
        val cursors = MapCursorCollection()
        cursors.addCursor(MapCursor(0, 0, 0, MapCursor.Type.PLAYER, true))
        val canvas = canvas(cursors)
        val view = unused(MapView::class.java)
        val player = unused(Player::class.java)

        renderer.update(listOf(
            GeneratorMapIcon(-128, 126, GeneratorType.ITEM_GENERATOR),
            GeneratorMapIcon(0, -2, GeneratorType.BOOK_GENERATOR),
            GeneratorMapIcon(2, 0, GeneratorType.NETHER_GENERATOR)
        ))
        renderer.render(view, canvas, player)
        assertEquals(
            listOf(
                CursorValue(0, 0, MapCursor.Type.PLAYER),
                CursorValue(-128, 126, MapCursor.Type.BANNER_YELLOW),
                CursorValue(0, -2, MapCursor.Type.BANNER_CYAN),
                CursorValue(2, 0, MapCursor.Type.BANNER_RED)
            ),
            cursors.values()
        )
        renderer.update(listOf(GeneratorMapIcon(0, -2, GeneratorType.BOOK_GENERATOR)))
        renderer.render(view, canvas, player)

        assertEquals(
            listOf(
                CursorValue(0, 0, MapCursor.Type.PLAYER),
                CursorValue(0, -2, MapCursor.Type.BANNER_CYAN)
            ),
            cursors.values()
        )
    }

    @Test
    fun `new viewer canvas receives the current icon snapshot`() {
        val renderer = GeneratorMapIconRenderer()
        renderer.update(listOf(GeneratorMapIcon(12, 13, GeneratorType.NETHER_GENERATOR)))
        val view = unused(MapView::class.java)
        val player = unused(Player::class.java)
        val cursors = MapCursorCollection()

        renderer.render(view, canvas(MapCursorCollection()), player)
        renderer.render(view, canvas(cursors), player)

        assertEquals(listOf(CursorValue(12, 13, MapCursor.Type.BANNER_RED)), cursors.values())
    }

    private fun canvas(cursors: MapCursorCollection): MapCanvas = proxy(MapCanvas::class.java) { method, _ ->
        if (method == "getCursors") cursors else null
    }

    private fun <T> unused(type: Class<T>): T = proxy(type) { _, _ -> null }

    @Suppress("UNCHECKED_CAST")
    private fun <T> proxy(
        type: Class<T>,
        handler: (String, Array<out Any?>) -> Any?
    ): T = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "toString" -> "${type.simpleName} test proxy"
            else -> handler(method.name, args ?: emptyArray())
        }
    } as T

    private fun MapCursorCollection.values(): List<CursorValue> =
        (0 until size()).map { index ->
            getCursor(index).let { CursorValue(it.x.toInt(), it.y.toInt(), it.type) }
        }

    private data class CursorValue(val x: Int, val y: Int, val type: MapCursor.Type)
}
