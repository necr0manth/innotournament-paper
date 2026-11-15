package dev.necr0manthre.innotournament.tournament

import dev.necr0manthre.innotournament.tournament.environment.PluginChunkTickets
import dev.necr0manthre.innotournament.tournament.environment.ResolvedWorldBindings
import dev.necr0manthre.innotournament.tournament.environment.TournamentEnvironment
import dev.necr0manthre.innotournament.tournament.environment.WorldPoint
import org.bukkit.GameMode
import org.bukkit.NamespacedKey
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.advancement.Advancement
import org.bukkit.advancement.AdvancementProgress
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.inventory.PlayerInventory
import org.bukkit.plugin.Plugin
import org.bukkit.structure.StructureManager
import org.bukkit.util.Vector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.UUID

class TournamentPlayerEffectsTest {
    @Test
    fun resetClearsPersonalStateAndUsesTheBoundGameSpawn() {
        val world = world()
        val gameSpawn = WorldPoint(world, -24.25, 71.5, 16.75)
        val effects = TournamentPlayerEffects(environment(world, gameSpawn))
        val calls = mutableListOf<String>()
        val awarded = mutableListOf<String>()
        val advancementProgress = proxy<AdvancementProgress> { method, args ->
            when (method) {
                "getAwardedCriteria" -> setOf("first", "second")
                "revokeCriteria" -> {
                    awarded += args!![0] as String
                    true
                }
                else -> unexpected(method)
            }
        }
        val advancement = proxy<Advancement> { method, _ ->
            if (method == "getKey") NamespacedKey.minecraft("test") else unexpected(method)
        }
        val server = proxy<Server> { method, _ ->
            if (method == "advancementIterator") listOf(advancement).iterator() else unexpected(method)
        }
        val inventory = proxy<PlayerInventory> { method, _ ->
            if (method == "clear") { calls += "inventory"; null } else unexpected(method)
        }
        val player = proxy<Player> { method, args ->
            when (method) {
                "getMaxHealth" -> 20.0
                "setHealth" -> { assertEquals(20.0, args!![0]); calls += "health"; null }
                "setFoodLevel" -> { assertEquals(20, args!![0]); calls += "food"; null }
                "setSaturation" -> { assertEquals(1f, args!![0]); calls += "saturation"; null }
                "getInventory" -> inventory
                "getServer" -> server
                "getAdvancementProgress" -> advancementProgress
                "setRespawnLocation" -> {
                    assertEquals(null, args!![0])
                    assertEquals(false, args[1])
                    calls += "respawn"
                    null
                }
                "setGameMode" -> { assertEquals(GameMode.SURVIVAL, args!![0]); calls += "mode"; null }
                "teleport" -> {
                    val target = args!![0] as org.bukkit.Location
                    assertSame(world, target.world)
                    assertEquals(-24.25, target.x)
                    assertEquals(71.5, target.y)
                    assertEquals(16.75, target.z)
                    assertEquals(0f, target.yaw)
                    assertEquals(0f, target.pitch)
                    assertEquals(PlayerTeleportEvent.TeleportCause.PLUGIN, args[1])
                    calls += "teleport"
                    true
                }
                "setVelocity" -> {
                    assertEquals(Vector(), args!![0])
                    calls += "velocity"
                    null
                }
                "setExp" -> { assertEquals(0f, args!![0]); calls += "exp"; null }
                "setLevel" -> { assertEquals(0, args!![0]); calls += "level"; null }
                "setTotalExperience" -> { assertEquals(0, args!![0]); calls += "totalExperience"; null }
                "clearActivePotionEffects" -> { calls += "potion"; true }
                else -> unexpected(method)
            }
        }

        effects.resetNewParticipation(player)

        assertEquals(listOf("first", "second"), awarded)
        assertEquals(
            listOf("health", "food", "saturation", "inventory", "respawn", "mode", "teleport", "velocity", "exp", "level", "totalExperience", "potion"),
            calls
        )
    }

    @Test
    fun teleportFailurePropagatesAndStopsTheReset() {
        val world = world()
        val calls = mutableListOf<String>()
        val server = proxy<Server> { method, _ ->
            if (method == "advancementIterator") emptyList<Advancement>().iterator() else unexpected(method)
        }
        val player = proxy<Player> { method, _ ->
            when (method) {
                "getMaxHealth" -> 20.0
                "setHealth", "setFoodLevel", "setSaturation", "setRespawnLocation", "setGameMode" -> null
                "getInventory" -> proxy<PlayerInventory> { name, _ -> if (name == "clear") null else unexpected(name) }
                "getServer" -> server
                "teleport" -> { calls += "teleport"; false }
                else -> unexpected(method)
            }
        }
        val environment = environment(world, WorldPoint(world, 1.0, 65.0, 1.0))

        val failure = assertThrows(IllegalStateException::class.java) {
            TournamentPlayerEffects(environment).resetNewParticipation(player)
        }

        assertTrue(failure.message!!.contains("Could not teleport"))
        assertEquals(listOf("teleport"), calls)
    }

    private fun environment(world: World, gameSpawn: WorldPoint): TournamentEnvironment {
        val bindings = ResolvedWorldBindings(world, world, world, world, world)
        val plugin = proxy<Plugin> { method, _ -> if (method == "getName") "test" else unexpected(method) }
        return TournamentEnvironment(
            bindings, gameSpawn, gameSpawn, PluginChunkTickets(plugin), plugin,
            proxy<StructureManager> { method, _ -> unexpected(method) }, null, gameSpawn
        )
    }

    private fun world(): World {
        val id = UUID.randomUUID()
        return proxy { method, args ->
            when (method) {
                "getKey" -> NamespacedKey.minecraft("world")
                "getUID" -> id
                "equals" -> (args?.get(0) as? World)?.uid == id
                "hashCode" -> id.hashCode()
                "toString" -> "test world"
                else -> unexpected(method)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> proxy(noinline handler: (String, Array<out Any?>?) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            handler(method.name, args)
        } as T

    private fun unexpected(method: String): Nothing = error("Unexpected API call: $method")
}
