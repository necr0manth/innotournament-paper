package dev.necr0manthre.innotournament.tournament.events

import dev.necr0manthre.innotournament.tournament.Tournament

fun interface ITournamentEventFactory<T : AbstractTournamentEvent<*>> {
    fun createEvent(tournament: Tournament): T
}