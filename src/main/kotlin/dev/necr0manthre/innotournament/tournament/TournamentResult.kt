package dev.necr0manthre.innotournament.tournament

import net.kyori.adventure.text.Component
import java.util.Collections
import java.util.UUID

/**
 * Detached result of a normally completed run. The tournament supplies final scores,
 * ranks, winners and display order; this value does not calculate any of them.
 * External cancellation/removal does not create a TournamentResult.
 */
class TournamentResult(
    teams: List<Team>,
    winnerTeamIds: Set<String>,
    val lastAliveTeamId: String?
) {
    enum class Reason { NORMAL }

    val reason: Reason = Reason.NORMAL

    /** Eligible teams in their final display order; removed teams have no rank. */
    val teams: List<Team> = Collections.unmodifiableList(ArrayList(teams))
    val winnerTeamIds: Set<String> = Collections.unmodifiableSet(LinkedHashSet(winnerTeamIds))

    private val finalTeamByPlayer: Map<UUID, Team> = run {
        val byPlayer = LinkedHashMap<UUID, Team>()
        for (team in this.teams) {
            for (player in team.players) {
                require(byPlayer.putIfAbsent(player.uuid, team) == null) {
                    "Player ${player.uuid} appears in multiple final team rosters"
                }
            }
        }
        Collections.unmodifiableMap(byPlayer)
    }

    /** Uses only membership captured at the finish, never the player's live team. */
    fun teamForPlayer(uuid: UUID): Team? = finalTeamByPlayer[uuid]

    /** A team's identity, exact score, rank and final ordered roster. */
    class Team(
        val id: String,
        val displayName: Component,
        val score: Double,
        val rank: Int,
        players: List<Player>
    ) {
        val players: List<Player> = Collections.unmodifiableList(ArrayList(players))
    }

    /** A final roster entry; null means the name was unknown at the finish. */
    data class Player(val uuid: UUID, val savedName: String?)
}
