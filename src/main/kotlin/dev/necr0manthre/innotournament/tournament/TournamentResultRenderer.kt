package dev.necr0manthre.innotournament.tournament

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import java.util.Collections
import java.util.Locale
import java.util.UUID

/** Builds the frozen ended-state sidebar and announcement without consulting live teams or players. */
object TournamentResultRenderer {
    data class Sidebar(val title: Component, val lines: List<Component>)

    fun sidebar(result: TournamentResult, viewer: UUID): Sidebar {
        val viewerTeam = result.teamForPlayer(viewer)
        val visibleTeams = result.teams.take(SIDEBAR_TEAM_LIMIT).toMutableList()
        if (viewerTeam != null && visibleTeams.none { it.id == viewerTeam.id }) {
            visibleTeams.add(viewerTeam)
        }

        val lines = mutableListOf<Component>(Component.text("Завершён", NamedTextColor.GOLD))
        lines.add(Component.text("Итог турнира", NamedTextColor.YELLOW))
        for (team in visibleTeams) {
            lines += teamLine(team, sidebar = true, isViewerTeam = team.id == viewerTeam?.id)
        }
        return Sidebar(Component.text("InnoTournament"), Collections.unmodifiableList(ArrayList(lines)))
    }

    fun finalAnnouncement(result: TournamentResult): Component {
        val winners = result.teams.filter { it.id in result.winnerTeamIds }
        val builder = Component.text()
            .append(Component.text("Турнир завершён", NamedTextColor.GOLD))
            .append(Component.newline())

        if (winners.isEmpty()) {
            builder.append(Component.text("Победитель не определён", NamedTextColor.YELLOW))
        } else {
            builder.append(Component.text("Победители: ", NamedTextColor.YELLOW))
            winners.forEachIndexed { index, team ->
                if (index > 0) builder.append(Component.text(", "))
                builder.append(team.displayName)
            }
        }

        for (team in result.teams) {
            builder.append(Component.newline()).append(teamLine(team, sidebar = false))
            if (team.players.isNotEmpty()) {
                builder.append(Component.text(" — "))
                team.players.forEachIndexed { index, player ->
                    if (index > 0) builder.append(Component.text(", "))
                    builder.append(Component.text(player.savedName ?: player.uuid.toString()))
                }
            }
        }
        return builder.build()
    }

    private fun teamLine(
        team: TournamentResult.Team,
        sidebar: Boolean,
        isViewerTeam: Boolean = false
    ): Component =
        Component.text("${team.rank}. ")
            .append(team.displayName)
            .let { line ->
                if (isViewerTeam) line.append(Component.text(" (ваша команда)", NamedTextColor.AQUA)) else line
            }
            .append(Component.text(": "))
            .append(formattedScore(team.score, sidebar))

    private fun formattedScore(score: Double, sidebar: Boolean): Component =
        Component.text(String.format(Locale.ROOT, "%.2f", score)).let { formatted ->
            if (sidebar) formatted else formatted.hoverEvent(
                Component.text("Точное значение: $score")
            )
        }

    private const val SIDEBAR_TEAM_LIMIT = 5
}
