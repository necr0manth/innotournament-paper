package dev.necr0manthre.innotournament.teams.invites

import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.teams.PluginTeam
import dev.necr0manthre.innotournament.teams.PluginTeam.TeamManager
import dev.necr0manthre.innotournament.tournament.events.SimpleEvent
import dev.necr0manthre.innotournament.tournament.events.asSubscriptionView
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.util.Collections
import java.util.UUID

/** Server-thread invitation state, independent of any tournament's lifetime. */
class InviteSystem(private val teamManager: TeamManager) : AutoCloseable {
    private val invites = LinkedHashMap<Pair<PluginTeam, UUID>, Invite>()
    private var currentTick = 0L
    private var task: BukkitTask? = null
    private var closed = false
    private val issuedEvent = SimpleEvent<Invite>()
    private val expiredEvent = SimpleEvent<Invite>()
    val issued = issuedEvent.asSubscriptionView()
    val expired = expiredEvent.asSubscriptionView()

    private val teamRemoved: (PluginTeam) -> Unit = { team ->
        invites.values.filter { it.team === team }.forEach(::remove)
    }

    init {
        teamManager.afterRemove.subscribe(teamRemoved)
    }

    sealed interface Invites {
        val invites: Set<Invite>
    }

    private class InvitesImpl : Invites {
        val entries = LinkedHashSet<Invite>()
        override val invites: Set<Invite>
            get() = Collections.unmodifiableSet(entries.filterTo(LinkedHashSet()) { it.isValid })
    }

    private inner class Entry(
        override val team: PluginTeam,
        override val player: PluginPlayer,
        override val expiresAtTick: Long
    ) : Invite {
        override val isExpired: Boolean get() = currentTick >= expiresAtTick
        override val isValid: Boolean
            get() = !isExpired && active(team) && invites[team to player.uuid] === this
    }

    fun getInvite(player: PluginPlayer, team: PluginTeam): Invite? =
        invites[team to player.uuid]?.takeIf { it.isValid }

    fun isPlayerInvitedToTeam(player: PluginPlayer, team: PluginTeam): Boolean = getInvite(player, team) != null

    /** An active duplicate keeps its original deadline and emits no second issued event. */
    fun invitePlayerToTeam(player: PluginPlayer, team: PluginTeam, lifetime: Int): Invite? {
        check(!closed) { "Invitation system is closed" }
        require(lifetime > 0) { "Invitation lifetime must be positive" }
        if (!active(team) || player.team === team) return null
        getInvite(player, team)?.let { return it }
        val deadline = Math.addExact(currentTick, lifetime.toLong())
        invites[team to player.uuid]?.let(::remove)
        val invite = Entry(team, player, deadline)
        invites[team to player.uuid] = invite
        team.entries().add(invite)
        player.entries().add(invite)
        issuedEvent.invoke(invite)
        return invite
    }

    fun revokeInvite(player: PluginPlayer, team: PluginTeam): Boolean =
        invites[team to player.uuid]?.let(::remove) ?: false

    fun rejectInvite(player: PluginPlayer, team: PluginTeam): Boolean = revokeInvite(player, team)

    /** Admission and consumption surround the ordinary structural operation, never replace it. */
    fun requestJoin(player: PluginPlayer, team: PluginTeam): Boolean {
        check(!closed) { "Invitation system is closed" }
        if (!active(team) || player.team === team) return false
        val invite = getInvite(player, team)
        if (!team.acceptAll && invite == null) return false
        if (!team.tryAddPlayerToTeam(player).isSuccess) return false
        invite?.let(::remove)
        return true
    }

    fun tick() {
        check(!closed) { "Invitation system is closed" }
        currentTick = Math.addExact(currentTick, 1L)
        for (invite in invites.values.toList()) {
            if (invite.isExpired && remove(invite)) expiredEvent.invoke(invite)
        }
    }

    /** CORE starts this once on enable and closes it on disable, not on tournament removal. */
    fun register(plugin: Plugin) {
        check(!closed) { "Invitation system is closed" }
        check(task == null) { "Invitation task is already running" }
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable(::tick), 1L, 1L)
    }

    override fun close() {
        if (closed) return
        closed = true
        task?.cancel()
        task = null
        teamManager.afterRemove.unsubscribe(teamRemoved)
        invites.values.toList().forEach(::remove)
        issuedEvent.close()
        expiredEvent.close()
    }

    private fun active(team: PluginTeam): Boolean = teamManager.getTeamById(team.id) === team

    private fun remove(invite: Invite): Boolean {
        val key = invite.team to invite.player.uuid
        if (invites[key] !== invite) return false
        invites.remove(key)
        invite.team.entries().remove(invite)
        invite.player.entries().remove(invite)
        return true
    }

    companion object {
        fun createInvites(): Invites = InvitesImpl()
        private fun PluginPlayer.entries() = (invites as InvitesImpl).entries
        private fun PluginTeam.entries() = (invites as InvitesImpl).entries
    }
}
