package dev.necr0manthre.innotournament.tournament.events.event_data

import dev.necr0manthre.innotournament.tournament.TournamentPlayer

interface ISourcePlayerProvider {
    val sourcePlayer: TournamentPlayer?
}
