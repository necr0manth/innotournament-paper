package dev.necr0manthre.innotournament.tournament.events.event_data

import dev.necr0manthre.innotournament.tournament.TournamentTeam

interface ITeamProvider {
    val team: TournamentTeam?
}
