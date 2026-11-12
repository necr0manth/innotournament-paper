package dev.necr0manthre.innotournament.generators;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ItemGeneratorOneSlotTimingTest {
    @Test
    void promotedIntermediateOneSlotWaitsForBothIntervalsBeforeNextPromotion() {
        // The middle level is reached by promotion, already full, and is NOT the final level.
        // Its own interval is 2+1 ticks; the next level's different interval is 4+1 ticks.
        var levels = List.of(
                level(1, Material.DIAMOND),
                level(2, Material.EMERALD),
                level(4, Material.GOLD_INGOT)
        );
        var profiles = new GeneratorProfiles(Arrays.stream(GeneratorType.values())
                .map(type -> new GeneratorProfile(type, type.getProfileFile(), levels)).toList());
        var system = new ItemGeneratorSystem(profiles, kotlin.random.RandomKt.Random(91));
        var key = new GeneratorKey(
                new GeneratorPosition(UUID.fromString("00000000-0000-0000-0000-000000000001"), 1, 64, 1),
                GeneratorType.ITEM_GENERATOR
        );

        oneSlot(system.register(key), 0, 0, false, null);
        assertTrue(system.tick().isEmpty());
        oneSlot(system.find(key.getPosition()), 0, 1, false, null);
        assertEquals(List.of(key), system.tick());
        oneSlot(system.find(key.getPosition()), 0, 0, true, Material.DIAMOND);

        // Initial promotion uses the middle level's interval (2+1).
        for (int timer = 1; timer <= 2; timer++) {
            assertTrue(system.tick().isEmpty());
            oneSlot(system.find(key.getPosition()), 0, timer, true, Material.DIAMOND);
        }
        assertEquals(List.of(key), system.tick());
        oneSlot(system.find(key.getPosition()), 1, 0, false, Material.EMERALD);

        // The first middle-level item fills its only slot, but waiting must stay false
        // until the middle level's own full generation interval has elapsed.
        for (int timer = 1; timer <= 2; timer++) {
            assertTrue(system.tick().isEmpty());
            oneSlot(system.find(key.getPosition()), 1, timer, false, Material.EMERALD);
        }
        assertTrue(system.tick().isEmpty());
        oneSlot(system.find(key.getPosition()), 1, 0, true, Material.EMERALD);

        // After waiting begins, the NEXT level's distinct interval (4+1) applies.
        for (int timer = 1; timer <= 4; timer++) {
            assertTrue(system.tick().isEmpty());
            oneSlot(system.find(key.getPosition()), 1, timer, true, Material.EMERALD);
        }
        assertEquals(List.of(key), system.tick());
        oneSlot(system.find(key.getPosition()), 2, 0, false, Material.GOLD_INGOT);
    }

    private static GeneratorLevel level(int generationTime, Material material) {
        return new GeneratorLevel(generationTime, 1, List.of(
                new GeneratorWeightedItem(material.name(), new Stack(material, 20), 1.0)
        ));
    }

    private static void oneSlot(GeneratorInstance state, int level, int timer, boolean waiting, Material item) {
        assertNotNull(state);
        assertEquals(level, state.getLevel(), "level");
        assertEquals(timer, state.getTimer(), "timer");
        assertEquals(waiting, state.getWaitingForNextLevel(), "waiting");
        var slots = state.getSlots();
        assertEquals(10, slots.size());
        if (item == null) {
            assertNull(slots.get(0));
        } else {
            assertNotNull(slots.get(0));
            assertEquals(item, slots.get(0).getType(), "occupied item");
            assertEquals(20, slots.get(0).getAmount(), "occupied stack amount");
        }
        for (int i = 1; i < slots.size(); i++) assertNull(slots.get(i), "slot " + i);
    }

    /** No Bukkit server/item factory: this double isolates the public domain state machine. */
    private static final class Stack extends ItemStack {
        private final Material material;
        private int amount;

        private Stack(Material material, int amount) {
            super();
            this.material = material;
            this.amount = amount;
        }

        @Override public Material getType() { return material; }
        @Override public int getAmount() { return amount; }
        @Override public void setAmount(int amount) { this.amount = amount; }
        @Override public int getMaxStackSize() { return 64; }
        @Override public Stack clone() { return new Stack(material, amount); }
    }
}
