package dev.necr0manthre.innotournament.presentation.map

import dev.necr0manthre.innotournament.generators.GeneratorKey
import dev.necr0manthre.innotournament.generators.GeneratorPosition
import dev.necr0manthre.innotournament.generators.GeneratorType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class GeneratorMapProjectionTest {
    private val world = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val otherWorld = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @Test
    fun `border side selects nearest covering vanilla scale and caps at 2048`() {
        assertEquals(0, mapScaleForBorderSide(128.0))
        assertEquals(1, mapScaleForBorderSide(128.1))
        assertEquals(1, mapScaleForBorderSide(250.0))
        assertEquals(4, mapScaleForBorderSide(2000.0))
        assertEquals(4, mapScaleForBorderSide(10000.0))
        assertThrows(IllegalArgumentException::class.java) { mapScaleForBorderSide(Double.NaN) }
    }

    @Test
    fun `cursor projection matches map scale around shifted negative centers`() {
        val area = MapAreaKey(world, 27, -27, 4)
        assertEquals(0 to -2, GeneratorMapProjection.cursor(position(16, -17), area))
        assertEquals(2 to 0, GeneratorMapProjection.cursor(position(32, -1), area))
        assertEquals(-128 to -128, GeneratorMapProjection.cursor(position(-1024, -1024), MapAreaKey(world, 0, 0, 4)))
        assertEquals(126 to 126, GeneratorMapProjection.cursor(position(1008, 1008), MapAreaKey(world, 0, 0, 4)))
        assertNull(GeneratorMapProjection.cursor(position(1024, 0), MapAreaKey(world, 0, 0, 4)))
        assertNull(GeneratorMapProjection.cursor(position(16, -17, otherWorld), area))
    }

    @Test
    fun `Nether cursor follows vanilla float stages and clamps at both edges`() {
        assertEquals(-128, GeneratorMapProjection.netherCursor(-63.0, 0, 0))
        assertEquals(127, GeneratorMapProjection.netherCursor(63.0, 0, 0))
        assertEquals(-124, GeneratorMapProjection.netherCursor(-125.0, 0, 1))
        assertEquals(125, GeneratorMapProjection.netherCursor(125.0, 0, 1))
        assertEquals(0, GeneratorMapProjection.netherCursor(0.0, 0, 4))
        assertEquals(1, GeneratorMapProjection.netherCursor(0.25, 0, 0))
        assertEquals(-1, GeneratorMapProjection.netherCursor(-0.75, 0, 0))
        assertEquals(1, GeneratorMapProjection.netherCursor(0.5, 0, 1))
    }

    @Test
    fun `icon layer preserves each type and excludes other worlds`() {
        val area = MapAreaKey(world, 0, 0, 4)
        val keys = listOf(
            key(1, 1, GeneratorType.ITEM_GENERATOR),
            key(2, 3, GeneratorType.ITEM_GENERATOR),
            key(4, 5, GeneratorType.NETHER_GENERATOR),
            key(20, 20, GeneratorType.BOOK_GENERATOR),
            key(16, 0, GeneratorType.ITEM_GENERATOR),
            key(17, 2, GeneratorType.ITEM_GENERATOR),
            key(40, 40, GeneratorType.NETHER_GENERATOR),
            key(5, 6, GeneratorType.NETHER_GENERATOR, otherWorld)
        )

        assertEquals(
            listOf(
                GeneratorMapIcon(2, 0, GeneratorType.ITEM_GENERATOR),
                GeneratorMapIcon(0, 0, GeneratorType.ITEM_GENERATOR),
                GeneratorMapIcon(0, 0, GeneratorType.NETHER_GENERATOR),
                GeneratorMapIcon(2, 2, GeneratorType.BOOK_GENERATOR),
                GeneratorMapIcon(4, 4, GeneratorType.NETHER_GENERATOR)
            ),
            GeneratorMapProjection.icons(keys, area)
        )
    }

    private fun position(x: Int, z: Int, worldId: UUID = world) = GeneratorPosition(worldId, x, 64, z)

    private fun key(x: Int, z: Int, type: GeneratorType, worldId: UUID = world) =
        GeneratorKey(position(x, z, worldId), type)
}
