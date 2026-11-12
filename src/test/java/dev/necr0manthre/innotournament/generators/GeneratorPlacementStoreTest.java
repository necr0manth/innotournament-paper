package dev.necr0manthre.innotournament.generators;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** In-memory Paper-interface doubles; full chunk persistence and server events still need live Paper. */
class GeneratorPlacementStoreTest {
    private static final NamespacedKey VERSION = owned("generators_version");
    private static final UUID WORLD_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID WORLD_B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final GeneratorPlacementStore store = new GeneratorPlacementStore();

    @Test
    void roundTripAcrossChunkBoundariesNegativeHeightsAndWorldUuids() {
        for (int x : new int[]{-17, -16, -1, 0, 15, 16}) {
            for (int y : new int[]{-64, -1, 0, 319}) {
                var a = new Fixture(WORLD_A, Math.floorDiv(x, 16), Math.floorDiv(x, 16));
                var b = new Fixture(WORLD_B, Math.floorDiv(x, 16), Math.floorDiv(x, 16));
                var first = key(WORLD_A, x, y, x, GeneratorType.ITEM_GENERATOR);
                var second = key(WORLD_B, x, y, x, GeneratorType.BOOK_GENERATOR);
                a.setBlock(x, y, x, Material.BARRIER);
                b.setBlock(x, y, x, Material.BARRIER);

                assertTrue(store.write(a.chunk, first));
                assertTrue(store.write(b.chunk, second));
                assertEquals(List.of(first), store.read(a.chunk));
                assertEquals(List.of(second), store.read(b.chunk));
                assertEquals(first, store.find(a.chunk, first.getPosition()));
                assertEquals(second, store.find(b.chunk, second.getPosition()));
                assertEquals(Integer.valueOf(1), a.data.get(VERSION, PersistentDataType.INTEGER));
                assertEquals(first.getType().getId(), a.data.get(placement(x, y, x), PersistentDataType.STRING));
                assertEquals(2, a.data.getKeys().size());
            }
        }
    }

    @Test
    void multiplePlacementsShareVersionAndRemovalPreservesForeignData() {
        var f = new Fixture(WORLD_A, -1, 0);
        var first = key(WORLD_A, -1, -3, 0, GeneratorType.ITEM_GENERATOR);
        var second = key(WORLD_A, -16, 12, 15, GeneratorType.NETHER_GENERATOR);
        var foreign = new NamespacedKey("anotherplugin", "generator_15_-3_0");
        var unrelated = owned("some_other_feature");
        f.data.set(foreign, PersistentDataType.STRING, "hello");
        f.data.set(unrelated, PersistentDataType.INTEGER, 7);
        f.setBlock(-1, -3, 0, Material.BARRIER);
        f.setBlock(-16, 12, 15, Material.BARRIER);

        assertTrue(store.write(f.chunk, first));
        assertTrue(store.write(f.chunk, second));
        assertFalse(store.write(f.chunk, first), "repeat discovery must not rewrite an existing placement");
        assertEquals(Set.of(first, second), Set.copyOf(store.read(f.chunk)));
        assertThrows(IllegalStateException.class, () -> store.write(f.chunk,
                key(WORLD_A, -1, -3, 0, GeneratorType.BOOK_GENERATOR)));
        assertThrows(IllegalStateException.class, () -> store.remove(f.chunk,
                key(WORLD_A, -1, -3, 0, GeneratorType.BOOK_GENERATOR)));

        assertTrue(store.remove(f.chunk, first));
        assertEquals(Integer.valueOf(1), f.data.get(VERSION, PersistentDataType.INTEGER));
        assertEquals(List.of(second), store.read(f.chunk));
        assertTrue(store.remove(f.chunk, second));
        assertFalse(store.remove(f.chunk, second));
        assertEquals(List.of(), store.read(f.chunk));
        assertFalse(f.data.has(VERSION), "last removal should clear only the now-unused shared header");
        assertEquals("hello", f.data.get(foreign, PersistentDataType.STRING));
        assertEquals(Integer.valueOf(7), f.data.get(unrelated, PersistentDataType.INTEGER));
        assertEquals(Set.of(foreign, unrelated), f.data.getKeys());
    }

    @Test
    void unmarkedBarrierAndForeignPrefixAreNotDiscovered() {
        var f = new Fixture(WORLD_A, 0, 0);
        var position = new GeneratorPosition(WORLD_A, 1, -3, 1);
        f.setBlock(1, -3, 1, Material.BARRIER);
        f.data.set(new NamespacedKey("other", "generator_1_-3_1"), PersistentDataType.STRING, "item_generator");
        f.data.set(owned("special_item_type"), PersistentDataType.STRING, "item_generator");
        assertNull(store.find(f.chunk, position));
        assertEquals(List.of(), store.read(f.chunk));
        assertEquals(0, f.blockReads, "unmarked block must not be scanned or inferred as a generator");
    }

    @Test
    void rejectsCorruptOwnedEntriesWithoutRewritingThem() {
        assertCorrupt(f -> f.data.set(VERSION, PersistentDataType.INTEGER, 2), "unsupported");
        assertCorrupt(f -> f.data.set(VERSION, PersistentDataType.STRING, "1"), "non-integer");
        assertCorrupt(f -> f.data.set(owned("generator_0_-1_0"), PersistentDataType.STRING, "item_generator"), "missing");
        assertCorrupt(f -> mark(f, "generator_0_-1_0", "unknown_generator"), "unknown");
        assertCorrupt(f -> mark(f, "generator_16_-1_0", "item_generator"), "coordinates");
        assertCorrupt(f -> mark(f, "generator_00_-1_0", "item_generator"), "coordinates");
        assertCorrupt(f -> mark(f, "generator_0_-65_0", "item_generator"), "height");
        assertCorrupt(f -> mark(f, "generator_0_320_0", "item_generator"), "height");
        assertCorrupt(f -> mark(f, "generator_0_9999999999999999999_0", "item_generator"), "coordinates");
        assertCorrupt(f -> mark(f, "generator_wrong", "item_generator"), "key");
        assertCorrupt(f -> {
            f.data.set(VERSION, PersistentDataType.INTEGER, 1);
            f.data.set(owned("generator_0_-1_0"), PersistentDataType.INTEGER, 5);
        }, "non-string");
        assertCorrupt(f -> mark(f, "generator_0_-1_0", "item_generator"), "expected BARRIER");
    }

    @Test
    void invalidPositionsCannotReadWriteOrRemoveFromWrongHolder() {
        var f = new Fixture(WORLD_A, -1, 0);
        for (var position : List.of(
                new GeneratorPosition(WORLD_B, -1, 4, 0),
                new GeneratorPosition(WORLD_A, 0, 4, 0),
                new GeneratorPosition(WORLD_A, -1, 4, -1),
                new GeneratorPosition(WORLD_A, -1, -65, 0),
                new GeneratorPosition(WORLD_A, -1, 320, 0))) {
            var key = new GeneratorKey(position, GeneratorType.ITEM_GENERATOR);
            assertIntegrity(f, () -> store.find(f.chunk, position));
            assertIntegrity(f, () -> store.write(f.chunk, key));
            assertIntegrity(f, () -> store.remove(f.chunk, key));
        }
        assertEquals(Set.of(), f.data.getKeys());
    }

    @Test
    void overflowedChunkPositionIsDiagnosedInsteadOfWrapping() {
        var f = new Fixture(WORLD_A, Integer.MAX_VALUE, 0);
        mark(f, "generator_15_1_0", "item_generator");
        var error = assertIntegrity(f, () -> store.read(f.chunk));
        assertTrue(error.getMessage().contains("overflow"), error.getMessage());
        assertTrue(f.data.has(owned("generator_15_1_0")));
    }

    @Test
    void corruptSiblingPreventsMutationsAndPreservesItsEvidence() {
        var f = new Fixture(WORLD_A, 0, 0);
        var existing = key(WORLD_A, 1, 9, 1, GeneratorType.BOOK_GENERATOR);
        var newKey = key(WORLD_A, 2, 9, 2, GeneratorType.ITEM_GENERATOR);
        f.setBlock(1, 9, 1, Material.BARRIER);
        f.setBlock(2, 9, 2, Material.BARRIER);
        assertTrue(store.write(f.chunk, existing));
        f.setBlock(1, 9, 1, Material.STONE); // unexpected modification by an outside actor
        assertIntegrity(f, () -> store.write(f.chunk, newKey));
        assertIntegrity(f, () -> store.remove(f.chunk, existing));
        assertTrue(f.data.has(placement(1, 9, 1)));
        assertFalse(f.data.has(placement(2, 9, 2)));
    }

    private void assertCorrupt(java.util.function.Consumer<Fixture> damage, String expectedFragment) {
        var f = new Fixture(WORLD_A, 0, 0);
        damage.accept(f);
        var before = new HashMap<>(f.values);
        var exception = assertIntegrity(f, () -> store.read(f.chunk));
        assertTrue(exception.getMessage().contains(expectedFragment), exception.getMessage());
        assertEquals(before, f.values, "integrity errors must not erase or repair corrupted PDC");
    }

    private static void mark(Fixture f, String entry, String type) {
        f.data.set(VERSION, PersistentDataType.INTEGER, 1);
        f.data.set(owned(entry), PersistentDataType.STRING, type);
    }

    private static IllegalStateException assertIntegrity(Fixture f, Runnable action) {
        var error = assertThrows(IllegalStateException.class, action::run);
        assertTrue(error.getMessage().contains("world=" + f.worldId), error.getMessage());
        assertTrue(error.getMessage().contains("chunk=(" + f.cx + "," + f.cz + ")"), error.getMessage());
        return error;
    }

    private static GeneratorKey key(UUID world, int x, int y, int z, GeneratorType type) {
        return new GeneratorKey(new GeneratorPosition(world, x, y, z), type);
    }

    private static NamespacedKey owned(String key) { return new NamespacedKey("innotournament", key); }
    private static NamespacedKey placement(int x, int y, int z) {
        return owned("generator_" + Math.floorMod(x, 16) + "_" + y + "_" + Math.floorMod(z, 16));
    }

    private record Tagged(PersistentDataType<?, ?> type, Object value) { }

    private static class Fixture {
        final UUID worldId;
        final int cx;
        final int cz;
        final Map<NamespacedKey, Tagged> values = new LinkedHashMap<>();
        final Map<String, Material> blocks = new HashMap<>();
        final PersistentDataContainer data;
        final Chunk chunk;
        int blockReads;

        Fixture(UUID worldId, int cx, int cz) {
            this.worldId = worldId;
            this.cx = cx;
            this.cz = cz;
            data = proxy(PersistentDataContainer.class, (method, args) -> switch (method) {
                case "getKeys" -> Set.copyOf(values.keySet());
                case "has" -> args.length == 1 ? values.containsKey(args[0]) :
                        values.containsKey(args[0]) && values.get(args[0]).type() == args[1];
                case "get" -> values.containsKey(args[0]) && values.get(args[0]).type() == args[1] ?
                        values.get(args[0]).value() : null;
                case "set" -> { values.put((NamespacedKey) args[0], new Tagged((PersistentDataType<?, ?>) args[1], args[2])); yield null; }
                case "remove" -> { values.remove(args[0]); yield null; }
                default -> throw new UnsupportedOperationException("Unexpected PDC method: " + method);
            });
            World world = proxy(World.class, (method, args) -> switch (method) {
                case "getUID" -> worldId;
                case "getName" -> "same-name-for-both-worlds";
                case "getMinHeight" -> -64;
                case "getMaxHeight" -> 320;
                default -> throw new UnsupportedOperationException("Unexpected World method: " + method);
            });
            chunk = proxy(Chunk.class, (method, args) -> switch (method) {
                case "getWorld" -> world;
                case "getX" -> cx;
                case "getZ" -> cz;
                case "getPersistentDataContainer" -> data;
                case "getBlock" -> {
                    blockReads++;
                    var material = blocks.getOrDefault(args[0] + ":" + args[1] + ":" + args[2], Material.AIR);
                    yield proxy(Block.class, (blockMethod, ignored) -> {
                        if (blockMethod.equals("getType")) return material;
                        throw new UnsupportedOperationException("Unexpected Block method: " + blockMethod);
                    });
                }
                default -> throw new UnsupportedOperationException("Unexpected Chunk method: " + method);
            });
        }

        void setBlock(int x, int y, int z, Material material) {
            assertEquals(cx, Math.floorDiv(x, 16));
            assertEquals(cz, Math.floorDiv(z, 16));
            blocks.put(Math.floorMod(x, 16) + ":" + y + ":" + Math.floorMod(z, 16), material);
        }
    }

    private interface MethodDispatcher { Object invoke(String method, Object[] args) throws Throwable; }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, MethodDispatcher dispatcher) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "Fake" + type.getSimpleName();
            default -> dispatcher.invoke(method.getName(), args == null ? new Object[0] : args);
        };
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
}
