package dev.necr0manthre.innotournament.tournament.environment

import org.bukkit.Bukkit
import org.bukkit.GameRules
import org.bukkit.NamespacedKey
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.WorldBorder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.bukkit.plugin.Plugin
import org.bukkit.util.Vector
import org.bukkit.structure.Structure
import org.bukkit.structure.StructureManager
import java.lang.reflect.Proxy
import java.util.UUID

class TournamentEnvironmentTest {
    @Test
    fun constructionIsPassiveAndPointsMustBelongToTheBoundWorlds() {
        val normal = world("overworld")
        val nether = world("the_nether")
        val end = world("the_end")
        val other = world("another", "custom")
        val bound = bindings(normal, normal, normal, nether, end)
        val prePoint = point(normal)
        val gamePoint = point(normal)

        TournamentEnvironment(bound, prePoint, gamePoint, tickets(), testPlugin(), structureManager(), null, prePoint)
        assertUnchanged(normal, nether, end)
        assertThrows(IllegalArgumentException::class.java) {
            TournamentEnvironment(bound, point(other), gamePoint, tickets(), testPlugin(), structureManager(), null, point(other))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TournamentEnvironment(bound, prePoint, point(other), tickets(), testPlugin(), structureManager(), null, prePoint)
        }
        assertUnchanged(normal, nether, end, other)
    }

    @Test
    fun prepareOverworldSetsSpawnAndOriginalCentersPlusEndMaximumOnly() {
        val normal = world("overworld")
        val nether = world("the_nether")
        val end = world("the_end", maxSize = 28765.0)
        val effects = environment(normal, normal, normal, nether, end)

        effects.applyPrepare()

        assertEquals(listOf(Triple(-805, 83, 236)), normal.spawns)
        assertEquals(listOf(-805.0 to 236.0), normal.centers)
        assertEquals(listOf(-100.0 to 29.0), nether.centers) // -805 / 8 truncates to -100.
        assertEquals(listOf(0.0 to 0.0), end.centers)
        assertEquals(listOf(28765.0), end.immediateSizes)
        assertTrue(normal.immediateSizes.isEmpty())
        assertTrue(nether.immediateSizes.isEmpty())
        assertTrue(listOf(normal, nether, end).all { it.transitions.isEmpty() && it.pvp.isEmpty() })
    }

    @Test
    fun prepareNetherDoesNotMoveOverworldCenterAndPrepareEndFinishesAtZero() {
        val normal = world("overworld")
        val nether = world("the_nether")
        val end = world("the_end", maxSize = 45678.0)

        environment(nether, normal, normal, nether, end).applyPrepare()
        assertEquals(listOf(Triple(-805, 83, 236)), nether.spawns)
        assertEquals(listOf(-805.0 to 236.0), nether.centers)
        assertTrue(normal.centers.isEmpty())
        assertEquals(listOf(0.0 to 0.0), end.centers)
        assertEquals(listOf(45678.0), end.immediateSizes)

        val normal2 = world("overworld")
        val nether2 = world("the_nether")
        val end2 = world("the_end", maxSize = 39000.0)
        environment(end2, normal2, normal2, nether2, end2).applyPrepare()
        assertEquals(listOf(Triple(-805, 83, 236)), end2.spawns)
        assertEquals(listOf(0.0 to 0.0), end2.centers) // final original End override, once.
        assertEquals(listOf(39000.0), end2.immediateSizes)
        assertTrue(normal2.centers.isEmpty() && nether2.centers.isEmpty())

        val customPre = world("lobby", "custom")
        val normal3 = world("overworld")
        val nether3 = world("the_nether")
        val end3 = world("the_end")
        environment(customPre, normal3, normal3, nether3, end3).applyPrepare()
        assertEquals(listOf(-805.0 to 236.0), customPre.centers)
        assertEquals(listOf(Triple(-805, 83, 236)), customPre.spawns)
        assertTrue(normal3.centers.isEmpty() && nether3.centers.isEmpty())
        assertEquals(listOf(0.0 to 0.0), end3.centers)
    }

    @Test
    fun startPreservesAllDimensionBranchesAndCustomSpawnWithoutExtraCenter() {
        val normal = world("overworld")
        val nether = world("the_nether")
        val end = world("the_end")
        environment(normal, normal, normal, nether, end).applyStart()
        assertEquals(listOf(-805.0 to 236.0), normal.centers)
        assertEquals(listOf(-100.0 to 29.0), nether.centers)
        assertEquals(listOf(0.0 to 0.0), end.centers)
        assertEquals(listOf(Triple(-805, 83, 236)), normal.spawns)
        assertTrue(end.immediateSizes.isEmpty())

        val normal2 = world("overworld")
        val nether2 = world("the_nether")
        val end2 = world("the_end")
        environment(normal2, nether2, normal2, nether2, end2).applyStart()
        assertEquals(listOf(-100.0 to 29.0), normal2.centers)
        assertEquals(listOf(-805.0 to 236.0), nether2.centers)
        assertEquals(listOf(0.0 to 0.0), end2.centers)
        assertEquals(listOf(Triple(-805, 83, 236)), nether2.spawns)

        for (gameInEnd in listOf(true, false)) {
            val n = world("overworld")
            val h = world("the_nether")
            val e = world("the_end")
            val game = if (gameInEnd) e else world("arena", "custom")
            environment(n, game, n, h, e).applyStart()
            assertEquals(listOf(-805.0 to 236.0), n.centers)
            assertEquals(listOf(-100.0 to 29.0), h.centers)
            assertEquals(listOf(-805.0 to 236.0), e.centers)
            assertEquals(listOf(Triple(-805, 83, 236)), game.spawns)
            if (!gameInEnd) assertTrue(game.centers.isEmpty())
        }
    }

    @Test
    fun borderActionUsesExactTicksAndOnlyRoundsPositiveSubMinimumEffectiveSizes() {
        val normal = world("overworld")
        val nether = world("the_nether")
        val end = world("the_end")
        val effects = environment(normal, normal, normal, nether, end)
        val durations = listOf(0L, 1L, 19L, 20L, Int.MAX_VALUE.toLong())

        durations.forEach { effects.changeBorder(200.0, it) }
        assertEquals(durations.map { 200.0 to it }, normal.transitions)
        assertEquals(durations.map { 25.0 to it }, nether.transitions)
        assertEquals(durations.map { 200.0 to it }, end.transitions)
        assertTrue(listOf(normal, nether, end).all { it.immediateSizes.isEmpty() })

        effects.changeBorder(4.0, 0)
        assertEquals(4.0 to 0L, normal.transitions.last())
        assertEquals(1.0 to 0L, nether.transitions.last()) // /8 = 0.5, rounded up to Paper min 1.
        assertEquals(4.0 to 0L, end.transitions.last())
        effects.changeBorder(0.25, 1)
        assertEquals(1.0 to 1L, normal.transitions.last())
        assertEquals(1.0 to 1L, nether.transitions.last())
        assertEquals(1.0 to 1L, end.transitions.last())
    }

    @Test
    fun invalidSizesDurationsAndAnyDerivedBoundsFailBeforeTheFirstMutation() {
        val normal = world("overworld", maxSize = 100.0)
        val nether = world("the_nether", maxSize = 5.0)
        val end = world("the_end", maxSize = 10.0)
        val effects = environment(normal, normal, normal, nether, end)
        for (size in listOf(0.0, -10.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { effects.changeBorder(size, 0) }
        }
        for (ticks in listOf(-1L, Int.MAX_VALUE.toLong() + 1)) {
            assertThrows(IllegalArgumentException::class.java) { effects.changeBorder(8.0, ticks) }
        }
        assertThrows(IllegalArgumentException::class.java) { effects.changeBorder(48.0, 1) } // Nether max 5.
        assertThrows(IllegalArgumentException::class.java) { effects.changeBorder(16.0, 1) } // End max 10.
        assertUnchanged(normal, nether, end)

        val endWithBadCenter = world("the_end", maxCenter = 10.0)
        assertThrows(IllegalArgumentException::class.java) {
            environment(normal, endWithBadCenter, normal, nether, endWithBadCenter).applyStart()
        }
        assertUnchanged(normal, nether, endWithBadCenter)
        val normalWithBadCenter = world("overworld", maxCenter = 10.0)
        assertThrows(IllegalArgumentException::class.java) {
            environment(normalWithBadCenter, normalWithBadCenter, normalWithBadCenter, nether, end).applyPrepare()
        }
        assertUnchanged(normalWithBadCenter, nether, end)
        val endWithInvalidMax = world("the_end", maxSize = 0.5)
        assertThrows(IllegalArgumentException::class.java) {
            environment(normal, normal, normal, nether, endWithInvalidMax).applyPrepare()
        }
        assertUnchanged(normal, nether, endWithInvalidMax)

        assertThrows(IllegalArgumentException::class.java) {
            TournamentEnvironment(
                bindings(normal, normal, normal, nether, end),
                WorldPoint(normal.world, Double.MAX_VALUE, 83.0, 236.0), point(normal), tickets(),
                testPlugin(), structureManager(), null, point(normal)
            ).applyPrepare()
        }
        assertUnchanged(normal, nether, end)
    }

    @Test
    fun samePhysicalWorldIsUpdatedOnceAndConflictingRolesFailWithoutMutation() {
        val normal = world("overworld")
        val nether = world("the_nether")
        val combinedEnd = normal // same physical World, even though two roles.
        val effects = environment(normal, normal, normal, nether, combinedEnd)
        effects.changeBorder(80.0, 19)
        assertEquals(listOf(80.0 to 19L), normal.transitions)
        assertEquals(listOf(10.0 to 19L), nether.transitions)

        // The End/custom branch requests the same center for normal and End.
        // When they are the same object it is a single physical operation.
        val customGame = world("arena", "custom")
        environment(normal, customGame, normal, nether, combinedEnd).applyStart()
        assertEquals(listOf(-805.0 to 236.0), normal.centers)
        assertEquals(listOf(-100.0 to 29.0), nether.centers)
        assertEquals(listOf(Triple(-805, 83, 236)), customGame.spawns)

        val sharedNormalNether = world("overworld")
        val separateEnd = world("the_end")
        val conflict = environment(sharedNormalNether, sharedNormalNether,
            sharedNormalNether, sharedNormalNether, separateEnd)
        assertThrows(IllegalArgumentException::class.java) { conflict.changeBorder(80.0, 1) }
        assertThrows(IllegalArgumentException::class.java) { conflict.applyStart() }
        assertThrows(IllegalArgumentException::class.java) { conflict.applyPrepare() }
        assertUnchanged(sharedNormalNether, separateEnd)
    }

    @Test
    fun explicitPvpTouchesOnlyBoundWorldReferencesAndDoesNotRepeatThem() {
        // GameRules.PVP needs Paper registries that headless JUnit cannot initialize.
        val server: Server? = Bukkit.getServer()
        assumeTrue(server != null, "PvP gamerule assertions require a running Paper server")

        val normal = world("overworld")
        val nether = world("the_nether")
        val end = world("the_end")
        val arena = world("arena", "custom")
        val unrelated = world("unrelated", "custom")
        val effects = environment(normal, arena, normal, nether, end)
        effects.setPvp(false)
        effects.setPvp(true)

        for (related in listOf(normal, nether, end, arena)) {
            assertEquals(listOf(false, true), related.pvp)
            assertTrue(related.centers.isEmpty())
            assertTrue(related.transitions.isEmpty())
        }
        assertUnchanged(unrelated)
        assertTrue(unrelated.pvp.isEmpty())
    }

    @Test
    fun prepareRetainsTheGameWorld65By65PreloadAndReleaseIsIdempotent() {
        val normal = world("overworld")
        val nether = world("the_nether")
        val end = world("the_end")
        val game = world("arena", "custom")
        val effects = environment(normal, game, normal, nether, end)

        effects.applyPrepare()
        effects.applyPrepare()

        assertEquals(65 * 65, game.ticketAdds.size)
        assertTrue(normal.ticketAdds.isEmpty())
        assertTrue(game.ticketAdds.contains(-83 to -18))
        assertTrue(game.ticketAdds.contains(-19 to 46))
        assertEquals(65 * 65, game.ticketAdds.toSet().size)

        effects.releaseResources()
        effects.releaseResources()

        assertEquals(65 * 65, game.ticketRemovals.size)
        assertEquals(game.ticketAdds.toSet(), game.ticketRemovals.toSet())
    }

    @Test
    fun releasingEnvironmentLeasesKeepsAnOverlappingGeneratorLease() {
        val normal = world("overworld")
        val nether = world("the_nether")
        val end = world("the_end")
        val game = world("arena", "custom")
        val tickets = tickets()
        val generatorLease = tickets.acquire(game.world, -51, 14)
        val effects = TournamentEnvironment(
            bindings(normal, game, normal, nether, end), point(normal), point(game), tickets,
            testPlugin(), structureManager(), null, point(normal)
        )

        effects.applyPrepare()
        effects.releaseResources()

        assertEquals(65 * 65, game.ticketAdds.size)
        assertEquals(65 * 65 - 1, game.ticketRemovals.size)
        assertTrue(game.ticketAdds.contains(-51 to 14))
        assertTrue(!game.ticketRemovals.contains(-51 to 14))

        generatorLease.release()
        assertEquals(game.ticketAdds.toSet(), game.ticketRemovals.toSet())
    }

    @Test
    fun configuredBoxGeometryWorksBeforePlacementAndRespawnLocationIsFresh() {
        val normal = world("overworld")
        val nether = world("the_nether")
        val end = world("the_end")
        val key = NamespacedKey("test", "box")
        val template = structure(Vector(7, 5, 9))
        val effects = TournamentEnvironment(
            bindings(normal, normal, normal, nether, end), point(normal), point(normal), tickets(),
            testPlugin(), structureManager(template), key,
            WorldPoint(normal.world, -808.0, 80.0, 232.0)
        )

        assertTrue(effects.isInsideStartBox(point(normal)))
        assertTrue(!effects.isInsideStartBox(WorldPoint(normal.world, -801.0, 84.0, 232.0)))
        assertTrue(!effects.isInsideStartBox(WorldPoint(nether.world, -805.0, 83.0, 236.0)))
        val first = effects.preStartRespawnLocation()
        val second = effects.preStartRespawnLocation()
        assertNotSame(first, second)
        first.x = 10_000.0
        assertEquals(-805.0, second.x)
        assertTrue(normal.ticketAdds.isEmpty())
        assertUnchanged(normal, nether, end)
    }

    @Test
    fun invalidConfiguredBoxGeometryFailsBeforeTicketsOrWorldChanges() {
        val normal = world("overworld")
        val nether = world("the_nether")
        val end = world("the_end")
        val effects = TournamentEnvironment(
            bindings(normal, normal, normal, nether, end), point(normal), point(normal), tickets(),
            testPlugin(), structureManager(structure(Vector(0, 5, 9))),
            NamespacedKey("test", "bad"), WorldPoint(normal.world, -808.0, 80.0, 232.0)
        )

        assertThrows(IllegalArgumentException::class.java) { effects.isInsideStartBox(point(normal)) }
        assertTrue(normal.ticketAdds.isEmpty())
        assertUnchanged(normal, nether, end)
    }

    private fun assertUnchanged(vararg worlds: FakeWorld) {
        for (world in worlds) {
            assertTrue(world.mutations.isEmpty(), "Unexpected mutations for ${world.key}: ${world.mutations}")
        }
    }

    private fun environment(
        pre: FakeWorld, game: FakeWorld, normal: FakeWorld, nether: FakeWorld, end: FakeWorld
    ): TournamentEnvironment = TournamentEnvironment(
        bindings(pre, game, normal, nether, end), point(pre), point(game), tickets(),
        testPlugin(), structureManager(), null, point(pre)
    )

    private fun testPlugin() = Proxy.newProxyInstance(
        Plugin::class.java.classLoader, arrayOf(Plugin::class.java)
    ) { self, method, args ->
        when (method.name) {
            "equals" -> self === args?.get(0)
            "hashCode" -> System.identityHashCode(self)
            "toString" -> "Test plugin"
            else -> null
        }
    } as Plugin

    private fun structureManager(structure: Structure? = null) = Proxy.newProxyInstance(
        StructureManager::class.java.classLoader, arrayOf(StructureManager::class.java)
    ) { _, method, args ->
        if (method.name == "loadStructure") structure else error("Unexpected StructureManager method: ${method.name}")
    } as StructureManager

    private fun structure(size: Vector) = Proxy.newProxyInstance(
        Structure::class.java.classLoader, arrayOf(Structure::class.java)
    ) { _, method, _ ->
        if (method.name == "getSize") size else error("Unexpected Structure method: ${method.name}")
    } as Structure

    private fun tickets() = PluginChunkTickets(testPlugin())

    private fun bindings(
        pre: FakeWorld, game: FakeWorld, normal: FakeWorld, nether: FakeWorld, end: FakeWorld
    ) = ResolvedWorldBindings(pre.world, game.world, normal.world, nether.world, end.world)

    private fun point(world: FakeWorld) = WorldPoint(world.world, -805.0, 83.0, 236.0)
    private fun world(key: String, namespace: String = "minecraft", maxSize: Double = 60_000_000.0,
                      maxCenter: Double = 30_000_000.0) = FakeWorld(NamespacedKey(namespace, key), maxSize, maxCenter)

    private class FakeWorld(val key: NamespacedKey, private val maxSize: Double,
                            private val maxCenter: Double) {
        val mutations = mutableListOf<String>()
        val centers = mutableListOf<Pair<Double, Double>>()
        val immediateSizes = mutableListOf<Double>()
        val transitions = mutableListOf<Pair<Double, Long>>()
        val spawns = mutableListOf<Triple<Int, Int, Int>>()
        val pvp = mutableListOf<Boolean>()
        val ticketAdds = mutableListOf<Pair<Int, Int>>()
        val ticketRemovals = mutableListOf<Pair<Int, Int>>()
        private val uid = UUID.randomUUID()

        val border: WorldBorder = Proxy.newProxyInstance(
            WorldBorder::class.java.classLoader, arrayOf(WorldBorder::class.java)
        ) { self, method, args ->
            when (method.name) {
                "getMaxSize" -> maxSize
                "getMaxCenterCoordinate" -> maxCenter
                "setCenter" -> {
                    centers.add((args!![0] as Number).toDouble() to (args[1] as Number).toDouble())
                    mutations.add("center")
                    null
                }
                "setSize" -> {
                    immediateSizes.add((args!![0] as Number).toDouble())
                    mutations.add("size")
                    null
                }
                "changeSize" -> {
                    transitions.add((args!![0] as Number).toDouble() to (args[1] as Number).toLong())
                    mutations.add("transition")
                    null
                }
                "equals" -> self === args?.get(0)
                "hashCode" -> System.identityHashCode(self)
                "toString" -> "Test border of $key"
                else -> error("Unexpected WorldBorder method: ${method.name}")
            }
        } as WorldBorder

        val world: World = Proxy.newProxyInstance(
            World::class.java.classLoader, arrayOf(World::class.java)
        ) { self, method, args ->
            when (method.name) {
                "getKey" -> key
                "getUID" -> uid
                "getWorldBorder" -> border
                "getMinHeight" -> -64
                "getMaxHeight" -> 320
                "setSpawnLocation" -> {
                    assertEquals(3, args!!.size)
                    spawns.add(Triple(args[0] as Int, args[1] as Int, args[2] as Int))
                    mutations.add("spawn")
                    true
                }
                "setGameRule" -> {
                    assertSame(GameRules.PVP, args!![0])
                    pvp.add(args[1] as Boolean)
                    mutations.add("pvp")
                    true
                }
                "addPluginChunkTicket" -> {
                    ticketAdds.add(args!![0] as Int to args[1] as Int)
                    true
                }
                "removePluginChunkTicket" -> {
                    ticketRemovals.add(args!![0] as Int to args[1] as Int)
                    true
                }
                "equals" -> self === args?.get(0)
                "hashCode" -> System.identityHashCode(self)
                "toString" -> "Test world $key"
                else -> error("Unexpected World method: ${method.name}")
            }
        } as World
    }
}
