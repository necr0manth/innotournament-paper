package dev.necr0manthre.innotournament.presentation.map

import org.bukkit.entity.Player
import org.bukkit.map.MapCanvas
import org.bukkit.map.MapView
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.awt.Color
import java.lang.reflect.Proxy

class MapRenderersTest {
    @Test
    fun `marker renderer applies updated pixels and clears removed pixels transparently`() {
        val renderer = GeneratorMapMarkerRenderer()
        val writes = mutableListOf<PixelWrite>()
        val canvas = canvas(writes)
        val view = unused(MapView::class.java)
        val player = unused(Player::class.java)

        renderer.update(listOf(GeneratorMapPixel(3, 5, GeneratorPointColor.YELLOW)))
        renderer.render(view, canvas, player)
        renderer.update(listOf(GeneratorMapPixel(4, 5, GeneratorPointColor.CYAN)))
        renderer.render(view, canvas, player)

        assertEquals(
            listOf(
                PixelWrite(3, 5, Color.YELLOW),
                PixelWrite(3, 5, null),
                PixelWrite(4, 5, Color.CYAN)
            ),
            writes
        )
    }

    @Test
    fun `new viewer canvas receives the current marker snapshot`() {
        val renderer = GeneratorMapMarkerRenderer()
        renderer.update(listOf(GeneratorMapPixel(12, 13, GeneratorPointColor.RED)))
        val view = unused(MapView::class.java)
        val player = unused(Player::class.java)
        val writes = mutableListOf<PixelWrite>()

        renderer.render(view, canvas(mutableListOf()), player)
        renderer.render(view, canvas(writes), player)

        assertEquals(listOf(PixelWrite(12, 13, Color.RED)), writes)
    }

    private fun canvas(writes: MutableList<PixelWrite>): MapCanvas = proxy(MapCanvas::class.java) { method, args ->
        if (method == "setPixelColor") writes += PixelWrite(args[0] as Int, args[1] as Int, args[2] as Color?)
        null
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

    private data class PixelWrite(val x: Int, val y: Int, val color: Color?)
}
