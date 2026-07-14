package dev.necr0manthre.innotournament.tournament

import dev.necr0manthre.innotournament.players.PluginPlayer
import net.kyori.adventure.text.Component
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import java.util.*

class TournamentPlayer internal constructor(
    val pluginPlayer: PluginPlayer,
    val tournament: Tournament
) {
    var lives: Int = 0
    internal var initialized: Boolean = false

    val uuid: UUID
        get() = pluginPlayer.uuid

    val player: Player?
        get() = pluginPlayer.player

    val offlinePlayer: OfflinePlayer
        get() = pluginPlayer.offlinePlayer

    val name: String
        get() = pluginPlayer.name

    val displayName: Component
        get() = pluginPlayer.displayName

    val team: TournamentTeam?
        get() = pluginPlayer.team?.let { tournament.getTeam(it) }
}
