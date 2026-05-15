package dev.necr0manthre.innotournament.teams.invites

import dev.necr0manthre.innotournament.tournament.components.TournamentPlayer
import dev.necr0manthre.innotournament.tournament.components.TournamentTeam
import java.util.WeakHashMap

object InviteSystem {
    private val invitesByTeam = WeakHashMap<TournamentTeam, MutableSet<Invite>>()
    private val invitesByPlayer = WeakHashMap<TournamentPlayer, MutableSet<Invite>>()

    fun register() {
    }

    fun tick() {
        invitesByTeam.values.forEach { invites ->
            invites.removeIf { invite ->
                invite.tick()
                if (invite.isExpired) {
                    invitesByPlayer[invite.player]?.remove(invite)
                    true
                } else {
                    false
                }
            }
        }
    }

    fun isPlayerInvitedToTeam(player: TournamentPlayer, team: TournamentTeam): Boolean {
        return invitesByTeam[team]
            ?.any { it.player == player && !it.isExpired }
            ?: false
    }

    fun invitePlayerToTeam(player: TournamentPlayer, team: TournamentTeam, lifetime: Int) {
        if (isPlayerInvitedToTeam(player, team)) return

        val invite = Invite(team, player, lifetime)
        invitesByTeam.getOrPut(team) { HashSet() }.add(invite)
        invitesByPlayer.getOrPut(player) { HashSet() }.add(invite)
    }

    fun rejectInvite(player: TournamentPlayer, team: TournamentTeam) {
        val teamInvites = invitesByTeam[team] ?: return
        teamInvites.removeIf { invite ->
            if (invite.player == player) {
                invitesByPlayer[player]?.remove(invite)
                true
            } else {
                false
            }
        }
    }

    fun getInvitesForPlayer(player: TournamentPlayer): Set<Invite> {
        return invitesByPlayer[player]?.toSet() ?: emptySet()
    }

    fun getInvitesForTeam(team: TournamentTeam): Set<Invite> {
        return invitesByTeam[team]?.toSet() ?: emptySet()
    }
}
