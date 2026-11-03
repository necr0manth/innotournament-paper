package dev.necr0manthre.innotournament.tournament.environment

import org.bukkit.NamespacedKey
import org.bukkit.World
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.UUID

class WorldBindingsTest {
    @Test
    fun worldPointProducesFreshLocationsWithoutMutableAliasing() {
        val world = world(NamespacedKey.minecraft("overworld"))
        val point = WorldPoint(world, 12.5, 70.0, -8.25)
        val first = point.toLocation()
        val second = point.toLocation()

        assertNotSame(first, second)
        assertSame(world, first.world)
        assertSame(world, second.world)
        first.x = 999.0
        first.world = world(NamespacedKey("other", "world"))
        assertEquals(12.5, second.x)
        assertEquals(12.5, point.toLocation().x)
        assertEquals(70.0, point.toLocation().y)
        assertEquals(-8.25, point.toLocation().z)
        assertSame(world, point.toLocation().world)
        assertThrows(IllegalArgumentException::class.java) { WorldPoint(world, Double.NaN, 0.0, 0.0) }
    }

    @Test
    fun configuredDimensionsResolveByExactKeyNotEnvironmentOrWorldOrder() {
        val extraNormal = world(NamespacedKey("custom", "lobby"))
        val overworld = world(NamespacedKey.minecraft("overworld"))
        val nether = world(NamespacedKey.minecraft("the_nether"))
        val end = world(NamespacedKey.minecraft("the_end"))
        val arena = world(NamespacedKey("custom", "arena"))
        val candidates = listOf(extraNormal, arena, nether, end, overworld)

        val bindings = ResolvedWorldBindings.resolve(extraNormal.key, arena.key, candidates)
        assertSame(extraNormal, bindings.preStartWorld)
        assertSame(arena, bindings.gameWorld)
        assertSame(overworld, bindings.normalWorld)
        assertSame(nether, bindings.netherWorld)
        assertSame(end, bindings.endWorld)

        val error = assertThrows(IllegalArgumentException::class.java) {
            ResolvedWorldBindings.resolve(NamespacedKey("custom", "unknown"), arena.key, candidates)
        }
        assertTrue(error.message!!.contains("custom:unknown"))
    }

    @Test
    fun missingAndAmbiguousRequiredWorldKeysAreErrors() {
        val overworld = world(NamespacedKey.minecraft("overworld"))
        val anotherOverworld = world(NamespacedKey.minecraft("overworld"))
        val nether = world(NamespacedKey.minecraft("the_nether"))
        val end = world(NamespacedKey.minecraft("the_end"))

        val missing = assertThrows(IllegalArgumentException::class.java) {
            ResolvedWorldBindings.resolve(overworld.key, nether.key, listOf(overworld, nether))
        }
        assertTrue(missing.message!!.contains("minecraft:the_end"))
        val ambiguous = assertThrows(IllegalArgumentException::class.java) {
            ResolvedWorldBindings.resolve(overworld.key, nether.key,
                listOf(overworld, anotherOverworld, nether, end))
        }
        assertTrue(ambiguous.message!!.contains("ambiguous"))
    }

    @Test
    fun repeatedWorldReferenceIsAllowedButDistinctEqualWorldsRemainAmbiguous() {
        val id = UUID.randomUUID()
        val overworld = world(NamespacedKey.minecraft("overworld"), id)
        val otherOverworld = world(overworld.key, id)
        val nether = world(NamespacedKey.minecraft("the_nether"))
        val end = world(NamespacedKey.minecraft("the_end"))
        val candidates = listOf(overworld, overworld, nether, end)

        val bindings = ResolvedWorldBindings.resolve(overworld.key, nether.key, candidates)
        assertSame(overworld, bindings.preStartWorld)
        assertSame(overworld, bindings.normalWorld)

        assertNotSame(overworld, otherOverworld)
        assertEquals(overworld, otherOverworld)
        val ambiguous = assertThrows(IllegalArgumentException::class.java) {
            ResolvedWorldBindings.resolve(overworld.key, nether.key, candidates + otherOverworld)
        }
        assertTrue(ambiguous.message!!.contains("ambiguous"))
    }

    @Test
    fun customCompositionRetainsExactlyItsExplicitWorldReferences() {
        val preStart = world(NamespacedKey("custom", "prestart"))
        val game = world(NamespacedKey("custom", "game"))
        val normal = world(NamespacedKey("custom", "normal"))
        val nether = world(NamespacedKey("custom", "nether"))
        val end = world(NamespacedKey("custom", "end"))
        val bindings = ResolvedWorldBindings(preStart, game, normal, nether, end)

        assertSame(preStart, bindings.preStartWorld)
        assertSame(game, bindings.gameWorld)
        assertSame(normal, bindings.normalWorld)
        assertSame(nether, bindings.netherWorld)
        assertSame(end, bindings.endWorld)
    }

    private fun world(key: NamespacedKey, id: UUID = UUID.randomUUID()): World {
        return Proxy.newProxyInstance(World::class.java.classLoader, arrayOf(World::class.java)) { self, method, args ->
            when (method.name) {
                "getKey" -> key
                "getUID" -> id
                "equals" -> (args?.get(0) as? World)?.uid == id
                "hashCode" -> id.hashCode()
                "toString" -> "test world $key"
                else -> error("Unexpected World API call: ${method.name}")
            }
        } as World
    }
}
