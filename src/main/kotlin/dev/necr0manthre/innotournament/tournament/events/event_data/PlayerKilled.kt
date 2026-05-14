package dev.necr0manthre.innotournament.tournament.events.event_data

import dev.dominion.ecs.api.Entity

@Data
class PlayerKilled : ISourcePlayerProvider, ITargetPlayerProvider, ITeamProvider {
    private val sourcePlayer: Entity? = null
    private val targetPlayer: Entity? = null

    override fun getSourcePlayer(): Entity {
        return sourcePlayer
    }

    override fun getTargetPlayer(): Entity {
        return targetPlayer
    }

    override fun getTeam(): Entity? {
        return TeamManager.Companion.getPlayersTeam(getSourcePlayer()).orElse(null)
    }
}
