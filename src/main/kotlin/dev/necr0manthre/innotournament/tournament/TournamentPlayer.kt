package dev.necr0manthre.innotournament.tournament

import dev.necr0manthre.innotournament.teams.invites.InviteSystem
import dev.necr0manthre.innotournament.teams.invites.Invites
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import java.lang.ref.WeakReference
import java.util.UUID

class TournamentPlayer private constructor(val uuid: UUID) {
    var player: Player? = null
    var lives: Int = 0
    var tournament: WeakReference<Tournament>? = null
    var team: TournamentTeam? = null
    val invites = InviteSystem.createInvites()

    val offlinePlayer: OfflinePlayer
        get() = Bukkit.getOfflinePlayer(uuid)

    val name: String
        get() = player?.name ?: offlinePlayer.name ?: uuid.toString()

    val displayName: Component
        get() = player?.displayName() ?: Component.text(name)

    companion object {
        private val players = HashMap<UUID, TournamentPlayer>()

        fun get(player: Player): TournamentPlayer {
            return players.getOrPut(player.uniqueId) { TournamentPlayer(player.uniqueId) }
                .also { it.player = player }
        }
    }
}
