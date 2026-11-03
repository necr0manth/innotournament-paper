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
    id: String,
    name: Component
) {
    sealed interface PlayerTeamState {
        val team: PluginTeam?
    }

    private class PlayerTeamStateImpl : PlayerTeamState {
        var value: PluginTeam? = null
        override val team: PluginTeam? get() = value
    }

    private val members = ArrayList<PluginPlayer>()
    private var ownerValue: PluginPlayer? = null
    private var ownerTransferInProgress = false
    private var renameInProgress = false
    private var removed = false
    private val isActive: Boolean get() = !removed && teams[id] === this

    var id: String = id
        private set
    var displayName: Component = name
        private set
    var acceptAll: Boolean = false
    val owner: PluginPlayer? get() = ownerValue
    val players: List<PluginPlayer>
        get() = immutableCopy(members)

    val invites = InviteSystem.createInvites()
    val tournamentState = Tournament.createTeamState()

    fun tryKickPlayerFromTeam(player: PluginPlayer): PlayerKickResult {
        beginMembershipOperation()
        if (!isActive) return PlayerKickResult.TEAM_REMOVED
        if (player.team !== this) return PlayerKickResult.NOT_IN_TEAM

        val before = PlayerTeamEvent(player, this, members.size)
        publishTeamEvent(beforeLeaveEvent, before)
        if (before.canceled) return PlayerKickResult.CANCELLED

        val sizeBefore = members.size
        check(members.remove(player)) { "Player's team and member list disagree" }
        state(player).value = null
        if (ownerValue === player) ownerValue = members.firstOrNull()
        publishTeamEvent(afterLeaveEvent, TeamChange(player, this, sizeBefore, members.size))
        return PlayerKickResult.SUCCESS
    }

    fun tryAddPlayerToTeam(player: PluginPlayer): PlayerJoinResult {
        beginMembershipOperation()
        if (!isActive) return PlayerJoinResult.TEAM_REMOVED
        if (player.team === this) return PlayerJoinResult.ALREADY_IN_TEAM

        // A -> B consists of a completed leave, its notification, then a separate join.
        // Cancellation or an exception during B does not undo the already completed leave.
        val previousTeam = player.team
        if (previousTeam != null) {
            val kickResult = previousTeam.tryKickPlayerFromTeam(player)
            if (!kickResult.isSuccess) return PlayerJoinResult.cannotKick(kickResult)
        }
        return joinWithoutLeaving(player)
    }

    private fun joinWithoutLeaving(player: PluginPlayer): PlayerJoinResult {
        check(player.team == null) { "Player must leave before joining another team" }
        val before = PlayerTeamEvent(player, this, members.size)
        publishTeamEvent(beforeJoinEvent, before)
        if (before.canceled) return PlayerJoinResult.CANCELLED

        val sizeBefore = members.size
        check(!members.contains(player)) { "Duplicate team member" }
        members.add(player)
        state(player).value = this
        if (ownerValue == null) ownerValue = player
        publishTeamEvent(afterJoinEvent, TeamChange(player, this, sizeBefore, members.size))
        return PlayerJoinResult.SUCCESS
    }

    /** Ownership can only be transferred to a current member. */
    fun transferOwnership(player: PluginPlayer): Boolean {
        check(!ownerTransferInProgress) { "Ownership cannot be transferred recursively" }
        if (!isActive || player.team !== this || !members.contains(player)) return false
        if (ownerValue === player) return true

        ownerTransferInProgress = true
        try {
            val before = TeamOwnerTransferEvent(this, ownerValue, player)
            publishTeamEvent(beforeOwnerTransferEvent, before)
            if (before.canceled) return false
            if (!isActive || player.team !== this || !members.contains(player)) return false
            if (ownerValue === player) return true

            val oldOwner = ownerValue
            ownerValue = player
            publishTeamEvent(afterOwnerTransferEvent, TeamOwnerChange(this, oldOwner, player))
            return true
        } finally {
            ownerTransferInProgress = false
        }
    }

    /** Rename changes the lookup key, not the identity of this team. */
    fun changeTeamName(name: Component): Boolean {
        check(!renameInProgress) { "Team cannot be renamed recursively" }
        if (!isActive) return false
        val newId = idFromDisplayName(name)
        if (newId != id && teams.containsKey(newId)) return false
        if (newId == id && name == displayName) return true

        renameInProgress = true
        try {
            val before = TeamRenameEvent(this, id, newId, displayName, name)
            publishTeamEvent(beforeRenameEvent, before)
            if (before.canceled) return false
            if (!isActive) return false
            if (newId != id && teams.containsKey(newId)) return false
            if (newId == id && name == displayName) return true

            val previousId = id
            val previousName = displayName
            if (newId != previousId) {
                // Preserve registry order across renames.
                val replacement = LinkedHashMap<String, PluginTeam>(teams.size)
                teams.forEach { (key, value) ->
                    replacement[if (value === this) newId else key] = value
                }
                teams.clear()
                teams.putAll(replacement)
                id = newId
            }
            displayName = name
            publishTeamEvent(afterRenameEvent, TeamRenameChange(this, previousId, id, previousName, name))
            return true
        } finally {
            renameInProgress = false
        }
    }

    companion object {
        private val teams = LinkedHashMap<String, PluginTeam>()
        private var initialized = false
        private var runtimeUsed = false
        private var teamEventDepth = 0

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

        fun createPlayerState(): PlayerTeamState = PlayerTeamStateImpl()

        private fun state(player: PluginPlayer): PlayerTeamStateImpl =
            player.teamState as PlayerTeamStateImpl

        private fun requireMembershipMutationAllowed() {
            check(teamEventDepth == 0) {
                "Membership cannot be changed from a team-event handler"
            }
        }

        private fun beginMembershipOperation() {
            requireMembershipMutationAllowed()
            runtimeUsed = true
        }

        private fun <T> publishTeamEvent(event: SimpleEvent<T>, value: T) {
            teamEventDepth++
            try {
                event.invoke(value)
            } finally {
                teamEventDepth--
            }
        }

        fun getTeamById(id: String): PluginTeam? = teams[id]
        fun getAllTeams(): List<PluginTeam> = immutableCopy(teams.values)

        fun createNewTeam(creator: PluginPlayer): TeamCreateResult {
            beginMembershipOperation()
            if (creator.team != null) return TeamCreateResult.ALREADY_IN_TEAM
            val base = creator.name.ifBlank { "team" }
            return createNewTeam(creator, uniqueId(base), Component.text(base))
        }

        fun createNewTeam(creator: PluginPlayer, id: String, displayName: Component): TeamCreateResult {
            beginMembershipOperation()
            require(id.isNotBlank()) { "Team id must not be blank" }
            if (creator.team != null) return TeamCreateResult.ALREADY_IN_TEAM
            if (teams.containsKey(id)) return TeamCreateResult.ID_ALREADY_EXISTS

            val beforeCreate = TeamCreateEvent(creator, id, displayName)
            publishTeamEvent(beforeCreateEvent, beforeCreate)
            if (beforeCreate.canceled) return TeamCreateResult.CANCELLED
            if (teams.containsKey(id)) return TeamCreateResult.ID_ALREADY_EXISTS

            val team = PluginTeam(id, displayName)
            val beforeJoin = PlayerTeamEvent(creator, team, 0)
            publishTeamEvent(beforeJoinEvent, beforeJoin)
            if (beforeJoin.canceled) return TeamCreateResult.CANCELLED
            if (teams.containsKey(id)) return TeamCreateResult.ID_ALREADY_EXISTS

            // Nothing has been published yet: both vetoes precede this single commit.
            team.members.add(creator)
            team.ownerValue = creator
            state(creator).value = team
            teams[id] = team
            publishTeamEvent(afterCreateEvent, TeamCreated(creator, team))
            publishTeamEvent(afterJoinEvent, TeamChange(creator, team, 0, 1))
            return TeamCreateResult.Created(team)
        }

        /** Domain removal: cancellation of either removal or any leave vetoes everything. */
        fun removeTeam(team: PluginTeam): Boolean {
            beginMembershipOperation()
            return removeTeamCommitted(team, ignoreCancellation = false)
        }

        /** Command removal ignores cancellation, but not exceptions or membership guards. */
        fun removeTeamFromCommand(team: PluginTeam): Boolean {
            beginMembershipOperation()
            return removeTeamCommitted(team, ignoreCancellation = true)
        }

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
            for (member in departing) state(member).value = null
            team.removed = true
            teams.remove(team.id)
            for ((index, member) in departing.withIndex()) {
                val sizeBefore = departing.size - index
                publishTeamEvent(afterLeaveEvent, TeamChange(member, team, sizeBefore, sizeBefore - 1))
            }
            publishTeamEvent(afterRemoveEvent, team)
            return true
        }

        /** Called once at boot, before any gameplay/command registration; not a runtime restore setter. */
        fun initialize(snapshot: TeamsSnapshot) {
            requireMembershipMutationAllowed()
            check(!initialized && !runtimeUsed && teams.isEmpty()) {
                "Teams have already been initialized or used"
            }
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
                val team = PluginTeam(saved.id, saved.displayName)
                team.acceptAll = saved.acceptAll
                team.members.addAll(saved.members.map { PluginPlayer.get(it) })
                team.ownerValue = team.members.firstOrNull { it.uuid == saved.ownerUuid }
                restored[team.id] = team
            }
            restored.values.forEach { team ->
                team.members.forEach { member ->
                    check(state(member).value == null) { "Player already belongs to a team: ${member.uuid}" }
                }
            }

            teams.putAll(restored)
            restored.values.forEach { team ->
                team.members.forEach { member -> state(member).value = team }
            }
            initialized = true
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
