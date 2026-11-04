package dev.necr0manthre.innotournament.tournament

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale
import java.util.ArrayList

class TournamentLiveSidebarBuilderTest {
    private val serializer = PlainTextComponentSerializer.plainText()

    @Test
    fun preparedShowsStableRosterWithoutRuntimeFieldsAndTruncatesLargeRoster() {
        val sidebar = TournamentLiveSidebarBuilder.prepared(
            TournamentLiveSidebarBuilder.PreparedSnapshot(
                TournamentLiveSidebarBuilder.Team(
                    Component.text("Blue"),
                    (1..7).map { TournamentLiveSidebarBuilder.RosterMember(Component.text("Player $it")) }
                )
            )
        )
        val lines = sidebar.lines.map(serializer::serialize)

        assertEquals("InnoTournament", serializer.serialize(sidebar.title))
        assertEquals("Подготовка", lines[0])
        assertEquals("Команда: Blue", lines[1])
        assertEquals(listOf("Player 1", "Player 2", "Player 3", "Player 4", "ещё 3"), lines.drop(2))
        assertTrue(lines.none { it.contains("score", ignoreCase = true) || it.contains("жизн") })
    }

    @Test
    fun startedShowsOfflineAndZeroLivesAndAddsActualViewerRankOutsideTopFive() {
        val snapshot = TournamentLiveSidebarBuilder.StartedSnapshot(
            TournamentLiveSidebarBuilder.Participation.Confirmed(
                teamId = "viewer-team",
                teamName = "Blue",
                score = 7.125,
                roster = listOf(
                    TournamentLiveSidebarBuilder.Participant("Online zero", lives = 0, online = true),
                    TournamentLiveSidebarBuilder.Participant("Offline alive", lives = 2, online = false)
                )
            ),
            rankedTeams = (1..6).map { rank ->
                TournamentLiveSidebarBuilder.RankedTeam(
                    id = if (rank == 6) "viewer-team" else "team-$rank",
                    displayName = "Team $rank",
                    score = 20.0 - rank,
                    rank = if (rank == 6) 9 else rank
                )
            }
        )

        val lines = TournamentLiveSidebarBuilder.started(snapshot).lines.map(serializer::serialize)

        assertTrue(lines.contains("Команда: Blue: 7.13"))
        assertTrue(lines.contains("Online zero — 0"))
        assertTrue(lines.contains("Offline alive (не в сети) — 2"))
        assertTrue(lines.contains("9. Team 6: 14.00"))
        assertEquals(11, lines.size)
        assertTrue(lines.size <= 15)
    }

    @Test
    fun startedDistinguishesNotAdmittedTeamFromNonparticipantAndKeepsDomainTieRanks() {
        val ranked = listOf(
            TournamentLiveSidebarBuilder.RankedTeam("b", "Team B", 10.0, 1),
            TournamentLiveSidebarBuilder.RankedTeam("a", "Team A", 10.0, 1),
            TournamentLiveSidebarBuilder.RankedTeam("c", "Team C", 8.0, 3)
        )
        fun lines(participation: TournamentLiveSidebarBuilder.Participation) =
            TournamentLiveSidebarBuilder.started(
                TournamentLiveSidebarBuilder.StartedSnapshot(participation, ranked)
            ).lines.map(serializer::serialize)

        val outsider = lines(TournamentLiveSidebarBuilder.Participation.NotParticipating)
        val notAdmitted = lines(TournamentLiveSidebarBuilder.Participation.PermanentTeamNotAdmitted)

        assertEquals("Вы не участвуете", outsider[1])
        assertEquals("Команда вне турнира", notAdmitted[1])
        assertEquals(listOf("1. Team B: 10.00", "1. Team A: 10.00", "3. Team C: 8.00"), outsider.drop(3))
        assertFalse(outsider.contains("Команда вне турнира"))
        assertFalse(notAdmitted.contains("Вы не участвуете"))
    }

    @Test
    fun scoreFormattingIgnoresDefaultLocale() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val lines = TournamentLiveSidebarBuilder.started(
                TournamentLiveSidebarBuilder.StartedSnapshot(
                    TournamentLiveSidebarBuilder.Participation.NotParticipating,
                    listOf(TournamentLiveSidebarBuilder.RankedTeam("a", "Team A", 1.25, 1))
                )
            ).lines.map(serializer::serialize)

            assertTrue(lines.contains("1. Team A: 1.25"))
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun snapshotsCopyAndExposeUnmodifiableRosterAndRankingLists() {
        val preparedRoster = ArrayList(listOf(TournamentLiveSidebarBuilder.RosterMember(Component.text("Before"))))
        val team = TournamentLiveSidebarBuilder.Team(Component.text("Blue"), preparedRoster)
        preparedRoster += TournamentLiveSidebarBuilder.RosterMember(Component.text("After"))
        assertEquals(listOf(Component.text("Before")), team.roster.map { it.displayName })
        assertThrows(UnsupportedOperationException::class.java) {
            (team.roster as MutableList).add(TournamentLiveSidebarBuilder.RosterMember(Component.text("Nope")))
        }

        val participantRoster = ArrayList(listOf(TournamentLiveSidebarBuilder.Participant("Before", 1, true)))
        val participation = TournamentLiveSidebarBuilder.Participation.Confirmed("blue", "Blue", 1.0, participantRoster)
        participantRoster += TournamentLiveSidebarBuilder.Participant("After", 2, false)
        assertEquals(listOf("Before"), participation.roster.map { it.displayName })
        assertThrows(UnsupportedOperationException::class.java) {
            (participation.roster as MutableList).clear()
        }

        val rankedTeams = ArrayList(listOf(TournamentLiveSidebarBuilder.RankedTeam("blue", "Blue", 1.0, 1)))
        val snapshot = TournamentLiveSidebarBuilder.StartedSnapshot(participation, rankedTeams)
        rankedTeams.clear()
        assertEquals(listOf("blue"), snapshot.rankedTeams.map { it.id })
        assertThrows(UnsupportedOperationException::class.java) {
            (snapshot.rankedTeams as MutableList).clear()
        }
    }
}
