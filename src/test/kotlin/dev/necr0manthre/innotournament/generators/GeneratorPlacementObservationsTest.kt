package dev.necr0manthre.innotournament.generators

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class GeneratorPlacementObservationsTest {
    private val worldA = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val worldB = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @Test
    fun `subscription sees past discovery then only new committed changes`() {
        val observations = GeneratorPlacementObservations()
        val a = key(worldA, -17, -42, 3, GeneratorType.ITEM_GENERATOR)
        val b = key(worldB, -17, -42, 3, GeneratorType.NETHER_GENERATOR)
        observations.discovered(a) // discovery before MapPresentation joins

        val changes = mutableListOf<GeneratorPlacementChange>()
        val subscription = observations.subscribe { changes.add(it) }
        assertEquals(listOf(a), subscription.initial)
        assertThrows(UnsupportedOperationException::class.java) {
            (subscription.initial as MutableList<GeneratorKey>).clear()
        }
        observations.discovered(a) // repeat chunk/world load is not a new placement
        observations.discovered(b) // same coordinates, a different world UUID
        observations.discovered(b)
        assertEquals(listOf(GeneratorPlacementChange.Discovered(b)), changes)

        // Ordinary unload/deactivation has no method on this placement source: observation remains.
        val afterUnload = observations.subscribe { }
        assertEquals(listOf(a, b), afterUnload.initial)
        observations.discovered(a) // rediscovery after unload is a no-op
        assertEquals(listOf(GeneratorPlacementChange.Discovered(b)), changes)
        afterUnload.close()

        observations.removed(a)
        assertEquals(listOf(b), observations.subscribe { }.also { it.close() }.initial)
        assertEquals(listOf(GeneratorPlacementChange.Discovered(b), GeneratorPlacementChange.Removed(a)), changes)
        subscription.close()
        subscription.close()
        observations.removed(b)
        assertEquals(2, changes.size, "unsubscribed view must not receive updates")
        observations.close() // does not emit fake removals for ordinary shutdown
    }

    @Test
    fun `completed chunk stays observed after later corrupt chunk and same-runtime rediscovery`() {
        val observations = GeneratorPlacementObservations()
        val first = key(worldA, 1, 64, 1, GeneratorType.ITEM_GENERATOR)
        val second = key(worldA, 17, 64, 1, GeneratorType.BOOK_GENERATOR)
        val active = mutableSetOf<Int>()
        var secondChunkCorrupt = true
        val changes = mutableListOf<GeneratorPlacementChange>()

        fun discoverChunk(chunk: Int): List<GeneratorKey> {
            if (chunk == 1 && secondChunkCorrupt) error("Corrupt owned placement")
            return if (active.add(chunk)) listOf(if (chunk == 0) first else second) else emptyList()
        }

        observations.subscribe { changes.add(it) }.use {
            assertThrows(IllegalStateException::class.java) {
                observations.observeLoadedChunks(listOf(0, 1), ::discoverChunk)
            }
            assertEquals(setOf(0), active, "first chunk has already been activated")
            assertEquals(listOf(first), observations.subscribe { }.also { it.close() }.initial)
            assertEquals(listOf(GeneratorPlacementChange.Discovered(first)), changes)

            secondChunkCorrupt = false
            observations.observeLoadedChunks(listOf(0, 1), ::discoverChunk)
            assertEquals(setOf(0, 1), active)
            assertEquals(listOf(first, second), observations.subscribe { }.also { it.close() }.initial)
            assertEquals(
                listOf(GeneratorPlacementChange.Discovered(first), GeneratorPlacementChange.Discovered(second)),
                changes
            )
            observations.observeLoadedChunks(listOf(0, 1), ::discoverChunk)
            assertEquals(2, changes.size, "no duplicate observations after rediscovery")
        }
    }

    @Test
    fun `owner remove then place at same position changes type without duplicate`() {
        val observations = GeneratorPlacementObservations()
        val old = key(worldA, 5, 64, -18, GeneratorType.ITEM_GENERATOR)
        val replacement = old.copy(type = GeneratorType.BOOK_GENERATOR)
        val changes = mutableListOf<GeneratorPlacementChange>()
        observations.subscribe { changes.add(it) }.use {
            observations.discovered(old)
            assertThrows(IllegalStateException::class.java) {
                observations.discovered(replacement) // PDC/store disallows silent competing types
            }
            assertEquals(listOf(old), observations.subscribe { }.also { it.close() }.initial)
            observations.removed(old) // only the adapter's completed physical removal calls this
            observations.discovered(replacement)
            assertEquals(listOf(replacement), observations.subscribe { }.also { it.close() }.initial)
            assertEquals(
                listOf(
                    GeneratorPlacementChange.Discovered(old),
                    GeneratorPlacementChange.Removed(old),
                    GeneratorPlacementChange.Discovered(replacement)
                ), changes
            )
        }
    }

    @Test
    fun `closed subscriptions stop receiving even during delivery and shutdown sends no removal`() {
        val observations = GeneratorPlacementObservations()
        val first = key(worldA, 1, 70, 1, GeneratorType.ITEM_GENERATOR)
        val second = key(worldA, 2, 70, 2, GeneratorType.BOOK_GENERATOR)
        var calledAfterClose = false
        lateinit var later: GeneratorPlacementSubscription
        val earlier = observations.subscribe { later.close() }
        later = observations.subscribe { calledAfterClose = true }
        observations.discovered(first)
        assertFalse(calledAfterClose)
        observations.discovered(second)
        assertFalse(calledAfterClose)
        earlier.close()
        observations.close()
        assertThrows(IllegalStateException::class.java) { observations.subscribe { } }
        assertThrows(IllegalStateException::class.java) { observations.discovered(first) }
    }

    private fun key(world: UUID, x: Int, y: Int, z: Int, type: GeneratorType) =
        GeneratorKey(GeneratorPosition(world, x, y, z), type)
}
