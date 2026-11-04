package dev.necr0manthre.innotournament.tournament.events

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dev.necr0manthre.innotournament.init.InnoTournamentEventHandlers
import dev.necr0manthre.innotournament.init.InnoTournamentEvents
import dev.necr0manthre.innotournament.tournament.events.handlers.WorldBorderAction
import dev.necr0manthre.innotournament.tournament.events.parsing.TournamentEventHandlerParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.nio.file.Path

class WorldBorderArgumentsTest {
    companion object {
        @JvmStatic @BeforeAll fun registerCatalog() {
            InnoTournamentEvents.init()
            InnoTournamentEventHandlers.init()
        }
    }

    private fun load(vararg actions: String): EventPlan {
        val entries = JsonArray()
        actions.forEach { entries.add(it) }
        val json = JsonObject().apply { add("onStart", entries) }
        return EventLoader.loadEvents(json, Path.of("world-border.json"), EventPlanStage.MAIN)
    }

    private fun assertInvalid(action: String) {
        val error = assertThrows(EventPlanLoadException::class.java) { load(action) }
        assertTrue(error.message!!.contains("world-border.json"), action)
        assertTrue(error.message!!.contains(action), action)
    }

    private fun assertParsedAction(action: String, size: Double, ticks: Long) {
        assertEquals(1, load(action).entries.size)
        val listener = TournamentEventHandlerParser.parse(action)?.listener
        assertTrue(listener is WorldBorderAction, action)
        val sizeField = WorldBorderAction::class.java.getDeclaredField("size").apply { isAccessible = true }
        val ticksField = WorldBorderAction::class.java.getDeclaredField("shrinkTime").apply { isAccessible = true }
        assertEquals(size, sizeField.getDouble(listener), action)
        assertEquals(ticks, ticksField.getLong(listener), action)
    }

    @Test fun zeroNegativeAndNonFiniteSizesRejectTheWholeLoad() {
        for (size in listOf("-10", "0", "-0.0", "NaN", "Infinity", "-Infinity", "1e309")) {
            assertInvalid("world_border $size")
            assertInvalid("world_border $size 21")
        }
        assertThrows(EventPlanLoadException::class.java) {
            load("world_border 1000 0", "world_border -10 0")
        }
    }

    @Test fun negativeOutOfApiRangeAndOverflowDurationsRejectAtLoad() {
        for (ticks in listOf("-20", "-1", "2147483648", "9223372036854775807", "9223372036854775808")) {
            assertInvalid("world_border 1000 $ticks")
        }
    }

    @Test fun zeroAndExactPositiveTickValuesReachTheProductionActionUnchanged() {
        assertParsedAction("world_border 1000.5", 1000.5, 0L)
        for (ticks in listOf(0L, 1L, 19L, 21L, 2400L, Int.MAX_VALUE.toLong())) {
            assertParsedAction("world_border 1000.5 $ticks", 1000.5, ticks)
        }
    }

    @Test fun positiveRawSizesAreNotSubjectToAnInventedNetherOrGlobalMinimum() {
        for (size in listOf(0.125, 1.0, 7.5, 1000.5)) {
            assertParsedAction("world_border $size 21", size, 21L)
        }
    }
}
