package dev.necr0manthre.innotournament.teams.invites

import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.teams.PluginTeam

sealed interface Invite {
    val team: PluginTeam
    val player: PluginPlayer
    val expiresAtTick: Long
    val isExpired: Boolean
    val isValid: Boolean
}
