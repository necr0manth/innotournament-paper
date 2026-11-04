package dev.necr0manthre.innotournament.tournament

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.bukkit.GameMode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Path

class TournamentConfigCodecTest {
    private val codec = TournamentConfigCodec()
    private val original = checkNotNull(javaClass.getResource("/tournament/original-tournament.json")).readText()

    private fun sample(): JsonObject = JsonParser.parseString(original).asJsonObject

    @Test fun originalJavaSampleRoundTripsAllElevenFields() {
        val settings = codec.decode(original)
        assertEquals(BlockPosition(-805, 83, 236), settings.tournamentPreSpawn)
        assertEquals("minecraft:overworld", settings.tournamentPreSpawnDimension.toString())
        assertEquals(BlockPosition(-797, 71, 246), settings.tournamentSpawn)
        assertEquals("minecraft:overworld", settings.tournamentSpawnDimension.toString())
        assertEquals("minecraft:start", settings.startBoxStructureResourceLocation.toString())
        assertEquals(BlockPosition(-815, 82, 226), settings.startBoxPos)
        assertEquals(GameMode.SURVIVAL, settings.preStartGameMode)
        assertEquals(Path.of("preEvents.json"), settings.preEventsPath)
        assertEquals(Path.of("mainEvents.json"), settings.mainEventsPath)
        assertEquals(listOf(0.0, 1.0, 0.475, 0.3, 0.2, 0.15, 0.12), settings.teamScoreModifiers)
        assertEquals(10000f, settings.lastTeamScore)

        val written = JsonParser.parseString(codec.encode(settings)).asJsonObject
        assertEquals(sample().keySet(), written.keySet())
        assertTrue(written.get("tournamentPreSpawn")!!.isJsonArray)
        assertTrue(written.get("tournamentSpawn")!!.isJsonArray)
        assertTrue(written.get("startBoxPos")!!.isJsonArray)
        assertEquals("survival", written.get("preStartGameMode")!!.asString)
        assertEquals(settings, codec.decode(codec.encode(settings)))
        assertEquals(settings.hashCode(), codec.decode(codec.encode(settings)).hashCode())
    }

    @Test fun paperPositionObjectIsReadButCanonicalWriterUsesArrays() {
        val json = sample()
        json.add("tournamentPreSpawn", JsonObject().apply {
            addProperty("x", -805); addProperty("y", 83); addProperty("z", 236)
        })
        assertEquals(BlockPosition(-805, 83, 236), codec.decode(json.toString()).tournamentPreSpawn)
        assertTrue(JsonParser.parseString(codec.encode(codec.decode(json.toString())))
            .asJsonObject.get("tournamentPreSpawn")!!.isJsonArray)
    }

    @Test fun unknownTopLevelKeysDoNotBreakTypedReadAndAreNotPreserved() {
        val json = sample().apply {
            add("extra", JsonObject().apply { addProperty("ignored", true) })
        }
        val written = JsonParser.parseString(codec.encode(codec.decode(json.toString()))).asJsonObject
        assertFalse(written.has("extra"))
        assertEquals(11, written.size())
    }

    @Test fun noMissingFieldBecomesASuccessfulPartialErrorFallback() {
        // Every one of the 11 original Codec reads reports an error when missing.
        for (field in sample().keySet()) {
            val json = sample().apply { remove(field) }
            val failure = assertThrows(TournamentConfigException::class.java) {
                codec.decode(json.toString())
            }
            assertTrue(failure.message!!.contains(field), field)
        }
    }

    @Test fun invalidPresentValuesFailWithUsefulDiagnostics() {
        val invalid = listOf(
            "tournamentPreSpawn" to JsonParser.parseString("[1,2]"),
            "tournamentPreSpawn" to JsonParser.parseString("[1,2.5,3]"),
            "tournamentPreSpawn" to JsonParser.parseString("[1,2.0000000000000001,3]"),
            "tournamentSpawn" to JsonParser.parseString("[2147483648,1,2]"),
            "tournamentSpawn" to JsonParser.parseString("[-2147483649,1,2]"),
            "startBoxPos" to JsonParser.parseString("{\"x\":1,\"y\":null,\"z\":3}"),
            "tournamentPreSpawnDimension" to JsonParser.parseString("\"MINECRAFT:overworld\""),
            "tournamentSpawnDimension" to JsonParser.parseString("false"),
            "startBoxStructureResourceLocation" to JsonParser.parseString("\"bad id\""),
            "preStartGameMode" to JsonParser.parseString("\"not_a_mode\""),
            "preStartGameMode" to JsonParser.parseString("\"SURVIVAL\""),
            "preEventsPath" to JsonParser.parseString("8"),
            "mainEventsPath" to JsonParser.parseString("null"),
            "mainEventsPath" to JsonParser.parseString("\"bad\\u0000name\""),
            "teamScoreModifiers" to JsonParser.parseString("[0,-5,\"bad\"]"),
            "teamScoreModifiers" to JsonParser.parseString("[\"not_number\",1]"),
            "teamScoreModifiers" to JsonParser.parseString("[1e309]"),
            "lastTeamScore" to JsonParser.parseString("1e309"),
            "lastTeamScore" to JsonParser.parseString("true")
        )
        for ((field, value) in invalid) {
            val json = sample().apply { add(field, value) }
            val error = assertThrows(TournamentConfigException::class.java) {
                codec.decode(json.toString())
            }
            val message = error.message.orEmpty()
            assertTrue(message.contains(field), "Missing field '$field' from: $message")
            assertTrue(message.substringAfter(field).trim().trimStart(':').isNotBlank(), "Missing value reason: $message")
        }
        assertDiagnostic("tournamentPreSpawn", "[1,2]", "Expected exactly 3 coordinates")
        assertDiagnostic("tournamentPreSpawn", "[1,2.5,3]", "Expected a 32-bit integer")
        assertDiagnostic("preEventsPath", "8", "Not a string")
        assertDiagnostic("lastTeamScore", "true", "Not a number")
        assertThrows(TournamentConfigException::class.java) { codec.decode("[]") }
        assertThrows(TournamentConfigException::class.java) { codec.decode("{broken") }
        assertThrows(TournamentConfigException::class.java) { codec.decode(original + " {}") }
    }

    private fun assertDiagnostic(field: String, value: String, reason: String) {
        val json = sample().apply { add(field, JsonParser.parseString(value)) }
        val error = assertThrows(TournamentConfigException::class.java) { codec.decode(json.toString()) }
        assertTrue(error.message.orEmpty().contains(field), "Missing field '$field' from: ${error.message}")
        assertTrue(error.message.orEmpty().contains(reason), "Missing reason '$reason' from: ${error.message}")
    }

    @Test fun coordinateBoundsRoundTripWithoutOverflow() {
        val json = sample().apply {
            add("startBoxPos", JsonParser.parseString("[-2147483648,0,2147483647]"))
        }
        val settings = codec.decode(json.toString())
        assertEquals(BlockPosition(Int.MIN_VALUE, 0, Int.MAX_VALUE), settings.startBoxPos)
        assertEquals(settings, codec.decode(codec.encode(settings)))
    }

    @Test fun signedZeroFloatScoreHasConsistentValueEquality() {
        val document = sample()
        document.addProperty("lastTeamScore", -0.0)
        document.add("teamScoreModifiers", JsonArray().apply { add(-0.0) })
        val negativeZero = codec.decode(document.toString())
        document.addProperty("lastTeamScore", 0.0)
        document.add("teamScoreModifiers", JsonArray().apply { add(0.0) })
        val positiveZero = codec.decode(document.toString())
        assertNotEquals(negativeZero, positiveZero)
        assertEquals(negativeZero, codec.decode(codec.encode(negativeZero)))
        assertEquals(negativeZero.hashCode(), codec.decode(codec.encode(negativeZero)).hashCode())
    }

    @Test fun scoringIsDefensiveAndAcceptsZeroNegativeAndArbitraryCardinality() {
        val source = doubleArrayOf(0.0, -9.5)
        val base = codec.decode(original)
        val settings = TournamentSettings(
            base.tournamentPreSpawn, base.tournamentPreSpawnDimension,
            base.tournamentSpawn, base.tournamentSpawnDimension,
            base.startBoxStructureResourceLocation, base.startBoxPos,
            base.preStartGameMode, base.preEventsPath, base.mainEventsPath,
            source.asList(), -100.5f
        )
        source[0] = 77.0
        assertEquals(listOf(0.0, -9.5), settings.teamScoreModifiers)
        assertThrows(UnsupportedOperationException::class.java) {
            (settings.teamScoreModifiers as MutableList<Double>).add(1.0)
        }
        val reparsed = codec.decode(codec.encode(settings))
        assertEquals(settings, reparsed)
        assertEquals(-100.5f, reparsed.lastTeamScore)
        val json = sample().apply { add("teamScoreModifiers", JsonArray()) }
        assertTrue(codec.decode(json.toString()).teamScoreModifiers.isEmpty())
        assertThrows(IllegalArgumentException::class.java) {
            TournamentSettings(
                base.tournamentPreSpawn, base.tournamentPreSpawnDimension,
                base.tournamentSpawn, base.tournamentSpawnDimension,
                base.startBoxStructureResourceLocation, base.startBoxPos,
                base.preStartGameMode, base.preEventsPath, base.mainEventsPath,
                listOf(Double.NaN), base.lastTeamScore
            )
        }
    }
}
