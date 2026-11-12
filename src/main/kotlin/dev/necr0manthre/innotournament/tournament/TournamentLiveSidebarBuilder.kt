package dev.necr0manthre.innotournament.tournament

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import java.util.Collections

/** Builds a personalized live sidebar from already captured display facts. */
object TournamentLiveSidebarBuilder {
    private fun <T> immutableCopy(values: List<T>): List<T> =
        Collections.unmodifiableList(ArrayList(values))

    data class Sidebar(val title: Component, val lines: List<Component>)

    data class PreparedSnapshot(val team: Team?)

    class StartedSnapshot(
        val participation: Participation,
        /** Domain-ordered teams with domain-owned ranks; this builder preserves their order. */
        rankedTeams: List<RankedTeam>
    ) {
        val rankedTeams: List<RankedTeam> = immutableCopy(rankedTeams)
    }

    class Team(
        val displayName: Component,
        roster: List<RosterMember>
    ) {
        val roster: List<RosterMember> = immutableCopy(roster)
    }

    data class RosterMember(val displayName: Component)

    sealed interface Participation {
        class Confirmed(
            val teamId: String,
            val teamName: Component,
            val score: Double,
            roster: List<Participant>
        ) : Participation {
            val roster: List<Participant> = immutableCopy(roster)
        }

        data object PermanentTeamNotAdmitted : Participation
        data object NotParticipating : Participation
    }

    data class Participant(
        val displayName: Component,
        val lives: Int,
        val online: Boolean
    )

    data class RankedTeam(
        val id: String,
        val displayName: Component,
        val score: Double,
        val rank: Int
    )

    fun prepared(snapshot: PreparedSnapshot): Sidebar {
        val lines = mutableListOf<Component>(Component.text("Подготовка", NamedTextColor.GOLD))
        val team = snapshot.team
        if (team == null) {
            lines += Component.text("Вы без команды", NamedTextColor.YELLOW)
        } else {
            lines += Component.text("Команда: ", NamedTextColor.YELLOW).append(team.displayName)
            lines += preparedRosterLines(team.roster.map(RosterMember::displayName))
        }
        return sidebar(lines)
    }

    fun started(snapshot: StartedSnapshot): Sidebar {
        val lines = mutableListOf<Component>(Component.text("Игра", NamedTextColor.GOLD))
        when (val participation = snapshot.participation) {
            is Participation.Confirmed -> {
                lines += Component.text("Команда: ", NamedTextColor.YELLOW)
                    .append(participation.teamName)
                    .append(Component.text(": ${TournamentScoreFormatter.format(participation.score)}"))
                lines += rosterLines(participation.roster.map { member ->
                    member.displayName
                        .append(if (member.online) Component.empty() else Component.text(" (не в сети)"))
                        .append(Component.text(" — ${member.lives}"))
                })
            }

            Participation.PermanentTeamNotAdmitted ->
                lines += Component.text("Команда вне турнира", NamedTextColor.YELLOW)

            Participation.NotParticipating ->
                lines += Component.text("Вы не участвуете", NamedTextColor.YELLOW)
        }

        lines += Component.text("Топ команд", NamedTextColor.YELLOW)
        val topTeams = snapshot.rankedTeams.take(SIDEBAR_TEAM_LIMIT)
        lines += topTeams.map(::rankedTeamLine)

        val viewerTeam = (snapshot.participation as? Participation.Confirmed)
            ?.let { confirmed -> snapshot.rankedTeams.firstOrNull { it.id == confirmed.teamId } }
        if (viewerTeam != null && topTeams.none { it.id == viewerTeam.id }) {
            lines += rankedTeamLine(viewerTeam)
        }

        return sidebar(lines)
    }

    private fun rankedTeamLine(team: RankedTeam): Component = Component.text("${team.rank}. ")
        .append(team.displayName)
        .append(Component.text(": ${TournamentScoreFormatter.format(team.score)}"))

    private fun rosterLines(names: List<Component>): List<Component> {
        val visible = if (names.size > ROSTER_LINE_LIMIT) {
            names.take(ROSTER_LINE_LIMIT - 1) + Component.text("ещё ${names.size - (ROSTER_LINE_LIMIT - 1)}")
        } else {
            names
        }
        return visible
    }

    private fun preparedRosterLines(names: List<Component>): List<Component> {
        val visible = if (names.size > ROSTER_LINE_LIMIT) {
            names.take(ROSTER_LINE_LIMIT - 1) + Component.text("ещё ${names.size - (ROSTER_LINE_LIMIT - 1)}")
        } else {
            names
        }
        return visible
    }

    private fun sidebar(lines: List<Component>) = Sidebar(
        Component.text("InnoTournament"),
        Collections.unmodifiableList(ArrayList(lines))
    )

    private const val SIDEBAR_TEAM_LIMIT = 5
    private const val ROSTER_LINE_LIMIT = 5
}
