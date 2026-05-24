package dev.necr0manthre.innotournament.teams.events

import dev.necr0manthre.innotournament.tournament.TournamentPlayer
import dev.necr0manthre.innotournament.tournament.TournamentTeam

data class PlayerTeamEvent(
    val player: TournamentPlayer,
    val team: TournamentTeam?,
    var canceled: Boolean = false
)

