package dev.necr0manthre.innotournament.tournament.components

import dev.necr0manthre.innotournament.tournament.Tournament
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Team
import java.lang.ref.WeakReference

class TournamentTeam(var team: Team) {
    var owner: Player? = null
    var acceptAll: Boolean = false
    var tournament: WeakReference<Tournament>? = null
    var displayName: Component = Component.empty()
    var players: List<TournamentPlayer> = emptyList()

    fun tryKickPlayerFromTeam(player: TournamentPlayer): Boolean {
        TODO()
    }

    companion object {
        fun get(team: Team): TournamentTeam? {
            TODO()
        }
        fun removeTeam(team: TournamentTeam){

        }
    }
}
