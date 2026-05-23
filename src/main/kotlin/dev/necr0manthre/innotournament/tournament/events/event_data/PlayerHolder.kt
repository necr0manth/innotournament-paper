package dev.necr0manthre.innotournament.tournament.events.event_data

import dev.necr0manthre.innotournament.tournament.TournamentPlayer

data class PlayerHolder(val player: TournamentPlayer) : ISourcePlayerProvider, ITargetPlayerProvider, ITeamProvider {
    override val sourcePlayer = player
    override val targetPlayer = player
    override val team = player.team
}
