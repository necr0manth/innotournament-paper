package dev.necr0manthre.innotournament.tournament.events.handlers

import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.events.ITournamentEventListener
import net.minecraft.world.level.Level

class WorldBorderAction(private val size: Double, private val shrinkTime: Long) : ITournamentEventListener<Any?> {
    override fun listen(tournament: Tournament, value: Any?) {
        if (shrinkTime != 0L) {
            tournament.getServer().overworld().getWorldBorder()
                .lerpSizeBetween(tournament.getServer().overworld().getWorldBorder().getSize(), size, shrinkTime)
            tournament.getServer().getLevel(Level.NETHER)!!.getWorldBorder().lerpSizeBetween(
                tournament.getServer().getLevel(
                    Level.NETHER
                )!!.getWorldBorder().getSize(), size / 8, shrinkTime
            )
            tournament.getServer().getLevel(Level.END)!!.getWorldBorder().lerpSizeBetween(
                tournament.getServer().getLevel(
                    Level.END
                )!!.getWorldBorder().getSize(), size, shrinkTime
            )
        } else {
            tournament.getServer().overworld().getWorldBorder().setSize(size)
            tournament.getServer().getLevel(Level.NETHER)!!.getWorldBorder().setSize(size / 8)
            tournament.getServer().getLevel(Level.END)!!.getWorldBorder().setSize(size)
        }
    }
}
