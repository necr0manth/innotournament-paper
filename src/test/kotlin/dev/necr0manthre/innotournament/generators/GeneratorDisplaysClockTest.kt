package dev.necr0manthre.innotournament.generators

import org.bukkit.World
import org.bukkit.entity.ItemDisplay
import org.bukkit.util.Transformation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.UUID

class GeneratorDisplaysClockTest {
    @Test
    fun `advancing game time animates and refreshes an existing slot when slot count changes`() {
        var gameTime = 100L
        val world = proxy(World::class.java) { method, _ ->
            when (method) {
                "getGameTime" -> gameTime
                "getFullTime" -> 0L
                else -> null
            }
        }
        val transformations = mutableListOf<Transformation>()
        val first = proxy(ItemDisplay::class.java) { method, args ->
            if (method == "setTransformation") transformations += args!![0] as Transformation
            null
        }
        val second = proxy(ItemDisplay::class.java) { _, _ -> null }
        val position = GeneratorPosition(UUID.randomUUID(), 0, 64, 0)
        val key = GeneratorKey(position, GeneratorType.ITEM_GENERATOR)
        val shownType = Class.forName("dev.necr0manthre.innotournament.generators.GeneratorDisplays\$Shown")
        val shown = shownType.declaredConstructors.single().apply { isAccessible = true }
            .newInstance(key, world, first)
        shownType.getDeclaredField("slots").apply { isAccessible = true }
            .set(shown, arrayOf(first, null, null, null, null, null, null, null, null, null))

        val displays = GeneratorDisplays(GeneratorItems())
        val animate = GeneratorDisplays::class.java.getDeclaredMethod("animate", shownType)
            .apply { isAccessible = true }
        animate.invoke(displays, shown)
        val singleSlotTransform = transformations.last()
        gameTime++
        animate.invoke(displays, shown)
        assertNotEquals(singleSlotTransform.translation.y, transformations.last().translation.y)

        shownType.getDeclaredField("slots").apply { isAccessible = true }
            .get(shown).let { (it as Array<ItemDisplay?>)[1] = second }
        gameTime++
        animate.invoke(displays, shown)

        val updatedExistingSlotTransform = transformations.last()
        assertEquals(3, transformations.size)
        assertNotEquals(singleSlotTransform.scale.x, updatedExistingSlotTransform.scale.x)
        assertEquals(0.85f, updatedExistingSlotTransform.scale.x, 0.0001f)
    }

    private fun <T : Any> proxy(type: Class<T>, handler: (String, Array<out Any?>?) -> Any?): T =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
            handler(method.name, args)
        } as T
}
