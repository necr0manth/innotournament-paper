package dev.necr0manthre.innotournament.teams

import dev.necr0manthre.innotournament.players.PluginPlayer

object TeamSystem {
    sealed interface PlayerTeamState {
        val team: PluginTeam?
    }

    internal class PlayerTeamStateImpl : PlayerTeamState {
        internal var teamInternal: PluginTeam? = null

        override val team: PluginTeam?
            get() = teamInternal
    }

    fun createPlayerState(): PlayerTeamState = PlayerTeamStateImpl()

    internal fun setPlayerTeam(player: PluginPlayer, team: PluginTeam) {
        (player.teamState as PlayerTeamStateImpl).teamInternal = team
    }

    internal fun clearPlayerTeam(player: PluginPlayer, team: PluginTeam) {
        val state = player.teamState as PlayerTeamStateImpl
        if (state.teamInternal === team) state.teamInternal = null
    }
}
