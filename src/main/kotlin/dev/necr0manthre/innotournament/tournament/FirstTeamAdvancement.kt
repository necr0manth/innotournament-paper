package dev.necr0manthre.innotournament.tournament

import org.bukkit.NamespacedKey

data class FirstTeamAdvancement(
    val team: TournamentTeam,
    val key: NamespacedKey
)
