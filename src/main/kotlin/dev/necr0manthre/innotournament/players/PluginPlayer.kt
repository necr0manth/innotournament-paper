package dev.necr0manthre.innotournament.players

import dev.necr0manthre.innotournament.teams.PluginTeam
import dev.necr0manthre.innotournament.teams.invites.InviteSystem
import dev.necr0manthre.innotournament.tournament.Tournament
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import java.util.UUID

class PluginPlayer private constructor(val uuid: UUID) {
    val player: Player?
        get() = Bukkit.getPlayer(uuid)
    val teamState: PluginTeam.PlayerTeamState = PluginTeam.createPlayerState()
    val invites = InviteSystem.createInvites()
    val tournamentState = Tournament.createPlayerState()

    val team: PluginTeam?
        get() = teamState.team

    val offlinePlayer: OfflinePlayer
        get() = Bukkit.getOfflinePlayer(uuid)

    val name: String
        get() = player?.name ?: offlinePlayer.name ?: uuid.toString()

    val displayName: Component
        get() = player?.displayName() ?: Component.text(name)

    companion object {
        private val players = HashMap<UUID, PluginPlayer>()

        fun get(player: Player): PluginPlayer = get(player.uniqueId)

        fun get(uuid: UUID): PluginPlayer {
            return players.getOrPut(uuid) { PluginPlayer(uuid) }
        }
    }
}
