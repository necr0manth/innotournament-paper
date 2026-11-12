package dev.necr0manthre.innotournament.generators;

import org.bukkit.ExplosionResult;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GeneratorProtectionListenerTest {
    @Test
    void breakAndMobChangesRequireOwnedMarkerAndRespectCreative() {
        var f = new Fixture();
        f.mark(3, 10, 3);
        f.materials.put("4:10:3", Material.BARRIER); // vanilla barrier, not ours
        var protection = new GeneratorProtectionListener(new GeneratorPlacementStore());

        var survival = new BlockBreakEvent(f.block(3, 10, 3), f.player(GameMode.SURVIVAL));
        protection.onBreak(survival);
        assertTrue(survival.isCancelled());
        var creative = new BlockBreakEvent(f.block(3, 10, 3), f.player(GameMode.CREATIVE));
        protection.onBreak(creative);
        assertFalse(creative.isCancelled(), "lifecycle adapter owns permitted creative removal");
        var unmarked = new BlockBreakEvent(f.block(4, 10, 3), f.player(GameMode.SURVIVAL));
        protection.onBreak(unmarked);
        assertFalse(unmarked.isCancelled());

        var mob = new EntityChangeBlockEvent(f.entity(), f.block(3, 10, 3), f.blockData());
        protection.onEntityChangeBlock(mob);
        assertTrue(mob.isCancelled());
        var foreign = new EntityChangeBlockEvent(f.entity(), f.block(4, 10, 3), f.blockData());
        protection.onEntityChangeBlock(foreign);
        assertFalse(foreign.isCancelled());
    }

    @Test
    void bothExplosionsExcludeOnlyRegisteredPositionsWithoutCancellingExplosion() {
        var f = new Fixture();
        f.mark(3, 10, 3);
        f.materials.put("4:10:3", Material.BARRIER);
        var protection = new GeneratorProtectionListener(new GeneratorPlacementStore());
        var other = f.block(5, 10, 3);
        var bareBarrier = f.block(4, 10, 3);
        var registered = f.block(3, 10, 3);

        var entityBlocks = new ArrayList<>(List.of(other, bareBarrier, registered));
        var entityExplosion = new EntityExplodeEvent(f.entity(), new Location(f.world, 3, 10, 3),
                entityBlocks, 1.0f, ExplosionResult.DESTROY);
        protection.onEntityExplode(entityExplosion);
        assertFalse(entityExplosion.isCancelled());
        assertEquals(List.of(other, bareBarrier), entityBlocks);

        var blockBlocks = new ArrayList<>(List.of(registered, other, bareBarrier));
        var blockExplosion = new BlockExplodeEvent(f.block(8, 10, 3), f.state(),
                blockBlocks, 1.0f, ExplosionResult.DESTROY);
        protection.onBlockExplode(blockExplosion);
        assertFalse(blockExplosion.isCancelled());
        assertEquals(List.of(other, bareBarrier), blockBlocks);
    }

    @Test
    void pistonChecksMovingSourcesDestinationsAndHeadWithoutProtectingNeighbors() {
        var f = new Fixture();
        f.mark(3, 10, 3);
        var protection = new GeneratorProtectionListener(new GeneratorPlacementStore());
        var piston = f.block(1, 10, 3);
        var adjacent = f.block(2, 10, 3);
        var registered = f.block(3, 10, 3);

        var pushIntoRegistered = new BlockPistonExtendEvent(piston, List.of(adjacent), BlockFace.EAST);
        protection.onPistonExtend(pushIntoRegistered);
        assertTrue(pushIntoRegistered.isCancelled());
        var pushRegistered = new BlockPistonExtendEvent(piston, List.of(registered), BlockFace.EAST);
        protection.onPistonExtend(pushRegistered);
        assertTrue(pushRegistered.isCancelled());
        var unrelated = new BlockPistonExtendEvent(f.block(9, 10, 3), List.of(f.block(10, 10, 3)), BlockFace.EAST);
        protection.onPistonExtend(unrelated);
        assertFalse(unrelated.isCancelled());
        var headIntoRegistered = new BlockPistonExtendEvent(adjacent, List.of(), BlockFace.EAST);
        protection.onPistonExtend(headIntoRegistered);
        assertTrue(headIntoRegistered.isCancelled());

        var pullRegistered = new BlockPistonRetractEvent(piston, List.of(registered), BlockFace.EAST);
        protection.onPistonRetract(pullRegistered);
        assertTrue(pullRegistered.isCancelled());
        var pullIntoRegistered = new BlockPistonRetractEvent(f.block(2, 10, 3),
                List.of(f.block(4, 10, 3)), BlockFace.EAST);
        protection.onPistonRetract(pullIntoRegistered);
        assertTrue(pullIntoRegistered.isCancelled());
        var pullOther = new BlockPistonRetractEvent(f.block(10, 10, 3),
                List.of(f.block(9, 10, 3)), BlockFace.WEST);
        protection.onPistonRetract(pullOther);
        assertFalse(pullOther.isCancelled());
    }

    @Test
    void corruptOwnedMarkerThrowsWithLocationAndDoesNotRewriteIt() {
        var f = new Fixture();
        f.mark(3, 10, 3);
        f.materials.put("3:10:3", Material.STONE);
        var before = Map.copyOf(f.values);
        var protection = new GeneratorProtectionListener(new GeneratorPlacementStore());
        var error = assertThrows(IllegalStateException.class, () ->
                protection.onBreak(new BlockBreakEvent(f.block(3, 10, 3), f.player(GameMode.SURVIVAL))));
        assertTrue(error.getMessage().contains("world=" + f.worldId));
        assertTrue(error.getMessage().contains("block=(3,10,3)"));
        assertEquals(before, f.values);

        var creativeError = assertThrows(IllegalStateException.class, () ->
                protection.onBreak(new BlockBreakEvent(f.block(3, 10, 3), f.player(GameMode.CREATIVE))));
        assertTrue(creativeError.getMessage().contains("block=(3,10,3)"));
        assertEquals(before, f.values);
    }

    @Test
    void eventHandlersAreHighPriorityAndIgnoreCancelledWithoutMonitorMutations() throws Exception {
        for (var name : List.of("onBreak", "onEntityExplode", "onBlockExplode", "onPistonExtend",
                "onPistonRetract", "onEntityChangeBlock")) {
            var method = java.util.Arrays.stream(GeneratorProtectionListener.class.getDeclaredMethods())
                    .filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
            var annotation = method.getAnnotation(EventHandler.class);
            assertNotNull(annotation);
            assertEquals(EventPriority.HIGHEST, annotation.priority());
            assertTrue(annotation.ignoreCancelled());
        }
    }

    private record Entry(PersistentDataType<?, ?> type, Object value) { }
    private static class Fixture {
        final UUID worldId = UUID.randomUUID();
        final Map<NamespacedKey, Entry> values = new LinkedHashMap<>();
        final Map<String, Material> materials = new HashMap<>();
        final PersistentDataContainer pdc = proxy(PersistentDataContainer.class, (name, args) -> switch (name) {
            case "getKeys" -> Set.copyOf(values.keySet());
            case "has" -> args.length == 1 ? values.containsKey(args[0]) :
                    values.containsKey(args[0]) && values.get(args[0]).type() == args[1];
            case "get" -> values.containsKey(args[0]) && values.get(args[0]).type() == args[1] ?
                    values.get(args[0]).value() : null;
            case "set" -> { values.put((NamespacedKey) args[0], new Entry((PersistentDataType<?, ?>) args[1], args[2])); yield null; }
            case "remove" -> { values.remove(args[0]); yield null; }
            default -> throw new UnsupportedOperationException("PDC " + name);
        });
        final World world = proxy(World.class, (name, args) -> switch (name) {
            case "getUID" -> worldId;
            case "getMinHeight" -> -64;
            case "getMaxHeight" -> 320;
            case "isChunkLoaded" -> (int) args[0] == 0 && (int) args[1] == 0;
            default -> throw new UnsupportedOperationException("World " + name);
        });
        final Chunk chunk = proxy(Chunk.class, (name, args) -> switch (name) {
            case "getX", "getZ" -> 0;
            case "getWorld" -> world;
            case "getPersistentDataContainer" -> pdc;
            case "getBlock" -> block((int) args[0], (int) args[1], (int) args[2]);
            default -> throw new UnsupportedOperationException("Chunk " + name);
        });

        void mark(int x, int y, int z) {
            materials.put(x + ":" + y + ":" + z, Material.BARRIER);
            values.put(new NamespacedKey("innotournament", "generators_version"),
                    new Entry(PersistentDataType.INTEGER, 1));
            values.put(new NamespacedKey("innotournament", "generator_" + x + "_" + y + "_" + z),
                    new Entry(PersistentDataType.STRING, GeneratorType.ITEM_GENERATOR.getId()));
        }

        Block block(int x, int y, int z) {
            return proxy(Block.class, (name, args) -> switch (name) {
                case "getWorld" -> world;
                case "getChunk" -> chunk;
                case "getX" -> x;
                case "getY" -> y;
                case "getZ" -> z;
                case "getType" -> materials.getOrDefault(x + ":" + y + ":" + z, Material.STONE);
                case "getRelative" -> {
                    var face = (BlockFace) args[0];
                    int distance = args.length == 1 ? 1 : (int) args[1];
                    yield block(x + face.getModX() * distance, y + face.getModY() * distance,
                            z + face.getModZ() * distance);
                }
                default -> throw new UnsupportedOperationException("Block " + name);
            });
        }

        Player player(GameMode gameMode) { return proxy(Player.class, (name, args) -> switch (name) {
            case "getGameMode" -> gameMode;
            default -> throw new UnsupportedOperationException("Player " + name);
        }); }
        Entity entity() { return proxy(Entity.class, (name, args) -> { throw new UnsupportedOperationException("Entity " + name); }); }
        BlockState state() { return proxy(BlockState.class, (name, args) -> { throw new UnsupportedOperationException("State " + name); }); }
        BlockData blockData() { return proxy(BlockData.class, (name, args) -> { throw new UnsupportedOperationException("BlockData " + name); }); }
    }

    private interface MethodHandler { Object call(String name, Object[] args) throws Throwable; }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, MethodHandler handler) {
        InvocationHandler dispatch = (self, method, args) -> switch (method.getName()) {
            case "equals" -> self == args[0];
            case "hashCode" -> System.identityHashCode(self);
            case "toString" -> "Fake" + type.getSimpleName();
            default -> handler.call(method.getName(), args == null ? new Object[0] : args);
        };
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, dispatch);
    }
}
