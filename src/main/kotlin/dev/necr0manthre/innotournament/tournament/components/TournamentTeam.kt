package dev.necr0manthre.innotournament.tournament.components

import dev.necr0manthre.innotournament.teams.PlayerJoinResult
import dev.necr0manthre.innotournament.teams.PlayerKickResult
import dev.necr0manthre.innotournament.tournament.Tournament
import net.kyori.adventure.text.Component
import org.bukkit.scoreboard.Team
import java.lang.ref.WeakReference

class TournamentTeam(var team: Team) {
    var id: String = ""
    var owner: TournamentPlayer? = null
    var acceptAll: Boolean = false
    var tournament: WeakReference<Tournament>? = null
    var displayName: Component = Component.empty()
    var players: List<TournamentPlayer> = emptyList()

    fun tryKickPlayerFromTeam(player: TournamentPlayer): PlayerKickResult {
        TODO()
    }

    fun tryAddPlayerToTeam(player: TournamentPlayer): PlayerJoinResult {
        TODO()
    }

    fun changeTeamName(name: Component): Boolean {
        TODO()
    }

    companion object {
        fun getAllTeams(): List<TournamentTeam> {
            TODO()
        }

        fun getTeamById(id: String): TournamentTeam? {
            TODO()
        }


        fun addTeam(team: TournamentTeam) {

        }

        fun removeTeam(team: TournamentTeam) {

        }
    }
}
