package dev.necr0manthre.innotournament.tournament.components

import dev.necr0manthre.innotournament.tournament.Tournament
import net.kyori.adventure.text.Component
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import java.lang.ref.WeakReference

class TournamentPlayer {
    var player: Player? = null
    var lives: Int = 0
    var tournament: WeakReference<Tournament>? = null
    var team: TournamentTeam? = null
    val offlinePlayer: OfflinePlayer = TODO()
    val displayName: Component = Component.empty()

    companion object {
        fun get(player: Player): TournamentPlayer {
            TODO()
        }
    }
}
