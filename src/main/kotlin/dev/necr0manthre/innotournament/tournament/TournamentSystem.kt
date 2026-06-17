package dev.necr0manthre.innotournament.tournament

import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.teams.PluginTeam

object TournamentSystem {
    sealed interface PlayerTournamentState {
        val tournament: Tournament?
    }

    sealed interface TeamTournamentState {
        val tournament: Tournament?
    }

    internal class PlayerTournamentStateImpl : PlayerTournamentState {
        internal var tournamentPlayer: TournamentPlayer? = null

        override val tournament: Tournament?
            get() = tournamentPlayer?.tournament
    }

    internal class TeamTournamentStateImpl : TeamTournamentState {
        internal var tournamentTeam: TournamentTeam? = null

        override val tournament: Tournament?
            get() = tournamentTeam?.tournament
    }

    fun createPlayerState(): PlayerTournamentState = PlayerTournamentStateImpl()

    fun createTeamState(): TeamTournamentState = TeamTournamentStateImpl()

    internal fun setPlayerState(player: PluginPlayer, tournamentPlayer: TournamentPlayer) {
        (player.tournamentState as PlayerTournamentStateImpl).tournamentPlayer = tournamentPlayer
    }

    internal fun clearPlayerState(player: PluginPlayer, tournament: Tournament) {
        val state = player.tournamentState as PlayerTournamentStateImpl
        if (state.tournamentPlayer?.tournament === tournament) state.tournamentPlayer = null
    }

    internal fun setTeamState(team: PluginTeam, tournamentTeam: TournamentTeam) {
        (team.tournamentState as TeamTournamentStateImpl).tournamentTeam = tournamentTeam
    }

    internal fun clearTeamState(team: PluginTeam, tournament: Tournament) {
        val state = team.tournamentState as TeamTournamentStateImpl
        if (state.tournamentTeam?.tournament === tournament) state.tournamentTeam = null
    }
}
