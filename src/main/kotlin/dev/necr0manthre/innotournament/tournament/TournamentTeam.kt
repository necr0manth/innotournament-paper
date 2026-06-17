package dev.necr0manthre.innotournament.tournament

import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.teams.PluginTeam
import net.kyori.adventure.text.Component
import org.bukkit.advancement.Advancement
import org.bukkit.advancement.AdvancementProgress

class TournamentTeam internal constructor(
    val pluginTeam: PluginTeam,
    val tournament: Tournament
) {
    var score: Double = 0.0
    val advancements: MutableMap<Advancement, AdvancementProgress> = mutableMapOf()

    val id: String
        get() = pluginTeam.id

    val owner: TournamentPlayer?
        get() = pluginTeam.owner?.let { tournament.getPlayer(it) }

    var acceptAll: Boolean
        get() = pluginTeam.acceptAll
        set(value) {
            pluginTeam.acceptAll = value
        }

    val displayName: Component
        get() = pluginTeam.displayName

    val players: List<TournamentPlayer>
        get() = pluginTeam.players.map { tournament.getPlayer(it) }

    fun contains(player: PluginPlayer): Boolean {
        return pluginTeam.players.contains(player)
    }
}
