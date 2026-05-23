package dev.necr0manthre.innotournament.tournament.events

import dev.necr0manthre.innotournament.tournament.Tournament

fun interface TournamentEventFactory<T : AbstractTournamentEvent<*, *>> {
    fun createEvent(tournament: Tournament): T
}