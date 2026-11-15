package dev.necr0manthre.innotournament.tournament

import dev.necr0manthre.innotournament.players.PluginPlayer
import net.kyori.adventure.text.Component
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import java.util.UUID

sealed interface TournamentPlayer {
    val pluginPlayer: PluginPlayer
    val tournament: Tournament
    val team: TournamentTeam
    val lives: Int
    val prepared: Boolean

    val uuid: UUID get() = pluginPlayer.uuid
    val player: Player? get() = pluginPlayer.player
    val offlinePlayer: OfflinePlayer get() = pluginPlayer.offlinePlayer
    val name: String get() = pluginPlayer.name
    val displayName: Component get() = pluginPlayer.displayName

    fun setLives(value: Int)
}
