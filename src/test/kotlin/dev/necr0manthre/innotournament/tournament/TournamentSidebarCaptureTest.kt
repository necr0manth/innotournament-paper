package dev.necr0manthre.innotournament.tournament

import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.teams.PluginTeam
import dev.necr0manthre.innotournament.teams.TeamCreateResult
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.Server
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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

    private fun withServer(
        names: Map<UUID, Component>,
        action: (FakeServer) -> Unit
    ) {
        val field = Bukkit::class.java.getDeclaredField("server").apply { isAccessible = true }
        val previous = field.get(null)
        val server = FakeServer(names)
        field.set(null, server.proxy)
        try {
            action(server)
        } finally {
            field.set(null, previous)
        }
    }

    private class FakeServer(names: Map<UUID, Component>) {
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
                "getPlayer" -> players[args!!.single() as UUID]
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
