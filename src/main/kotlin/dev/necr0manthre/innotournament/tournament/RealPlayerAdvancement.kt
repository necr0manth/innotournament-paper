package dev.necr0manthre.innotournament.tournament

import org.bukkit.NamespacedKey

data class RealPlayerAdvancement(
    val player: TournamentPlayer,
    val key: NamespacedKey
)
