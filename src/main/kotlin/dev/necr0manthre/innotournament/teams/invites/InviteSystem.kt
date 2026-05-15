package dev.necr0manthre.innotournament.teams.invites

import dev.necr0manthre.innotournament.tournament.TournamentPlayer
import dev.necr0manthre.innotournament.tournament.TournamentTeam
import java.util.Collections

object InviteSystem {
    private val invites = mutableSetOf<Invite>()
    fun register() {
    }

    sealed interface Invites {
        val invites: Set<Invite>
    }

    private class InvitesImpl : Invites {
        val invitesInternal = mutableSetOf<Invite>()
        override val invites: Set<Invite> = Collections.unmodifiableSet(invitesInternal)
    }

    fun createInvites(): Invites = InvitesImpl()
    fun tick() {
        invites.removeIf { invite ->
            invite.tick()
            if (invite.isExpired) {
                invite.player.invites().remove(invite)
                invite.team.invites().remove(invite)
                true
            } else {
                false
            }
        }
    }

    fun isPlayerInvitedToTeam(player: TournamentPlayer, team: TournamentTeam): Boolean {
        return team.invites().any { it.player == player && !it.isExpired }
    }

    fun invitePlayerToTeam(player: TournamentPlayer, team: TournamentTeam, lifetime: Int) {
        if (isPlayerInvitedToTeam(player, team)) return

        val invite = Invite(team, player, lifetime)
        invites.add(invite)
        team.invites().add(invite)
        player.invites().add(invite)
    }

    fun rejectInvite(player: TournamentPlayer, team: TournamentTeam) {
        val teamInvites = team.invites()
        teamInvites.removeIf { invite ->
            if (invite.player == player) {
                player.invites().remove(invite)
                true
            } else {
                false
            }
        }
    }

    private fun TournamentPlayer.invites() = (invites as InvitesImpl).invitesInternal
    private fun TournamentTeam.invites() = (invites as InvitesImpl).invitesInternal
}

