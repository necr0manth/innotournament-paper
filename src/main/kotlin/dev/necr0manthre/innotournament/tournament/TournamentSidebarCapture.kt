package dev.necr0manthre.innotournament.tournament

import dev.necr0manthre.innotournament.teams.PluginTeam
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
}
