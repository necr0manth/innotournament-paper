package dev.necr0manthre.innotournament.tournament.events.event_data

import dev.dominion.ecs.api.Entity

@Data
class TeamAdvancement : ITeamProvider {
    private val team: Entity? = null
    private val advancement: AdvancementHolder? = null
}
