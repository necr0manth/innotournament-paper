package dev.necr0manthre.innotournament.tournament.events.handlers

import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.events.ITournamentEventListener
import org.bukkit.World

class WorldBorderAction(private val size: Double, private val shrinkTime: Long) : ITournamentEventListener<Any?> {
    override fun listen(tournament: Tournament, value: Any?) {
        val overworld = tournament.getServer().worlds.firstOrNull { it.environment == World.Environment.NORMAL }
        val nether = tournament.getServer().worlds.firstOrNull { it.environment == World.Environment.NETHER }
        val end = tournament.getServer().worlds.firstOrNull { it.environment == World.Environment.THE_END }
        if (shrinkTime != 0L) {
            val seconds = (shrinkTime / 20L).coerceAtLeast(1L)
            overworld?.worldBorder?.setSize(size, seconds)
            nether?.worldBorder?.setSize(size / 8, seconds)
            end?.worldBorder?.setSize(size, seconds)
        } else {
            overworld?.worldBorder?.size = size
            nether?.worldBorder?.size = size / 8
            end?.worldBorder?.size = size
        }
    }
}
