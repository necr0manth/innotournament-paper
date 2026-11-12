package dev.necr0manthre.innotournament.tournament

import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.teams.PluginTeam.TeamManager
import dev.necr0manthre.innotournament.teams.TeamCreateResult
import dev.necr0manthre.innotournament.tournament.events.EventPlan
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.OfflinePlayer
import org.bukkit.Server
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.UUID

class TournamentStandingsTest {
    @Test
    fun snapshotsActiveTeamsWithCompetitionRanksAndOrderedRosters() {
        val serverField = Bukkit::class.java.getDeclaredField("server").apply { isAccessible = true }
        val previousServer = serverField.get(null)
        serverField.set(null, serverProxy())
        try {
            val manager = TeamManager()
            fun createTeam(id: String, owner: PluginPlayer) =
                (manager.createNewTeam(owner, id, Component.text(id)) as TeamCreateResult.Created).team

            val beta = createTeam("beta", PluginPlayer.get(UUID(0, 21)))
            val alphaOwner = PluginPlayer.get(UUID(0, 11))
            val alphaMember = PluginPlayer.get(UUID(0, 12))
            val alpha = createTeam("alpha", alphaOwner)
            check(alpha.tryAddPlayerToTeam(alphaMember).isSuccess)
            val empty = createTeam("empty", PluginPlayer.get(UUID(0, 31)))
            check(empty.tryKickPlayerFromTeam(empty.players.single()).isSuccess)
            val deleted = createTeam("deleted", PluginPlayer.get(UUID(0, 41)))

            val tournament = testTournament(manager)
            Tournament::class.java.getDeclaredField("phase").apply { isAccessible = true }
                .set(tournament, TournamentPhase.STARTED)
            listOf(beta, alpha, empty, deleted).forEach(tournament::checkTeam)
            tournament.addScores(tournament.getTeam(beta), 10.0)
            tournament.addScores(tournament.getTeam(alpha), 10.0)
            tournament.addScores(tournament.getTeam(empty), 4.0)
            tournament.addScores(tournament.getTeam(deleted), 100.0)
            check(manager.removeTeam(deleted))

            val alphaParticipation = tournament.getTeam(alpha)
            val rows = tournament.standings()

            assertEquals(listOf("alpha", "beta", "empty"), rows.map { it.id })
            assertEquals(listOf(1, 1, 3), rows.map { it.rank })
            assertEquals(listOf(10.0, 10.0, 4.0), rows.map { it.score })
            assertEquals(listOf(
                TournamentResult.Player(alphaOwner.uuid, "Saved-${alphaOwner.uuid}"),
                TournamentResult.Player(alphaMember.uuid, "Saved-${alphaMember.uuid}")
            ), rows.first().players)
            assertEquals(emptyList<TournamentResult.Player>(), rows.last().players)
            assertSame(alphaParticipation, tournament.getTeam(alpha))

            assertThrows(UnsupportedOperationException::class.java) {
                (rows as MutableList<TournamentResult.Team>).clear()
            }
            assertThrows(UnsupportedOperationException::class.java) {
                (rows.first().players as MutableList<TournamentResult.Player>).clear()
            }

            alpha.tryKickPlayerFromTeam(alphaMember)
            tournament.addScores(tournament.getTeam(alpha), 1.0)
            assertEquals(10.0, rows.first().score)
            assertEquals(2, rows.first().players.size)
        } finally {
            serverField.set(null, previousServer)
        }
    }

    private fun serverProxy(): Server = Proxy.newProxyInstance(
        Server::class.java.classLoader,
        arrayOf(Server::class.java)
    ) { _, method, args ->
        when (method.name) {
            "getOfflinePlayer" -> offlinePlayerProxy(args!![0] as UUID)
            "getPlayer" -> null
            else -> null
        }
    } as Server

    private fun offlinePlayerProxy(uuid: UUID): OfflinePlayer = Proxy.newProxyInstance(
        OfflinePlayer::class.java.classLoader,
        arrayOf(OfflinePlayer::class.java)
    ) { _, method, _ ->
        when (method.name) {
            "getName" -> "Saved-$uuid"
            else -> null
        }
    } as OfflinePlayer
}
