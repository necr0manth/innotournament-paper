package dev.necr0manthre.innotournament.teams.invites

import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.teams.PluginTeam

class Invite(
    val team: PluginTeam,
    val player: PluginPlayer,
    private var lifetime: Int
) {
    fun tick() {
        if (lifetime > 0) lifetime--
    }

    val isExpired: Boolean
        get() = lifetime <= 0
}
