package dev.necr0manthre.innotournament.presentation.map

import dev.necr0manthre.innotournament.generators.GeneratorKey
import dev.necr0manthre.innotournament.generators.GeneratorPlacementObservations
import dev.necr0manthre.innotournament.generators.GeneratorPosition
import dev.necr0manthre.innotournament.generators.GeneratorType
import org.bukkit.NamespacedKey
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.PluginManager
import org.bukkit.plugin.messaging.Messenger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.lang.reflect.Proxy
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import java.util.UUID

class JourneyMapDeliveryTest {
    @TempDir
    lateinit var tempDir: Path

    private val worldId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val position = GeneratorPosition(worldId, -10, 70, 32)
    private val key = GeneratorKey(position, GeneratorType.ITEM_GENERATOR)

    @Test
    fun `startup clears managed history before publishing and removal deletes only owned waypoint`() {
        val path = tempDir.resolve(MapPresentationStore.FILE_NAME)
        val store = MapPresentationStore(path)
        val name = JourneyMapWaypointMessages.managedName(position)
        store.rememberWaypointNames(
            listOf("innotournament_00000000-0000-0000-0000-000000000002_1_2_3", "innotournament_not-managed", "other_plugin_waypoint")
        )
        val world = world(worldId)
        val sent = mutableListOf<String>()
        val player = player(world, sent) {
            if (it == "create") assertTrue(MapPresentationStore(path).publishedWaypointNames.contains(name))
        }
        val fixture = fixture(listOf(player))
        val source = GeneratorPlacementObservations().also { it.discovered(key) }
        val delivery = JourneyMapDelivery(fixture.plugin, source, store, enabled = true)

        assertEquals(listOf("delete", "create"), sent)
        assertEquals(setOf("innotournament_00000000-0000-0000-0000-000000000002_1_2_3", name, "innotournament_not-managed", "other_plugin_waypoint"),
            store.publishedWaypointNames)

        source.removed(key)
        assertEquals(listOf("delete", "create", "delete"), sent)

        val beforeJoin = sent.size
        delivery.onPlayerJoin(PlayerJoinEvent(player, "joined"))
        assertEquals(listOf("delete", "delete"), sent.drop(beforeJoin))
        delivery.close()
        delivery.close()
        val beforeClosedChange = sent.size
        source.discovered(key)
        assertEquals(beforeClosedChange, sent.size)
    }

    @Test
    fun `disabled integration only cleans managed history and does not grow it`() {
        val path = tempDir.resolve(MapPresentationStore.FILE_NAME)
        val store = MapPresentationStore(path)
        val oldName = JourneyMapWaypointMessages.managedName(position)
        store.rememberWaypointName(oldName)
        val sent = mutableListOf<String>()
        val player = player(world(worldId), sent)
        val fixture = fixture(listOf(player))
        val source = GeneratorPlacementObservations().also { it.discovered(key) }
        val delivery = JourneyMapDelivery(fixture.plugin, source, store, enabled = false)

        assertEquals(listOf("delete"), sent)
        assertEquals(setOf(oldName), store.publishedWaypointNames)
        source.removed(key)
        assertEquals(listOf("delete"), sent)
        delivery.close()
    }

    private data class Fixture(val plugin: Plugin)

    private fun fixture(players: List<Player>): Fixture {
        val server = proxy(Server::class.java) { method, _ ->
            when (method.name) {
                "isPrimaryThread" -> true
                "getOnlinePlayers" -> players
                "getMessenger" -> proxy(Messenger::class.java) { _, _ -> null }
                "getPluginManager" -> proxy(PluginManager::class.java) { _, _ -> null }
                else -> defaultValue(method.returnType)
            }
        }
        val plugin = proxy(Plugin::class.java) { method, _ ->
            when (method.name) {
                "getServer" -> server
                "getName" -> "InnoTournament"
                else -> defaultValue(method.returnType)
            }
        }
        return Fixture(plugin)
    }

    private fun world(id: UUID): World = proxy(World::class.java) { method, _ ->
        when (method.name) {
            "getUID" -> id
            "getKey" -> NamespacedKey.minecraft("overworld")
            else -> defaultValue(method.returnType)
        }
    }

    private fun player(world: World, sent: MutableList<String>, beforeCreate: (String) -> Unit = {}): Player =
        proxy(Player::class.java) { method, args ->
            when (method.name) {
                "getWorld" -> world
                "sendPluginMessage" -> {
                    val action = decodeAction(args!![2] as ByteArray)
                    if (action == "create") beforeCreate(action)
                    sent.add(action)
                    null
                }
                else -> defaultValue(method.returnType)
            }
        }

    private fun <T> proxy(type: Class<T>, answer: (java.lang.reflect.Method, Array<out Any?>?) -> Any?): T =
        type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
            when (method.name) {
                "toString" -> "proxy(${type.simpleName})"
                "hashCode" -> System.identityHashCode(method)
                "equals" -> false
                else -> answer(method, args)
            }
        })

    private fun decodeAction(message: ByteArray): String {
        val input = ByteArrayInputStream(message)
        val jsonLength = input.readVarInt()
        input.skipNBytes(jsonLength.toLong())
        val actionLength = input.readVarInt()
        return DataInputStream(input).readNBytes(actionLength).toString(UTF_8)
    }

    private fun ByteArrayInputStream.readVarInt(): Int {
        var value = 0
        var shift = 0
        while (true) {
            val next = read()
            require(next >= 0 && shift < 35)
            value = value or ((next and 127) shl shift)
            if (next and 128 == 0) return value
            shift += 7
        }
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        java.lang.Character.TYPE -> '\u0000'
        else -> null
    }
}
