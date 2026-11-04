package dev.necr0manthre.innotournament.presentation.map

import com.google.gson.JsonParser
import dev.necr0manthre.innotournament.generators.GeneratorKey
import dev.necr0manthre.innotournament.generators.GeneratorPosition
import dev.necr0manthre.innotournament.generators.GeneratorType
import org.bukkit.NamespacedKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID

class JourneyMapWaypointMessagesTest {
    @Test
    fun `create packet has current nested schema and exact framing`() {
        val position = GeneratorPosition(UUID.fromString("00000000-0000-0000-0000-000000000001"), -10, 70, 32)
        val key = GeneratorKey(position, GeneratorType.ITEM_GENERATOR)
        val bytes = JourneyMapWaypointMessages.create(key, NamespacedKey.minecraft("overworld"))
        val input = ByteArrayInputStream(bytes)
        val json = input.readProtocolUtf()
        assertTrue(json.toByteArray(UTF_8).size > 127, "JSON framing must exercise the multi-byte VarInt length")
        val action = input.readProtocolUtf()
        assertEquals("create", action)
        assertEquals(0, input.read())
        assertEquals(-1, input.read())

        val root = JsonParser.parseString(json).asJsonObject
        assertEquals("innotournament_00000000-0000-0000-0000-000000000001_-10_70_32", root["name"].asString)
        assertEquals("journeymap_default", root["groupId"].asString)
        assertEquals("external", root["origin"].asString)
        assertEquals(16776960, root["color"].asInt)
        val pos = root["pos"].asJsonObject
        assertEquals(-10, pos["x"].asInt)
        assertEquals(70, pos["y"].asInt)
        assertEquals(32, pos["z"].asInt)
        assertEquals("minecraft:overworld", pos["primaryDimension"].asString)
        assertEquals(listOf("minecraft:overworld"), root["dimensions"].asJsonArray.map { it.asString })
        val settings = root["settings"].asJsonObject
        assertTrue(settings["enable"].asBoolean)
        assertFalse(settings["showDeviation"].asBoolean)
        assertFalse(settings["persistent"].asBoolean)
        assertEquals("journeymap:textures/waypoint/icon/waypoint-icon.png", root.getAsJsonObject("icon").get("id").asString)
        assertFalse(root.has("id"))
        assertFalse(root.has("x"))
        assertFalse(root.has("persistent"))
    }

    @Test
    fun `type colors and delete packet use managed identity`() {
        val position = GeneratorPosition(UUID.fromString("00000000-0000-0000-0000-000000000001"), 4, 5, -6)
        val dimension = NamespacedKey.fromString("example:moon")!!
        val expectedColors = mapOf(
            GeneratorType.ITEM_GENERATOR to 0xFFFF00,
            GeneratorType.BOOK_GENERATOR to 0x00FFFF,
            GeneratorType.NETHER_GENERATOR to 0xFF0000
        )
        val name = JourneyMapWaypointMessages.managedName(position)

        expectedColors.forEach { (type, color) ->
            val json = packetParts(JourneyMapWaypointMessages.create(GeneratorKey(position, type), dimension)).first
            val root = JsonParser.parseString(json).asJsonObject
            assertEquals(name, root["name"].asString)
            assertEquals(color, root["color"].asInt)
            assertEquals("example:moon", root.getAsJsonObject("pos").get("primaryDimension").asString)
            assertEquals("example:moon", root["dimensions"].asJsonArray.get(0).asString)
        }

        val (deleteJson, action, announce) = packetParts(JourneyMapWaypointMessages.delete(name))
        val delete = JsonParser.parseString(deleteJson).asJsonObject
        assertEquals(setOf("name", "origin"), delete.keySet())
        assertEquals(name, delete["name"].asString)
        assertEquals("external", delete["origin"].asString)
        assertEquals("delete", action)
        assertEquals(0, announce)
    }

    private fun packetParts(bytes: ByteArray): Triple<String, String, Int> {
        val input = ByteArrayInputStream(bytes)
        val json = input.readProtocolUtf()
        val action = input.readProtocolUtf()
        val announce = input.read()
        assertEquals(-1, input.read())
        return Triple(json, action, announce)
    }

    private fun ByteArrayInputStream.readProtocolUtf(): String {
        val length = readProtocolVarInt()
        require(length >= 0 && length <= available())
        return DataInputStream(this).readNBytes(length).toString(UTF_8)
    }

    private fun ByteArrayInputStream.readProtocolVarInt(): Int {
        var value = 0
        var shift = 0
        while (true) {
            val next = read()
            require(next >= 0 && shift < 35) { "Invalid VarInt" }
            value = value or ((next and 127) shl shift)
            if (next and 128 == 0) return value
            shift += 7
        }
    }
}
