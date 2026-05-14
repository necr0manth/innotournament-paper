package dev.necr0manthre.innotournament.teams.invites

import dev.necr0manthre.innotournament.tournament.components.TournamentPlayer
import dev.necr0manthre.innotournament.tournament.components.TournamentTeam


object InviteSystem {
    val INVITE_EVENT: Event<java.util.function.Consumer<Invite?>?> = EventFactory.createLoop()
    val INVITE_EXPIRATION_EVENT: Event<java.util.function.Consumer<Invite?>?> = EventFactory.createLoop()

    fun register() {
        TickEvent.SERVER_POST.register({ obj: InviteSystem?, server: MinecraftServer? -> tick(server) })
    }

    fun tick(server: MinecraftServer?) {
        val teamManager: TeamManager = TeamManager.Companion.get(server)
        teamManager.getAllTeams().forEach(java.util.function.Consumer { team: Entity? ->
            val invites: Invites = Invites.getComponent(team)
            synchronized(invites.invites) {
                invites.invites.removeIf { invite: Invite? ->
                    invite!!.tick()
                    if (invite.isExpired()) {
                        INVITE_EXPIRATION_EVENT.invoker().accept(invite)
                        Invites.getComponent(invite.player).invites.remove(invite)
                        return@removeIf true
                    }
                    false
                }
            }
        })
    }

    fun isPlayerInvitedToTeam(player: TournamentPlayer, team: TournamentTeam): Boolean {
        val invites: Invites = Invites.getComponent(team)
        synchronized(invites.invites) {
            return invites.invites.stream()
                .anyMatch { invite: Invite? -> invite!!.player.equals(player) && !invite.isExpired() }
        }
    }

    fun invitePlayerToTeam(player: Entity, team: Entity, lifetime: Int) {
        if (isPlayerInvitedToTeam(player, team)) return
        val invite = Invite(team, player, lifetime)
        Invites.getComponent(team).invites.add(invite)
        Invites.getComponent(player).invites.add(invite)
        INVITE_EVENT.invoker().accept(invite)
    }

    fun rejectInvite(player: Entity, team: Entity) {
        if (!isPlayerInvitedToTeam(player, team)) return
        val invites: Invites = Invites.getComponent(team)
        synchronized(invites.invites) {
            invites.invites.removeIf { invite: Invite? ->
                if (invite!!.player.equals(player)) {
                    Invites.getComponent(player).invites.remove(invite)
                    return@removeIf true
                }
                false
            }
        }
    }

    fun getInvitesForPlayer(player: Entity): MutableSet<Invite?> {
        return java.util.Set.copyOf<Invite?>(Invites.getComponent(player).invites)
    }

    fun getInvitesForTeam(team: Entity): MutableSet<Invite?> {
        return java.util.Set.copyOf<Invite?>(Invites.getComponent(team).invites)
    }
}
