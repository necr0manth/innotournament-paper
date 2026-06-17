package dev.necr0manthre.innotournament.players

import dev.necr0manthre.innotournament.teams.PluginTeam
import dev.necr0manthre.innotournament.teams.TeamSystem
import dev.necr0manthre.innotournament.teams.invites.InviteSystem
import dev.necr0manthre.innotournament.tournament.TournamentSystem
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import java.util.UUID

class PluginPlayer private constructor(val uuid: UUID) {
    var player: Player? = null
        private set
    val teamState = TeamSystem.createPlayerState()
    val invites = InviteSystem.createInvites()
    val tournamentState = TournamentSystem.createPlayerState()

    val team: PluginTeam?
        get() = teamState.team

    val offlinePlayer: OfflinePlayer
        get() = Bukkit.getOfflinePlayer(uuid)

    val name: String
        get() = player?.name ?: offlinePlayer.name ?: uuid.toString()

    val displayName: Component
        get() = player?.displayName() ?: Component.text(name)

    internal fun updateOnlinePlayer(player: Player) {
        this.player = player
    }

    companion object {
        private val players = HashMap<UUID, PluginPlayer>()

        fun get(player: Player): PluginPlayer {
            return players.getOrPut(player.uniqueId) { PluginPlayer(player.uniqueId) }
                .also { it.updateOnlinePlayer(player) }
        }

        fun get(uuid: UUID): PluginPlayer {
            return players.getOrPut(uuid) { PluginPlayer(uuid) }
        }
    }
}
