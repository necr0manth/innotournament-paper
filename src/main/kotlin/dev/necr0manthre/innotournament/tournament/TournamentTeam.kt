package dev.necr0manthre.innotournament.tournament

import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.teams.PluginTeam
import net.kyori.adventure.text.Component
import org.bukkit.NamespacedKey

sealed interface TournamentTeam {
    val pluginTeam: PluginTeam
    val tournament: Tournament
    val score: Double
    val deleted: Boolean
    val advancements: Set<NamespacedKey>

    val id: String get() = pluginTeam.id
    val owner: TournamentPlayer? get() = pluginTeam.owner?.let(tournament::getPlayer)
    val displayName: Component get() = pluginTeam.displayName
    val players: List<TournamentPlayer>
    fun contains(player: PluginPlayer): Boolean = pluginTeam.players.contains(player)
    fun addScore(baseScore: Double) = tournament.addScores(this, baseScore)
}
