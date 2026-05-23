package dev.necr0manthre.innotournament.tournament.events.event_data

import dev.necr0manthre.innotournament.tournament.TournamentPlayer

data class PlayerKilled(
    override val sourcePlayer: TournamentPlayer,
    override val targetPlayer: TournamentPlayer
) :
    ISourcePlayerProvider, ITargetPlayerProvider, ITeamProvider {
    override val team = sourcePlayer.team
}
