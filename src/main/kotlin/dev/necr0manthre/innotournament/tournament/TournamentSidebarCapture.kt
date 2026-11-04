package dev.necr0manthre.innotournament.tournament

import dev.necr0manthre.innotournament.teams.PluginTeam
import dev.necr0manthre.innotournament.players.PluginPlayer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.Collections
import java.util.UUID

/** Captures permanent team facts once and returns a renderer over that frozen view. */
object TournamentSidebarCapture {
    fun capturePrepared(teamManager: PluginTeam.TeamManager): (Player) -> TournamentLiveSidebarBuilder.Sidebar {
        check(Bukkit.isPrimaryThread()) { "Prepared sidebar capture must run on the server thread" }

        val teamByPlayer = LinkedHashMap<UUID, TournamentLiveSidebarBuilder.Team>()
        teamManager.getAllTeams().forEach { team ->
            val members = team.players
            val roster = members.map { member ->
                TournamentLiveSidebarBuilder.RosterMember(member.displayName)
            }
            val snapshot = TournamentLiveSidebarBuilder.Team(team.displayName, roster)
            members.forEach { member -> teamByPlayer[member.uuid] = snapshot }
        }
        val frozenTeams = Collections.unmodifiableMap(teamByPlayer)

        return { viewer ->
            TournamentLiveSidebarBuilder.prepared(
                TournamentLiveSidebarBuilder.PreparedSnapshot(frozenTeams[viewer.uniqueId])
            )
        }
    }

    fun captureStarted(
        tournament: Tournament,
        teamManager: PluginTeam.TeamManager
    ): (Player) -> TournamentLiveSidebarBuilder.Sidebar {
        check(Bukkit.isPrimaryThread()) { "Started sidebar capture must run on the server thread" }
        check(tournament.phase == TournamentPhase.STARTED) { "Started sidebar capture requires a started tournament" }

        val runtimeTeamsById = tournament.teams().associateBy { it.id }
        val standings = tournament.standings()
        val rankedTeams = standings.map { standing ->
            val runtimeTeam = runtimeTeamsById[standing.id]
            check(runtimeTeam != null && tournament.getTeam(runtimeTeam.pluginTeam) === runtimeTeam) {
                "Standings contain a team without canonical runtime participation: ${standing.id}"
            }
            TournamentLiveSidebarBuilder.RankedTeam(
                standing.id,
                standing.displayName,
                standing.score,
                standing.rank
            )
        }

        val confirmedByPlayer = LinkedHashMap<UUID, TournamentLiveSidebarBuilder.Participation.Confirmed>()
        for (standing in standings) {
            val runtimeTeam = runtimeTeamsById.getValue(standing.id)
            val runtimePlayersByUuid = runtimeTeam.players.associateBy { it.uuid }
            val rosterByPlayer = standing.players.mapNotNull { savedPlayer ->
                val member = runtimePlayersByUuid[savedPlayer.uuid] ?: return@mapNotNull null
                val canonical = tournament.getPlayer(member.pluginPlayer)
                if (canonical !== member || canonical.team !== runtimeTeam) return@mapNotNull null
                savedPlayer.uuid to TournamentLiveSidebarBuilder.Participant(
                    member.displayName,
                    member.lives,
                    member.player != null
                )
            }
            val participation = TournamentLiveSidebarBuilder.Participation.Confirmed(
                standing.id,
                standing.displayName,
                standing.score,
                rosterByPlayer.map { it.second }
            )
            rosterByPlayer.forEach { (uuid, _) -> confirmedByPlayer[uuid] = participation }
        }

        val permanentTeamMembers = teamManager.getAllTeams()
            .flatMap { team -> team.players.map(PluginPlayer::uuid) }
            .toHashSet()
        val frozenConfirmedByPlayer = Collections.unmodifiableMap(LinkedHashMap(confirmedByPlayer))
        val frozenPermanentTeamMembers = Collections.unmodifiableSet(permanentTeamMembers)
        val frozenRankedTeams = Collections.unmodifiableList(ArrayList(rankedTeams))

        return { viewer ->
            val uuid = viewer.uniqueId
            val participation = frozenConfirmedByPlayer[uuid]
                ?: if (uuid in frozenPermanentTeamMembers) {
                    TournamentLiveSidebarBuilder.Participation.PermanentTeamNotAdmitted
                } else {
                    TournamentLiveSidebarBuilder.Participation.NotParticipating
                }
            TournamentLiveSidebarBuilder.started(
                TournamentLiveSidebarBuilder.StartedSnapshot(participation, frozenRankedTeams)
            )
        }
    }
}
