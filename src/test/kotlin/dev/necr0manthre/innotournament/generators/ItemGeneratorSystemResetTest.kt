package dev.necr0manthre.innotournament.generators

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class ItemGeneratorSystemResetTest {
    @Test
    fun resetOnlyMarksSelectedWorldsForResetOnTheirNextTick() {
        val profiles = GeneratorProfiles(GeneratorType.entries.map { type ->
            GeneratorProfile(
                type,
                "test",
                listOf(GeneratorLevel(10, 1, listOf(GeneratorWeightedItem("stone", Stack(), 1.0))))
            )
        })
        val system = ItemGeneratorSystem(profiles)
        val selectedWorld = UUID.randomUUID()
        val otherWorld = UUID.randomUUID()
        val selected = GeneratorKey(GeneratorPosition(selectedWorld, 0, 64, 0), GeneratorType.ITEM_GENERATOR)
        val other = GeneratorKey(GeneratorPosition(otherWorld, 0, 64, 0), GeneratorType.ITEM_GENERATOR)
        system.register(selected)
        system.register(other)

        system.tick()
        system.tick()
        system.resetForWorlds(setOf(selectedWorld))
        system.tick()

        assertEquals(1, system.find(selected.position)?.timer)
        assertEquals(3, system.find(other.position)?.timer)
    }

    private class Stack : ItemStack() {
        override fun getType() = Material.STONE
        override fun getAmount() = 1
        override fun getMaxStackSize() = 64
        override fun clone(): ItemStack = Stack()
    }
}
