package dev.necr0manthre.innotournament.generators

import dev.necr0manthre.innotournament.tournament.environment.PluginChunkTickets
import org.bukkit.Chunk
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/** Exercises the real lifecycle discovery body without installing a fake Bukkit server. */
class GeneratorPlacementLifecycleClosedWorldTest {
    @Test
    fun `closed runtime rejects empty loaded world before reading its chunks`() {
        val level = GeneratorLevel(1, 1, listOf(GeneratorWeightedItem("stone", Stack(), 1.0)))
        val profiles = GeneratorProfiles(GeneratorType.entries.map {
            GeneratorProfile(it, it.profileFile, listOf(level))
        })
        val items = GeneratorItems()
        val placements = GeneratorPlacementStore()
        val plugin = proxy(Plugin::class.java) { error("No plugin tickets expected") }
        val runtime = GeneratorRuntime(
            ItemGeneratorSystem(profiles), placements, GeneratorDisplays(items), PluginChunkTickets(plugin)
        )
        val lifecycle = GeneratorPlacementLifecycle(items, placements, runtime)
        var chunkReads = 0
        val emptyWorld = proxy(World::class.java) { method ->
            when (method) {
                "getLoadedChunks" -> {
                    chunkReads++
                    emptyArray<Chunk>()
                }
                else -> error("Unexpected World method: $method")
            }
        }

        // Main-thread validation is in the public wrapper; invoke its real discovery body here
        // because Bukkit.isPrimaryThread() requires an actual server not available in a unit test.
        val discover = GeneratorPlacementLifecycle::class.java.getDeclaredMethod(
            "discoverLoadedWorldOnPrimaryThread", World::class.java
        ).apply { isAccessible = true }
        assertEquals(emptyList<GeneratorKey>(), discover.invoke(lifecycle, emptyWorld))
        assertEquals(1, chunkReads)

        runtime.close() // Intentionally leave the independent lifecycle open.
        val failure = assertThrows(InvocationTargetException::class.java) {
            discover.invoke(lifecycle, emptyWorld)
        }
        assertSame(IllegalStateException::class.java, failure.cause?.javaClass)
        assertEquals("GeneratorRuntime is closed", failure.cause?.message)
        assertEquals(1, chunkReads, "closed runtime must fail before World.loadedChunks")
        // The runtime is closed while the lifecycle remains open, exactly as in R-L1.
    }

    private class Stack : ItemStack() {
        override fun getType() = Material.STONE
        override fun getAmount() = 1
        override fun getMaxStackSize() = 64
        override fun clone(): ItemStack = Stack()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> proxy(type: Class<T>, handler: (String) -> Any?): T =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
            handler(method.name)
        } as T
}
