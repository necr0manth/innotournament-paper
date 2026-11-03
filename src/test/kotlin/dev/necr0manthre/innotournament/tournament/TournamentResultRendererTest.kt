package dev.necr0manthre.innotournament.tournament

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale
import java.util.UUID

class TournamentResultRendererTest {
    private val viewer = UUID.fromString("00000000-0000-0000-0000-000000000006")

    @Test
    fun sidebarUsesFrozenTopAndAddsViewersFinalTeamWithinBudget() {
        val teams = (1..6).map { rank ->
            TournamentResult.Team(
                "team$rank",
                Component.text("Team $rank"),
                10.0 - rank,
                rank,
                if (rank == 6) listOf(TournamentResult.Player(viewer, "Current name")) else emptyList()
            )
        }
        val result = TournamentResult(teams, emptySet(), null)

        val sidebar = TournamentResultRenderer.sidebar(result, viewer)
        val lines = sidebar.lines.map(PlainTextComponentSerializer.plainText()::serialize)

        assertEquals("InnoTournament", PlainTextComponentSerializer.plainText().serialize(sidebar.title))
        assertEquals("Завершён", lines.first())
        assertTrue(lines.contains("6. Team 6 (ваша команда): 4.00"))
        assertEquals(8, lines.size)
        assertTrue(lines.size <= 15)
    }

    @Test
    fun sidebarMarksViewersTeamInsideTopFiveButNotForOutsider() {
        val team = TournamentResult.Team(
            "team1",
            Component.text("Team 1"),
            10.0,
            1,
            listOf(TournamentResult.Player(viewer, "Saved name"))
        )
        val result = TournamentResult(listOf(team), emptySet(), null)
        val serializer = PlainTextComponentSerializer.plainText()

        val memberLines = TournamentResultRenderer.sidebar(result, viewer).lines.map { serializer.serialize(it) }
        val outsiderLines = TournamentResultRenderer.sidebar(result, UUID.randomUUID()).lines.map { serializer.serialize(it) }

        assertTrue(memberLines.any { it == "1. Team 1 (ваша команда): 10.00" })
        assertTrue(outsiderLines.any { it == "1. Team 1: 10.00" })
        assertTrue(outsiderLines.none { it.contains("ваша команда") })
    }

    @Test
    fun announcementIncludesEveryWinnerAllFinalTeamsAndFrozenRosterNames() {
        val uuid = UUID.fromString("00000000-0000-0000-0000-000000000007")
        val result = TournamentResult(
            listOf(
                TournamentResult.Team("first", Component.text("First"), 10.0, 1, listOf(TournamentResult.Player(viewer, "Saved"))),
                TournamentResult.Team("also-first", Component.text("Also first"), 10.0, 1, listOf(TournamentResult.Player(uuid, null))),
                TournamentResult.Team("third", Component.text("Third"), 8.0, 3, emptyList())
            ),
            linkedSetOf("first", "also-first"),
            null
        )

        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val text = PlainTextComponentSerializer.plainText()
                .serialize(TournamentResultRenderer.finalAnnouncement(result))

            assertTrue(text.contains("Победители: First, Also first"))
            assertTrue(text.contains("1. First: 10.00 — Saved"))
            assertTrue(text.contains("1. Also first: 10.00 — $uuid"))
            assertTrue(text.contains("3. Third: 8.00"))
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
