package dev.necr0manthre.innotournament.tournament

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class TournamentResultTest {
    private val first = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val second = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val outsider = UUID.fromString("00000000-0000-0000-0000-000000000003")

    @Test
    fun freezesNestedRostersTeamOrderAndWinnerSet() {
        val name = Component.text("Крылья", NamedTextColor.GOLD)
        val roster = mutableListOf(TournamentResult.Player(first, "Old name"))
        val team = TournamentResult.Team("alpha", name, 12.345, 1, roster)
        val teams = mutableListOf(team)
        val winners = linkedSetOf("alpha")
        val result = TournamentResult(teams, winners, "alpha")

        roster.add(TournamentResult.Player(second, "Later"))
        teams.clear()
        winners.clear()

        assertEquals(TournamentResult.Reason.NORMAL, result.reason)
        assertEquals(listOf("alpha"), result.teams.map { it.id })
        assertEquals(name, result.teams.single().displayName)
        assertEquals(12.345, result.teams.single().score)
        assertEquals(1, result.teams.single().rank)
        assertEquals(listOf(TournamentResult.Player(first, "Old name")), team.players)
        assertEquals(setOf("alpha"), result.winnerTeamIds)
        assertEquals("alpha", result.lastAliveTeamId)
        assertSame(team, result.teamForPlayer(first))
        assertNull(result.teamForPlayer(second))

        assertThrows(UnsupportedOperationException::class.java) {
            (result.teams as MutableList<TournamentResult.Team>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (team.players as MutableList<TournamentResult.Player>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (result.winnerTeamIds as MutableSet<String>).clear()
        }
    }

    @Test
    fun preservesSuppliedTiesAndUnknownNamesWithoutRankingOrLookingUpLivePlayers() {
        val shared = Component.text("Same", NamedTextColor.AQUA)
        val b = TournamentResult.Team("b", shared, 10.0, 1,
            listOf(TournamentResult.Player(second, null)))
        val a = TournamentResult.Team("a", shared, 10.0, 1,
            listOf(TournamentResult.Player(first, "Saved name")))
        val c = TournamentResult.Team("c", Component.text("Third"), 9.0, 3, emptyList())
        val result = TournamentResult(listOf(b, a, c), linkedSetOf("b", "a"), null)

        assertEquals(listOf("b", "a", "c"), result.teams.map { it.id })
        assertEquals(listOf(1, 1, 3), result.teams.map { it.rank })
        assertEquals(listOf(10.0, 10.0, 9.0), result.teams.map { it.score })
        assertEquals(listOf("b", "a"), result.winnerTeamIds.toList())
        assertNull(result.teams[0].players.single().savedName)
        assertSame(b, result.teamForPlayer(second))
        assertSame(a, result.teamForPlayer(first))
        assertNull(result.lastAliveTeamId)
        assertNull(result.teamForPlayer(outsider))

        val empty = TournamentResult(emptyList(), emptySet(), null)
        assertEquals(emptyList<TournamentResult.Team>(), empty.teams)
        assertEquals(emptySet<String>(), empty.winnerTeamIds)
    }
}
