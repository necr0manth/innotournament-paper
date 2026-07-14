package dev.necr0manthre.innotournament.teams

import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.teams.events.PlayerTeamEvent
import dev.necr0manthre.innotournament.teams.events.TeamEvents
import dev.necr0manthre.innotournament.teams.invites.InviteSystem
import dev.necr0manthre.innotournament.tournament.Tournament
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

class PluginTeam private constructor(
    id: String,
    displayName: Component
) {
    var id: String = id
        private set
    var owner: PluginPlayer? = null
    var acceptAll: Boolean = false
    var displayName: Component = displayName
        private set
    val players: MutableList<PluginPlayer> = mutableListOf()
    val invites = InviteSystem.createInvites()
    val tournamentState = Tournament.createTeamState()

    fun tryKickPlayerFromTeam(player: PluginPlayer): PlayerKickResult {
        if (player.team !== this) return PlayerKickResult.NOT_IN_TEAM

        val beforeEvent = PlayerTeamEvent(player, this)
        TeamEvents.emitBeforeLeave(beforeEvent)
        if (beforeEvent.canceled) return PlayerKickResult.CANCELLED

        players.remove(player)
        TeamSystem.clearPlayerTeam(player, this)
        if (owner === player) owner = players.firstOrNull()

        TeamEvents.emitAfterLeave(PlayerTeamEvent(player, this))
        return PlayerKickResult.SUCCESS
    }

    fun tryAddPlayerToTeam(player: PluginPlayer): PlayerJoinResult {
        if (player.team === this) return PlayerJoinResult.ALREADY_IN_TEAM

        val beforeEvent = PlayerTeamEvent(player, this)
        TeamEvents.emitBeforeJoin(beforeEvent)
        if (beforeEvent.canceled) return PlayerJoinResult.CANCELLED

        val oldTeam = player.team
        if (oldTeam != null) {
            val kickResult = oldTeam.tryKickPlayerFromTeam(player)
            if (!kickResult.isSuccess) return PlayerJoinResult.cannotKick(kickResult)
        }

        if (!players.contains(player)) players.add(player)
        TeamSystem.setPlayerTeam(player, this)
        if (owner == null) owner = player

        TeamEvents.emitAfterJoin(PlayerTeamEvent(player, this))
        return PlayerJoinResult.SUCCESS
    }

    fun changeTeamName(name: Component): Boolean {
        val newId = idFromDisplayName(name)
        if (newId != id && getTeamById(newId) != null) return false

        if (newId != id) {
            teams.remove(id)
            id = newId
            teams[id] = this
        }

        displayName = name
        return true
    }

    companion object {
        private val teams = LinkedHashMap<String, PluginTeam>()

        fun getAllTeams(): List<PluginTeam> {
            return teams.values.toList()
        }

        fun getTeamById(id: String): PluginTeam? {
            return teams[id]
        }

        fun addTeam(team: PluginTeam) {
            teams[team.id] = team
        }

        fun removeTeam(team: PluginTeam) {
            team.players.toList().forEach { team.tryKickPlayerFromTeam(it) }
            teams.remove(team.id)
        }

        fun createNewTeam(creator: PluginPlayer): PluginTeam {
            val base = creator.name.ifBlank { "team" }
            return createNewTeam(creator, uniqueId(base), Component.text(base))
        }

        fun createNewTeam(
            creator: PluginPlayer,
            id: String,
            displayName: Component
        ): PluginTeam {
            val team = PluginTeam(uniqueId(id), displayName)
            addTeam(team)
            team.tryAddPlayerToTeam(creator)
            team.owner = creator
            return team
        }

        private fun uniqueId(base: String): String {
            val normalizedBase = base.ifBlank { "team" }
            var id = normalizedBase
            var index = 1
            while (teams.containsKey(id)) {
                id = "${normalizedBase}_${index++}"
            }
            return id
        }

        private fun idFromDisplayName(displayName: Component): String {
            return PlainTextComponentSerializer.plainText().serialize(displayName)
                .ifBlank { "team" }
        }
    }
}
