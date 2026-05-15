package dev.necr0manthre.innotournament.teams.invites

import dev.necr0manthre.innotournament.tournament.components.TournamentPlayer
import dev.necr0manthre.innotournament.tournament.components.TournamentTeam

class Invite(
    val team: TournamentTeam,
    val player: TournamentPlayer,
    private var lifetime: Int
) {
    fun tick() {
        if (lifetime > 0) lifetime--
    }

    val isExpired: Boolean
        get() = lifetime <= 0
}
