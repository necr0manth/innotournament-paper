package dev.necr0manthre.innotournament.teams.events

import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.teams.PluginTeam

data class PlayerTeamEvent(
    val player: PluginPlayer,
    val team: PluginTeam?,
    var canceled: Boolean = false
)
