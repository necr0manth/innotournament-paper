package dev.necr0manthre.innotournament.teams.invites

import dev.dominion.ecs.api.Entity

class Invite(team: Entity?, player: Entity?, lifetime: Int) {
    val team: Entity?
    val player: Entity?

    @Getter
    private var lifetime: Int

    init {
        this.team = team
        this.player = player
        this.lifetime = lifetime
    }

    fun tick() {
        if (lifetime > 0) {
            lifetime--
        }
    }

    val isExpired: Boolean
        get() = lifetime <= 0
}
