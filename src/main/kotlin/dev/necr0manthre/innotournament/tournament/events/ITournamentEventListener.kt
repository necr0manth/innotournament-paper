package dev.necr0manthre.innotournament.tournament.events

import dev.necr0manthre.innotournament.tournament.Tournament

interface ITournamentEventListener<T> {
    fun listen(tournament: Tournament, value: T)
}
