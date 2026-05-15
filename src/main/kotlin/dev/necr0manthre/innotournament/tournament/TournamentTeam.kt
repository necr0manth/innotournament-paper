package dev.necr0manthre.innotournament.tournament

import dev.necr0manthre.innotournament.teams.PlayerJoinResult
import dev.necr0manthre.innotournament.teams.PlayerKickResult
import dev.necr0manthre.innotournament.teams.invites.InviteSystem
import dev.necr0manthre.innotournament.teams.invites.Invites
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.advancement.Advancement
import org.bukkit.advancement.AdvancementProgress
import java.lang.ref.WeakReference

class TournamentTeam private constructor(
    id: String,
    displayName: Component
) {
    var id: String = id
        private set
    var owner: TournamentPlayer? = null
    var acceptAll: Boolean = false
    var tournament: WeakReference<Tournament>? = null
    var displayName: Component = displayName
        private set
    val players: MutableList<TournamentPlayer> = mutableListOf()
    val advancements: MutableMap<Advancement, AdvancementProgress> = mutableMapOf()
    val invites = InviteSystem.createInvites()

    fun tryKickPlayerFromTeam(player: TournamentPlayer): PlayerKickResult {
        if (player.team !== this) return PlayerKickResult.NOT_IN_TEAM

        players.remove(player)
        player.team = null
        if (owner === player) owner = players.firstOrNull()

        return PlayerKickResult.SUCCESS
    }

    fun tryAddPlayerToTeam(player: TournamentPlayer): PlayerJoinResult {
        if (player.team === this) return PlayerJoinResult.ALREADY_IN_TEAM

        val oldTeam = player.team
        if (oldTeam != null) {
            val kickResult = oldTeam.tryKickPlayerFromTeam(player)
            if (!kickResult.isSuccess) return PlayerJoinResult.cannotKick(kickResult)
        }

        if (!players.contains(player)) players.add(player)
        player.team = this
        if (owner == null) owner = player

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
        private val teams = LinkedHashMap<String, TournamentTeam>()

        fun getAllTeams(): List<TournamentTeam> {
            return teams.values.toList()
        }

        fun getTeamById(id: String): TournamentTeam? {
            return teams[id]
        }

        fun addTeam(team: TournamentTeam) {
            teams[team.id] = team
        }

        fun removeTeam(team: TournamentTeam) {
            team.players.toList().forEach { team.tryKickPlayerFromTeam(it) }
            teams.remove(team.id)
        }

        fun createNewTeam(creator: TournamentPlayer): TournamentTeam {
            val base = creator.name.ifBlank { "team" }
            return createNewTeam(creator, uniqueId(base), Component.text(base))
        }

        fun createNewTeam(
            creator: TournamentPlayer,
            id: String,
            displayName: Component
        ): TournamentTeam {
            val team = TournamentTeam(uniqueId(id), displayName)
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
