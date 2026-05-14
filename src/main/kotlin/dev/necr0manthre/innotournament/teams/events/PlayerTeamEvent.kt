package dev.necr0manthre.innotournament.teams.events

import dev.dominion.ecs.api.Entity

@RequiredArgsConstructor
class PlayerTeamEvent {
    val player: Entity? = null
    val team: Entity? = null

    @Getter
    @Setter
    private val canceled = false
}
