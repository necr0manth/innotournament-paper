package dev.necr0manthre.innotournament.tournament.events.event_data

import dev.necr0manthre.innotournament.tournament.TournamentPlayer

interface ITargetPlayerProvider {
    val targetPlayer: TournamentPlayer?
}
