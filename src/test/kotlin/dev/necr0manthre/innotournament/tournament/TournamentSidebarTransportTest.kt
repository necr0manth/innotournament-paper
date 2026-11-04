package dev.necr0manthre.innotournament.tournament

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Score
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.ScoreboardManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.UUID

class TournamentSidebarTransportTest {
    @Test
    fun staleConnectionReleaseLeavesNewConnectionSidebarOwned() {
        val fixture = Fixture()
        val id = UUID.randomUUID()
        val oldPlayer = fixture.player(id)
        val newPlayer = fixture.player(id)
        val line = Component.text("first")

        try {
            fixture.transport.display(oldPlayer, Component.text("title"), listOf(line))
            fixture.transport.display(newPlayer, Component.text("title"), listOf(line))
            val newScoreboard = newPlayer.scoreboard
            val newObjective = fixture.objectives.last()

            fixture.transport.release(oldPlayer)
            fixture.transport.display(newPlayer, Component.text("title"), listOf(Component.text("updated")))

            assertSame(newScoreboard, newPlayer.scoreboard)
            assertEquals(0, newObjective.unregisterCount)
            assertEquals(2, newObjective.scores.values.single().customNameCount)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun updatesOnlyChangedRowsAndRemovesOnlyRowsThatDisappear() {
        val fixture = Fixture()
        val player = fixture.player(UUID.randomUUID())
        val first = Component.text("first")
        val second = Component.text("second")
        val third = Component.text("third")

        try {
            fixture.transport.display(player, Component.text("title"), listOf(first, second, third))
            val objective = fixture.objectives.single()
            assertEquals(3, objective.scores.values.sumOf { it.customNameCount })
            assertEquals(3, objective.scores.values.sumOf { it.scoreSetCount })

            fixture.transport.display(player, Component.text("title"), listOf(first, Component.text("changed"), third))

            assertEquals(4, objective.scores.values.sumOf { it.customNameCount })
            assertEquals(3, objective.scores.values.sumOf { it.scoreSetCount })
            assertEquals(0, fixture.resetEntries.size)

            fixture.transport.display(player, Component.text("title"), listOf(first))

            assertEquals(4, objective.scores.values.sumOf { it.customNameCount })
            assertEquals(3, objective.scores.values.sumOf { it.scoreSetCount })
            assertEquals(2, fixture.resetEntries.size)

            fixture.transport.display(player, Component.text("title"), emptyList())
            assertEquals(3, fixture.resetEntries.size)
            fixture.transport.display(player, Component.text("title"), listOf(first))
            assertEquals(3, fixture.resetEntries.size)
            assertEquals(5, objective.scores.values.sumOf { it.customNameCount })
            assertEquals(4, objective.scores.values.sumOf { it.scoreSetCount })
        } finally {
            fixture.close()
        }
    }

    private class Fixture {
        val objectives = mutableListOf<ObjectiveProxy>()
        val resetEntries = mutableListOf<String>()
        private val serverField = Bukkit::class.java.getDeclaredField("server").apply { isAccessible = true }
        private val previousServer = serverField.get(null)
        val transport: TournamentSidebarTransport

        init {
            val criteria = proxy<Criteria> { _, _ -> error("Unexpected criteria call") }
            serverField.set(null, proxy<Server> { method, _ ->
                check(method == "getScoreboardCriteria")
                criteria
            })
            val manager = proxy<ScoreboardManager> { method, _ ->
                check(method == "getNewScoreboard")
                scoreboard()
            }
            transport = TournamentSidebarTransport(manager)
        }

        fun close() {
            transport.close()
            serverField.set(null, previousServer)
        }

        fun player(id: UUID): Player {
            var board: Scoreboard = scoreboard()
            return proxy { method, args ->
                when (method) {
                    "getUniqueId" -> id
                    "isOnline" -> true
                    "getScoreboard" -> board
                    "setScoreboard" -> { board = args!!.single() as Scoreboard; null }
                    else -> error("Unexpected player call: $method")
                }
            }
        }

        private fun scoreboard(): Scoreboard = proxy { method, args ->
            when (method) {
                "registerNewObjective" -> ObjectiveProxy().also { objectives += it }.proxy
                "resetScores" -> { resetEntries += args!!.single() as String; null }
                else -> error("Unexpected scoreboard call: $method")
            }
        }

        inner class ObjectiveProxy {
            val scores = mutableMapOf<String, ScoreProxy>()
            var unregisterCount = 0
            val proxy: Objective = this@Fixture.proxy { method, args ->
                when (method) {
                    "setDisplaySlot", "displayName" -> null
                    "getScore" -> scores.getOrPut(args!!.single() as String) { ScoreProxy() }.proxy
                    "getScoreboard" -> if (unregisterCount == 0) AnyScoreboard else null
                    "unregister" -> { unregisterCount++; null }
                    else -> error("Unexpected objective call: $method")
                }
            }
        }

        inner class ScoreProxy {
            var customNameCount = 0
            var scoreSetCount = 0
            val proxy: Score = this@Fixture.proxy { method, _ ->
                when (method) {
                    "customName" -> { customNameCount++; null }
                    "setScore" -> { scoreSetCount++; null }
                    else -> error("Unexpected score call: $method")
                }
            }
        }

        private val AnyScoreboard: Scoreboard = proxy { _, _ -> null }

        private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>?) -> Any?): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
                call(method.name, args)
            } as T
    }
}
