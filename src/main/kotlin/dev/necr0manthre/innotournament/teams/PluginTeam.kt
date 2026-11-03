package dev.necr0manthre.innotournament.teams

import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.teams.events.*
import dev.necr0manthre.innotournament.teams.invites.InviteSystem
import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.events.EventSubscriptionView
import dev.necr0manthre.innotournament.tournament.events.SimpleEvent
import dev.necr0manthre.innotournament.tournament.events.asSubscriptionView
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.util.Collections
import java.util.UUID

/** Permanent team membership and its reverse player link have one owner: PluginTeam. */
class PluginTeam private constructor(
    private val manager: TeamManager,
    id: String,
    name: Component
) {
    sealed interface PlayerTeamState { val team: PluginTeam? }
    private class PlayerTeamStateImpl : PlayerTeamState {
        var value: PluginTeam? = null
        var changing: Boolean = false
        override val team: PluginTeam? get() = value
    }

    private val members = ArrayList<PluginPlayer>()
    private var ownerValue: PluginPlayer? = null
    private var changing = false
    private var removed = false
    private val isActive: Boolean get() = !removed && manager.getTeamById(id) === this

    var id: String = id
        private set
    var displayName: Component = name
        private set
    var acceptAll: Boolean = false
        set(value) {
            check(!changing) { "Team cannot be changed from a team-event handler" }
            field = value
        }
    val owner: PluginPlayer? get() = ownerValue
    val players: List<PluginPlayer> get() = Collections.unmodifiableList(ArrayList(members))
    val invites = InviteSystem.createInvites()
    val tournamentState = Tournament.createTeamState()

    fun tryKickPlayerFromTeam(player: PluginPlayer) = manager.tryKickPlayerFromTeam(this, player)
    fun tryAddPlayerToTeam(player: PluginPlayer) = manager.tryAddPlayerToTeam(this, player)
    fun transferOwnership(player: PluginPlayer) = manager.transferOwnership(this, player)
    fun changeTeamName(name: Component) = manager.changeTeamName(this, name)

    companion object {
        fun createPlayerState(): PlayerTeamState = PlayerTeamStateImpl()
        private fun state(player: PluginPlayer): PlayerTeamStateImpl = player.teamState as PlayerTeamStateImpl
        private fun isChanging(player: PluginPlayer): Boolean = state(player).changing
        private fun reserve(player: PluginPlayer) {
            check(!state(player).changing) { "Player cannot be changed from a team-event handler" }
            state(player).changing = true
        }
        private fun release(player: PluginPlayer) { state(player).changing = false }
    }

class TeamManager(snapshot: TeamsSnapshot? = null) {
        private val teams = LinkedHashMap<String, PluginTeam>()
        val events = TeamEvents(this)

        private val beforeJoinEvent = SimpleEvent<PlayerTeamEvent>()
        private val afterJoinEvent = SimpleEvent<TeamChange>()
        private val beforeLeaveEvent = SimpleEvent<PlayerTeamEvent>()
        private val afterLeaveEvent = SimpleEvent<TeamChange>()
        private val beforeCreateEvent = SimpleEvent<TeamCreateEvent>()
        private val afterCreateEvent = SimpleEvent<TeamCreated>()
        private val beforeRemoveEvent = SimpleEvent<TeamRemoveEvent>()
        private val afterRemoveEvent = SimpleEvent<PluginTeam>()
        private val beforeRenameEvent = SimpleEvent<TeamRenameEvent>()
        private val afterRenameEvent = SimpleEvent<TeamRenameChange>()
        private val beforeOwnerTransferEvent = SimpleEvent<TeamOwnerTransferEvent>()
        private val afterOwnerTransferEvent = SimpleEvent<TeamOwnerChange>()

        val beforeJoin: EventSubscriptionView<PlayerTeamEvent> = beforeJoinEvent.asSubscriptionView()
        val afterJoin: EventSubscriptionView<TeamChange> = afterJoinEvent.asSubscriptionView()
        val beforeLeave: EventSubscriptionView<PlayerTeamEvent> = beforeLeaveEvent.asSubscriptionView()
        val afterLeave: EventSubscriptionView<TeamChange> = afterLeaveEvent.asSubscriptionView()
        val beforeCreate: EventSubscriptionView<TeamCreateEvent> = beforeCreateEvent.asSubscriptionView()
        val afterCreate: EventSubscriptionView<TeamCreated> = afterCreateEvent.asSubscriptionView()
        val beforeRemove: EventSubscriptionView<TeamRemoveEvent> = beforeRemoveEvent.asSubscriptionView()
        val afterRemove: EventSubscriptionView<PluginTeam> = afterRemoveEvent.asSubscriptionView()
        val beforeRename: EventSubscriptionView<TeamRenameEvent> = beforeRenameEvent.asSubscriptionView()
        val afterRename: EventSubscriptionView<TeamRenameChange> = afterRenameEvent.asSubscriptionView()
        val beforeOwnerTransfer: EventSubscriptionView<TeamOwnerTransferEvent> = beforeOwnerTransferEvent.asSubscriptionView()
        val afterOwnerTransfer: EventSubscriptionView<TeamOwnerChange> = afterOwnerTransferEvent.asSubscriptionView()

        private fun <T> withChange(affectedTeams: Collection<PluginTeam>, affectedPlayers: Collection<PluginPlayer>, action: () -> T): T {
            val teamsToLock = affectedTeams.distinct()
            val playersToLock = affectedPlayers.toSet()
            check(teamsToLock.none { it.changing } && playersToLock.none(PluginTeam::isChanging)) {
                "Team or player cannot be changed from a team-event handler"
            }
            teamsToLock.forEach { it.changing = true }
            playersToLock.forEach(PluginTeam::reserve)
            try { return action() } finally {
                teamsToLock.forEach { it.changing = false }
                playersToLock.forEach(PluginTeam::release)
            }
        }

        private fun <T> publishTeamEvent(event: SimpleEvent<T>, value: T) {
            event.invoke(value)
        }

        fun getTeamById(id: String): PluginTeam? = teams[id]
        fun getAllTeams(): List<PluginTeam> = immutableCopy(teams.values)

        fun tryKickPlayerFromTeam(team: PluginTeam, player: PluginPlayer): PlayerKickResult =
            if (team.manager !== this) PlayerKickResult.TEAM_REMOVED
            else withChange(listOf(team), listOf(player)) { kickWithoutLock(team, player) }

        private fun kickWithoutLock(team: PluginTeam, player: PluginPlayer): PlayerKickResult {
            if (!team.isActive) return PlayerKickResult.TEAM_REMOVED
            if (player.team !== team) return PlayerKickResult.NOT_IN_TEAM
            val before = PlayerTeamEvent(player, team, team.members.size)
            publishTeamEvent(beforeLeaveEvent, before)
            if (before.canceled) return PlayerKickResult.CANCELLED
            val sizeBefore = team.members.size
            check(team.members.remove(player)) { "Player's team and member list disagree" }
            PluginTeam.state(player).value = null
            if (team.ownerValue === player) team.ownerValue = team.members.firstOrNull()
            publishTeamEvent(afterLeaveEvent, TeamChange(player, team, sizeBefore, team.members.size))
            return PlayerKickResult.SUCCESS
        }

        fun tryAddPlayerToTeam(team: PluginTeam, player: PluginPlayer): PlayerJoinResult {
            if (team.manager !== this) return PlayerJoinResult.TEAM_REMOVED
            val previous = player.team
            return withChange(listOfNotNull(previous, team), listOf(player)) {
                if (!team.isActive) return@withChange PlayerJoinResult.TEAM_REMOVED
                if (previous === team) return@withChange PlayerJoinResult.ALREADY_IN_TEAM
                if (previous != null) {
                    val leave = previous.manager.kickWithoutLock(previous, player)
                    if (!leave.isSuccess) return@withChange PlayerJoinResult.cannotKick(leave)
                }
                joinWithoutLock(team, player)
            }
        }

        private fun joinWithoutLock(team: PluginTeam, player: PluginPlayer): PlayerJoinResult {
            check(player.team == null) { "Player must leave before joining another team" }
            val before = PlayerTeamEvent(player, team, team.members.size)
            publishTeamEvent(beforeJoinEvent, before)
            if (before.canceled) return PlayerJoinResult.CANCELLED
            val sizeBefore = team.members.size
            check(!team.members.contains(player)) { "Duplicate team member" }
            team.members.add(player)
            PluginTeam.state(player).value = team
            if (team.ownerValue == null) team.ownerValue = player
            publishTeamEvent(afterJoinEvent, TeamChange(player, team, sizeBefore, team.members.size))
            return PlayerJoinResult.SUCCESS
        }

        fun transferOwnership(team: PluginTeam, player: PluginPlayer): Boolean =
            if (team.manager !== this) false else withChange(listOf(team), listOf(player)) {
                if (!team.isActive || player.team !== team || !team.members.contains(player)) return@withChange false
                if (team.ownerValue === player) return@withChange true
                val before = TeamOwnerTransferEvent(team, team.ownerValue, player)
                publishTeamEvent(beforeOwnerTransferEvent, before)
                if (before.canceled || !team.isActive || player.team !== team || !team.members.contains(player))
                    return@withChange false
                if (team.ownerValue === player) return@withChange true
                val oldOwner = team.ownerValue
                team.ownerValue = player
                publishTeamEvent(afterOwnerTransferEvent, TeamOwnerChange(team, oldOwner, player))
                true
            }

        fun changeTeamName(team: PluginTeam, name: Component): Boolean =
            if (team.manager !== this) false else withChange(listOf(team), emptyList()) {
                if (!team.isActive) return@withChange false
                val newId = idFromDisplayName(name)
                if (newId != team.id && teams.containsKey(newId)) return@withChange false
                if (newId == team.id && name == team.displayName) return@withChange true
                val before = TeamRenameEvent(team, team.id, newId, team.displayName, name)
                publishTeamEvent(beforeRenameEvent, before)
                if (before.canceled || !team.isActive) return@withChange false
                if (newId != team.id && teams.containsKey(newId)) return@withChange false
                if (newId == team.id && name == team.displayName) return@withChange true
                val oldId = team.id
                val oldName = team.displayName
                if (newId != oldId) {
                    val replacement = LinkedHashMap<String, PluginTeam>(teams.size)
                    teams.forEach { (key, value) -> replacement[if (value === team) newId else key] = value }
                    teams.clear()
                    teams.putAll(replacement)
                    team.id = newId
                }
                team.displayName = name
                publishTeamEvent(afterRenameEvent, TeamRenameChange(team, oldId, team.id, oldName, name))
                true
            }

        fun createNewTeam(creator: PluginPlayer): TeamCreateResult {
            check(!PluginTeam.isChanging(creator) && creator.team?.changing != true) {
                "Player cannot be changed from a team-event handler"
            }
            if (creator.team != null) return TeamCreateResult.ALREADY_IN_TEAM
            val base = creator.name.ifBlank { "team" }
            return createNewTeam(creator, uniqueId(base), Component.text(base))
        }

        fun createNewTeam(creator: PluginPlayer, id: String, displayName: Component): TeamCreateResult =
            withChange(emptyList(), listOf(creator)) {
            require(id.isNotBlank()) { "Team id must not be blank" }
            if (creator.team != null) return@withChange TeamCreateResult.ALREADY_IN_TEAM
            if (teams.containsKey(id)) return@withChange TeamCreateResult.ID_ALREADY_EXISTS

            val beforeCreate = TeamCreateEvent(creator, id, displayName)
            publishTeamEvent(beforeCreateEvent, beforeCreate)
            if (beforeCreate.canceled) return@withChange TeamCreateResult.CANCELLED
            if (teams.containsKey(id)) return@withChange TeamCreateResult.ID_ALREADY_EXISTS

            val team = PluginTeam(this, id, displayName)
            try {
            team.changing = true
            val beforeJoin = PlayerTeamEvent(creator, team, 0)
            publishTeamEvent(beforeJoinEvent, beforeJoin)
            if (beforeJoin.canceled) return@withChange TeamCreateResult.CANCELLED
            if (teams.containsKey(id)) return@withChange TeamCreateResult.ID_ALREADY_EXISTS

            // Nothing has been published yet: both vetoes precede this single commit.
            team.members.add(creator)
            team.ownerValue = creator
            PluginTeam.state(creator).value = team
            teams[id] = team
            publishTeamEvent(afterCreateEvent, TeamCreated(creator, team))
            publishTeamEvent(afterJoinEvent, TeamChange(creator, team, 0, 1))
            TeamCreateResult.Created(team)
            } finally {
                team.changing = false
            }
        }

        /** Domain removal: cancellation of either removal or any leave vetoes everything. */
        fun removeTeam(team: PluginTeam): Boolean {
            return removeTeamLocked(team, ignoreCancellation = false)
        }

        /** Command removal ignores cancellation, but not exceptions or membership guards. */
        fun removeTeamFromCommand(team: PluginTeam): Boolean {
            return removeTeamLocked(team, ignoreCancellation = true)
        }

        private fun removeTeamLocked(team: PluginTeam, ignoreCancellation: Boolean): Boolean =
            if (team.manager !== this) false
            else withChange(listOf(team), team.members) { removeTeamCommitted(team, ignoreCancellation) }

        private fun removeTeamCommitted(team: PluginTeam, ignoreCancellation: Boolean): Boolean {
            if (team.removed || teams[team.id] !== team) return false
            val beforeRemove = TeamRemoveEvent(team)
            publishTeamEvent(beforeRemoveEvent, beforeRemove)
            if (beforeRemove.canceled && !ignoreCancellation) return false

            val departing = team.members.toList()
            for (member in departing) {
                val beforeLeave = PlayerTeamEvent(member, team, departing.size)
                publishTeamEvent(beforeLeaveEvent, beforeLeave)
                if (beforeLeave.canceled && !ignoreCancellation) return false
            }

            // Complete all reverse links and registry changes before the first after-event.
            team.members.clear()
            team.ownerValue = null
            for (member in departing) PluginTeam.state(member).value = null
            team.removed = true
            teams.remove(team.id)
            for ((index, member) in departing.withIndex()) {
                val sizeBefore = departing.size - index
                publishTeamEvent(afterLeaveEvent, TeamChange(member, team, sizeBefore, sizeBefore - 1))
            }
            publishTeamEvent(afterRemoveEvent, team)
            return true
        }

        /** A snapshot is decoded before a new manager is constructed. */
        init { if (snapshot != null) restore(snapshot) }

        private fun restore(snapshot: TeamsSnapshot) {
            require(snapshot.version == 1) { "Unsupported teams snapshot version: ${snapshot.version}" }

            // Validate the complete input before creating canonical player objects.
            val ids = HashSet<String>()
            val claimed = HashSet<UUID>()
            for (saved in snapshot.teams) {
                require(saved.id.isNotBlank()) { "Team id must not be blank" }
                require(ids.add(saved.id)) { "Duplicate team id: ${saved.id}" }
                for (uuid in saved.members) {
                    require(claimed.add(uuid)) { "Player belongs to multiple teams or occurs twice: $uuid" }
                }
                require(
                    if (saved.members.isEmpty()) saved.ownerUuid == null
                    else saved.ownerUuid != null && saved.members.contains(saved.ownerUuid)
                ) { "Team owner is inconsistent with membership: ${saved.id}" }
            }

            val restored = LinkedHashMap<String, PluginTeam>()
            for (saved in snapshot.teams) {
                val team = PluginTeam(this, saved.id, saved.displayName)
                team.acceptAll = saved.acceptAll
                team.members.addAll(saved.members.map { PluginPlayer.get(it) })
                team.ownerValue = team.members.firstOrNull { it.uuid == saved.ownerUuid }
                restored[team.id] = team
            }
            restored.values.forEach { team ->
                team.members.forEach { member ->
                    check(!PluginTeam.isChanging(member)) { "Player is being changed: ${member.uuid}" }
                    check(PluginTeam.state(member).value == null) { "Player already belongs to a team: ${member.uuid}" }
                }
            }

            teams.putAll(restored)
            restored.values.forEach { team ->
                team.members.forEach { member -> PluginTeam.state(member).value = team }
            }
        }

        fun snapshot(): TeamsSnapshot = TeamsSnapshot(1, teams.values.map { team ->
            TeamSnapshot(
                team.id,
                team.displayName,
                team.acceptAll,
                team.members.map { it.uuid },
                team.ownerValue?.uuid
            )
        })

        private fun uniqueId(base: String): String {
            val normalizedBase = base.ifBlank { "team" }
            var id = normalizedBase
            var index = 1
            while (teams.containsKey(id)) id = "${normalizedBase}_${index++}"
            return id
        }

        private fun idFromDisplayName(displayName: Component): String =
            PlainTextComponentSerializer.plainText().serialize(displayName).ifBlank { "team" }

        private fun <T> immutableCopy(values: Collection<T>): List<T> =
            Collections.unmodifiableList(ArrayList(values))
}
}

typealias TeamManager = PluginTeam.TeamManager
