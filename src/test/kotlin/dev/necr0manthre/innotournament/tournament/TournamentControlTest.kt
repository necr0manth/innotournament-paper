package dev.necr0manthre.innotournament.tournament

import dev.necr0manthre.innotournament.teams.PluginTeam
import dev.necr0manthre.innotournament.tournament.events.EventPlan
import dev.necr0manthre.innotournament.tournament.events.SimpleEvent
import dev.necr0manthre.innotournament.tournament.events.TournamentEventInstance
import org.bukkit.GameMode
import org.bukkit.Location
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.file.Path

class TournamentControlTest {
    @Test
    fun prepareRequiresSelectionAndStartDoesNotPrepareImplicitly() {
        var factoryCalls = 0
        val control = TournamentControl(TournamentConfigRepository()) {
            factoryCalls++
            tournament()
        }

        assertThrows(IllegalStateException::class.java) { control.prepare() }
        control.select(Path.of("configs", "..", "tournament.json"))
        assertEquals(Path.of("tournament.json").toAbsolutePath(), control.selectedPath)
        assertThrows(IllegalStateException::class.java) { control.start() }
        assertNull(control.current)
        assertEquals(0, factoryCalls)
    }

    @Test
    fun replacementRemovesOldRunBeforeInvalidLoadAndDoesNotCallFactory() {
        var factoryCalls = 0
        val control = TournamentControl(TournamentConfigRepository()) {
            factoryCalls++
            tournament()
        }
        val previous = tournament()
        setCurrent(control, previous)
        control.select(Path.of("missing-tournament.json"))

        assertThrows(Exception::class.java) { control.prepare() }

        assertEquals(TournamentPhase.REMOVED, previous.phase)
        assertNull(control.current)
        assertEquals(0, factoryCalls)
    }

    @Test
    fun failedOldRunCleanupKeepsCurrentReference() {
        val control = TournamentControl(TournamentConfigRepository()) { tournament() }
        val previous = tournament()
        val cleanupFailure = IllegalStateException("unsubscribe failed")
        val instance = TournamentEventInstance(previous, SimpleEvent<Unit>()) { throw cleanupFailure }
        @Suppress("UNCHECKED_CAST")
        val instances = field(previous, "preStartEventInstances").get(previous) as MutableSet<TournamentEventInstance<*>>
        instances.add(instance)
        setCurrent(control, previous)
        control.select(Path.of("missing-tournament.json"))

        val actual = assertThrows(IllegalStateException::class.java) { control.prepare() }

        assertSame(cleanupFailure, actual)
        assertSame(previous, control.current)
        assertEquals(TournamentPhase.CREATED, previous.phase)
    }

    @Test
    fun successfulRemovalSetsTerminalPhaseAndNotifiesOnce() {
        val tournament = tournament()
        var removeNotices = 0
        tournament.onRemove.subscribe { removeNotices++ }

        tournament.remove(RemovalReason.SHUTDOWN)
        tournament.remove(RemovalReason.ERROR)

        assertEquals(TournamentPhase.REMOVED, tournament.phase)
        assertEquals(1, removeNotices)
    }

    private fun tournament() = Tournament(
        PluginTeam.TeamManager(),
        Location(null, 0.0, 0.0, 0.0),
        Location(null, 0.0, 0.0, 0.0),
        null,
        Location(null, 0.0, 0.0, 0.0),
        GameMode.SURVIVAL,
        EventPlan.of(emptyList()),
        EventPlan.of(emptyList()),
        doubleArrayOf(),
        0f
    )

    private fun setCurrent(control: TournamentControl, tournament: Tournament) {
        field(control, "currentValue").set(control, tournament)
    }

    private fun field(target: Any, name: String) = target.javaClass.getDeclaredField(name).apply { isAccessible = true }
}
