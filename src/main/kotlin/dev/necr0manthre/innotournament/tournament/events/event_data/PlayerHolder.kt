package dev.necr0manthre.innotournament.tournament.events.event_data

import dev.dominion.ecs.api.Entity

@Data
class PlayerHolder : ISourcePlayerProvider, ITargetPlayerProvider, ITeamProvider {
    private val player: Entity? = null

    override fun getSourcePlayer(): Entity {
        return player
    }

    override fun getTargetPlayer(): Entity {
        return player
    }

    override fun getTeam(): Entity? {
        return TeamManager.Companion.getPlayersTeam(player).orElse(null)
    }
}
