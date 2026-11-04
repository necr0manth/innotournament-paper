package dev.necr0manthre.innotournament.tournament

import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.teams.PluginTeam
import dev.necr0manthre.innotournament.teams.TeamCreateResult
import dev.necr0manthre.innotournament.tournament.events.EventPlan
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.Server
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.UUID

class TournamentSidebarCaptureTest {
    private val plain = PlainTextComponentSerializer.plainText()

    @Test
    fun captureFreezesRealTeamNamesAndOrderedRosterWhileFreshCaptureReadsChanges() {
        val ownerId = UUID.randomUUID()
        val secondId = UUID.randomUUID()
        val thirdId = UUID.randomUUID()
        val outsiderId = UUID.randomUUID()
        val ownerName = Component.text("Owner", NamedTextColor.AQUA)
        val secondName = Component.text("Second", NamedTextColor.GREEN)
        val thirdName = Component.text("Third", NamedTextColor.LIGHT_PURPLE)
        withServer(mapOf(ownerId to ownerName, secondId to secondName, thirdId to thirdName)) { server ->
            val manager = PluginTeam.TeamManager()
            val owner = PluginPlayer.get(ownerId)
            val second = PluginPlayer.get(secondId)
            val third = PluginPlayer.get(thirdId)
            val teamName = Component.text("Blue", NamedTextColor.GOLD)
            val team = (manager.createNewTeam(owner, "blue", teamName) as TeamCreateResult.Created).team
            assertTrue(team.tryAddPlayerToTeam(second).isSuccess)

            val frozen = TournamentSidebarCapture.capturePrepared(manager)
            assertNull(owner.tournamentState.tournament)
            assertNull(team.tournamentState.tournament)

            assertTrue(team.changeTeamName(Component.text("Red", NamedTextColor.RED)))
            assertTrue(team.tryKickPlayerFromTeam(second).isSuccess)
            assertTrue(team.tryAddPlayerToTeam(third).isSuccess)

            val frozenSidebar = frozen(server.player(ownerId))
            val frozenLines = frozenSidebar.lines
            assertEquals("Команда: Blue", plain.serialize(frozenLines[1]))
            assertEquals(teamName, frozenLines[1].children().single())
            assertEquals(listOf(ownerName, secondName), frozenLines.drop(2))

            val currentSidebar = TournamentSidebarCapture.capturePrepared(manager)(server.player(ownerId))
            assertEquals("Команда: Red", plain.serialize(currentSidebar.lines[1]))
            assertEquals(listOf(ownerName, thirdName), currentSidebar.lines.drop(2))

            val outsider = TournamentSidebarCapture.capturePrepared(manager)(server.player(outsiderId))
            assertEquals("Вы без команды", plain.serialize(outsider.lines[1]))
            assertNull(owner.tournamentState.tournament)
            assertNull(second.tournamentState.tournament)
            assertNull(third.tournamentState.tournament)
            assertNull(team.tournamentState.tournament)
        }
    }

    @Test
    fun startedCaptureUsesDomainStandingsCanonicalRuntimeFactsAndFrozenViewerClassification() {
        val onlineId = UUID.randomUUID()
        val offlineId = UUID.randomUUID()
        val outsideId = UUID.randomUUID()
        val viewerWithoutTeamId = UUID.randomUUID()
        val blueName = Component.text("Blue", NamedTextColor.GOLD)
        val redName = Component.text("Red", NamedTextColor.RED)
        val playerName = Component.text("Zero lives", NamedTextColor.AQUA)
        withServer(mapOf(onlineId to playerName, offlineId to Component.text("Offline alive")), setOf(onlineId)) { server ->
            val manager = PluginTeam.TeamManager()
            val online = PluginPlayer.get(onlineId)
            val offline = PluginPlayer.get(offlineId)
            val outside = PluginPlayer.get(outsideId)
            val blue = (manager.createNewTeam(online, "blue", blueName) as TeamCreateResult.Created).team
            assertTrue(blue.tryAddPlayerToTeam(offline).isSuccess)
            manager.createNewTeam(outside, "outside", Component.text("Outside"))
            val redOwner = PluginPlayer.get(UUID.randomUUID())
            val red = (manager.createNewTeam(redOwner, "red", redName) as TeamCreateResult.Created).team
            val deleted = (manager.createNewTeam(
                PluginPlayer.get(UUID.randomUUID()), "deleted", Component.text("Deleted")
            ) as TeamCreateResult.Created).team

            val tournament = testTournament(manager)
            Tournament::class.java.getDeclaredField("phase").apply { isAccessible = true }
                .set(tournament, TournamentPhase.STARTED)
            tournament.checkTeam(blue)
            tournament.checkTeam(red)
            tournament.checkTeam(deleted)
            val blueParticipation = tournament.getTeam(blue)!!
            val redParticipation = tournament.getTeam(red)!!
            val deletedParticipation = tournament.getTeam(deleted)!!
            blueParticipation.addScore(10.0)
            redParticipation.addScore(10.0)
            deletedParticipation.addScore(100.0)
            assertTrue(manager.removeTeam(deleted))
            val onlineParticipation = tournament.getPlayer(online)!!
            markTournamentPlayerPrepared(onlineParticipation)
            onlineParticipation.setLives(0)

            val captured = TournamentSidebarCapture.captureStarted(tournament, manager)
            val ranks = tournament.standings()
            assertEquals(listOf("blue", "red"), ranks.map { it.id })
            assertEquals(listOf(1, 1), ranks.map { it.rank })
            assertNull(outside.tournamentState.tournament)

            val blueSidebar = captured(server.player(onlineId))
            val blueLines = blueSidebar.lines
            assertEquals("Команда: Blue: 10.00", plain.serialize(blueLines[1]))
            assertEquals(blueName, blueLines[1].children().first())
            assertEquals("Zero lives — 0", plain.serialize(blueLines[2]))
            assertEquals("Offline alive (не в сети) — 3", plain.serialize(blueLines[3]))
            assertEquals(listOf("1. Blue: 10.00", "1. Red: 10.00"), blueLines.drop(5).map(plain::serialize))
            assertTrue(blueLines.none { plain.serialize(it).contains("Deleted") })
            assertSame(blueName, ranks.first { it.id == "blue" }.displayName)

            val outsideSidebar = captured(server.player(outsideId))
            assertEquals("Команда вне турнира", plain.serialize(outsideSidebar.lines[1]))
            val unteamedSidebar = captured(server.player(viewerWithoutTeamId))
            assertEquals("Вы не участвуете", plain.serialize(unteamedSidebar.lines[1]))

            blue.changeTeamName(Component.text("Changed"))
            blue.tryKickPlayerFromTeam(offline)
            blueParticipation.addScore(5.0)
            onlineParticipation.setLives(2)
            assertEquals(blueSidebar, captured(server.player(onlineId)))
            assertEquals(blueName, blueLines[1].children().first())
            assertNull(outside.tournamentState.tournament)
        }
    }

    @Test
    fun startedCaptureRejectsWrongPhaseAndOffThreadCallsBeforeReading() {
        val manager = PluginTeam.TeamManager()
        val tournament = testTournament(manager)
        withServer(emptyMap()) {
            val phaseFailure = assertThrows(IllegalStateException::class.java) {
                TournamentSidebarCapture.captureStarted(tournament, manager)
            }
            assertTrue(phaseFailure.message.orEmpty().contains("started tournament"))

            val offThreadServer = proxy<Server> { method, _ ->
                when (method) {
                    "isPrimaryThread" -> false
                    else -> error("Unexpected Server call: $method")
                }
            }
            val field = Bukkit::class.java.getDeclaredField("server").apply { isAccessible = true }
            val previous = field.get(null)
            field.set(null, offThreadServer)
            try {
                val failure = assertThrows(IllegalStateException::class.java) {
                    TournamentSidebarCapture.captureStarted(tournament, manager)
                }
                assertTrue(failure.message.orEmpty().contains("server thread"))
            } finally {
                field.set(null, previous)
            }
        }
    }

    private fun withServer(
        names: Map<UUID, Component>,
        onlineIds: Set<UUID> = names.keys,
        action: (FakeServer) -> Unit
    ) {
        val field = Bukkit::class.java.getDeclaredField("server").apply { isAccessible = true }
        val previous = field.get(null)
        val server = FakeServer(names, onlineIds)
        field.set(null, server.proxy)
        try {
            action(server)
        } finally {
            field.set(null, previous)
        }
    }

    private class FakeServer(names: Map<UUID, Component>, onlineIds: Set<UUID>) {
        private val players = names.mapValues { (id, displayName) ->
            proxy<Player> { method, _ ->
                when (method) {
                    "getUniqueId" -> id
                    "displayName" -> displayName
                    "getName" -> plain.serialize(displayName)
                    else -> error("Unexpected Player call: $method")
                }
            }
        }

        val proxy: Server = proxy { method, args ->
            when (method) {
                "isPrimaryThread" -> true
                "getPlayer" -> (args!!.single() as UUID).takeIf { it in onlineIds }?.let(players::get)
                "getOfflinePlayer" -> proxy<OfflinePlayer> { offlineMethod, _ ->
                    when (offlineMethod) {
                        "getName" -> names[args!!.single() as UUID]?.let(plain::serialize)
                        else -> error("Unexpected OfflinePlayer call: $offlineMethod")
                    }
                }
                else -> error("Unexpected Server call: $method")
            }
        }

        fun player(id: UUID): Player = players[id] ?: proxy { method, _ ->
            when (method) {
                "getUniqueId" -> id
                else -> error("Unexpected outsider Player call: $method")
            }
        }
    }

    private companion object {
        val plain = PlainTextComponentSerializer.plainText()

        inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>?) -> Any?): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
                call(method.name, args)
            } as T
    }
}
