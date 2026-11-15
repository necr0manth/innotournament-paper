package dev.necr0manthre.innotournament.tournament.events.handlers

import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.events.ITournamentEventListener

class WorldBorderAction(private val size: Double, private val shrinkTime: Long) : ITournamentEventListener<Any?> {
    override fun listen(tournament: Tournament, value: Any?) {
        tournament.changeBorder(size, shrinkTime)
    }
}
