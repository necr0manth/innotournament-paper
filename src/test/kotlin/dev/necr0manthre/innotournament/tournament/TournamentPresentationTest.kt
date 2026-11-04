package dev.necr0manthre.innotournament.tournament

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.PluginManager
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Score
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.ScoreboardManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.UUID

class TournamentPresentationTest {
    @Test
    fun activationCapturesOncePerRefreshAndPersonalizesEveryViewer() {
        val fixture = Fixture()
        val first = fixture.player(UUID.randomUUID())
        val second = fixture.player(UUID.randomUUID())
        fixture.online += first.player
        fixture.online += second.player
        var captures = 0
        var personalizations = 0
        var presentation: TournamentPresentation? = null
        try {
            presentation = TournamentPresentation(
                fixture.plugin,
                TournamentSidebarTransport(fixture.scoreboardManager),
            ) {
                captures++
                { _ ->
                    personalizations++
                    TournamentLiveSidebarBuilder.Sidebar(Component.text("Live"), listOf(Component.text("Game")))
                }
            }

            presentation.activate()
            assertSame(presentation, fixture.registeredListener)
            assertEquals(20L, fixture.refreshPeriod)
            assertEquals(1, captures)
            assertEquals(2, personalizations)
            fixture.refreshTask!!.run()
            assertEquals(2, captures)
            assertEquals(4, personalizations)
            presentation.onQuit(org.bukkit.event.player.PlayerQuitEvent(first.player, Component.empty()))
            assertSame(first.previousScoreboard, first.player.scoreboard)
            presentation.onJoin(org.bukkit.event.player.PlayerJoinEvent(first.player, Component.empty()))
            assertEquals(3, captures)
            assertEquals(5, personalizations)

            presentation.finishNormally(TournamentResult(emptyList(), emptySet(), null))
            assertEquals(1, first.messages.size)
            assertEquals(7, fixture.cancelledTask)

            val late = fixture.player(UUID.randomUUID())
            presentation.onJoin(org.bukkit.event.player.PlayerJoinEvent(late.player, Component.empty()))
            presentation.finishNormally(TournamentResult(emptyList(), emptySet(), null))
            assertEquals(1, first.messages.size)
            assertEquals(0, late.messages.size)
            assertEquals(3, captures)
            assertEquals(5, personalizations)

            presentation.close()
            assertSame(first.previousScoreboard, first.player.scoreboard)
            assertSame(late.previousScoreboard, late.player.scoreboard)
        } finally {
            presentation?.close()
            fixture.close()
        }
    }

    @Test
    fun periodicFailureCancelsItsTaskAndPropagates() {
        val fixture = Fixture()
        fixture.online += fixture.player(UUID.randomUUID()).player
        val failure = IllegalStateException("render failed")
        var captures = 0
        var presentation: TournamentPresentation? = null
        try {
            presentation = TournamentPresentation(
                fixture.plugin,
                TournamentSidebarTransport(fixture.scoreboardManager),
            ) {
                captures++
                if (captures > 1) throw failure
                { _ -> TournamentLiveSidebarBuilder.Sidebar(Component.text("Live"), emptyList()) }
            }

            presentation.activate()
            val thrown = assertThrows(IllegalStateException::class.java) { fixture.refreshTask!!.run() }
            assertSame(failure, thrown)
            assertEquals(7, fixture.cancelledTask)
            assertEquals(2, captures)
        } finally {
            presentation?.close()
            fixture.close()
        }
    }

    @Test
    fun lifecycleAndEventCallbacksRejectWorkerThreadBeforeChangingState() {
        val fixture = Fixture()
        val player = fixture.player(UUID.randomUUID())
        fixture.online += player.player
        var presentation: TournamentPresentation? = null
        try {
            presentation = TournamentPresentation(
                fixture.plugin,
                TournamentSidebarTransport(fixture.scoreboardManager),
            ) { { _ -> TournamentLiveSidebarBuilder.Sidebar(Component.text("Live"), emptyList()) } }

            fixture.primaryThread = false
            assertThrows(IllegalStateException::class.java) { presentation.activate() }
            assertEquals(null, fixture.registeredListener)
            assertEquals(null, fixture.refreshTask)

            fixture.primaryThread = true
            presentation.activate()
            val presentationBoard = player.player.scoreboard
            fixture.primaryThread = false
            assertThrows(IllegalStateException::class.java) {
                presentation.finishNormally(TournamentResult(emptyList(), emptySet(), null))
            }
            assertThrows(IllegalStateException::class.java) {
                presentation.onJoin(org.bukkit.event.player.PlayerJoinEvent(player.player, Component.empty()))
            }
            assertThrows(IllegalStateException::class.java) {
                presentation.onQuit(org.bukkit.event.player.PlayerQuitEvent(player.player, Component.empty()))
            }
            assertThrows(IllegalStateException::class.java) { presentation.close() }
            assertSame(presentationBoard, player.player.scoreboard)
            assertEquals(0, player.messages.size)
            assertEquals(-1, fixture.cancelledTask)

            fixture.primaryThread = true
            presentation.finishNormally(TournamentResult(emptyList(), emptySet(), null))
            assertEquals(1, player.messages.size)
            presentation.close()
            assertSame(player.previousScoreboard, player.player.scoreboard)
        } finally {
            fixture.primaryThread = true
            presentation?.close()
            fixture.close()
        }
    }

    private class Fixture {
        private val serverField = Bukkit::class.java.getDeclaredField("server").apply { isAccessible = true }
        private val previousServer = serverField.get(null)
        val online = mutableListOf<Player>()
        var registeredListener: Any? = null
        var refreshTask: Runnable? = null
        var refreshPeriod = -1L
        var cancelledTask = -1
        var primaryThread = true
        val plugin: Plugin = proxy { _, _ -> null }
        private val pluginManager: PluginManager = proxy { method, args ->
            check(method == "registerEvents")
            registeredListener = args!![0]
            null
        }
        private val scheduler: BukkitScheduler = proxy { method, args ->
            when (method) {
                "scheduleSyncRepeatingTask" -> {
                    refreshTask = args!![1] as Runnable
                    refreshPeriod = args[3] as Long
                    7
                }
                "cancelTask" -> { cancelledTask = args!!.single() as Int; null }
                else -> error("Unexpected scheduler call: $method")
            }
        }
        val scoreboardManager: ScoreboardManager = proxy { method, _ ->
            check(method == "getNewScoreboard")
            scoreboard()
        }

        init {
            val criteria = proxy<Criteria> { _, _ -> error("Unexpected criteria call") }
            serverField.set(null, proxy<Server> { method, _ ->
                when (method) {
                    "getPluginManager" -> pluginManager
                    "getScheduler" -> scheduler
                    "getScoreboardManager" -> scoreboardManager
                    "getOnlinePlayers" -> online
                    "getScoreboardCriteria" -> criteria
                    "isPrimaryThread" -> primaryThread
                    else -> error("Unexpected server call: $method")
                }
            })
        }

        fun player(id: UUID): PlayerState {
            val previous = scoreboard()
            var current = previous
            val messages = mutableListOf<Component>()
            val player = proxy<Player> { method, args ->
                when (method) {
                    "getUniqueId" -> id
                    "isOnline" -> true
                    "getScoreboard" -> current
                    "setScoreboard" -> { current = args!!.single() as Scoreboard; null }
                    "sendMessage" -> { messages += args!!.first() as Component; null }
                    else -> error("Unexpected player call: $method")
                }
            }
            return PlayerState(player, previous, messages)
        }

        private fun scoreboard(): Scoreboard = proxy { method, _ ->
            when (method) {
                "registerNewObjective" -> objective()
                "resetScores" -> null
                else -> error("Unexpected scoreboard call: $method")
            }
        }

        private fun objective(): Objective {
            lateinit var scoreboard: Scoreboard
            val objective = proxy<Objective> { method, _ ->
                when (method) {
                    "setDisplaySlot", "displayName", "unregister" -> null
                    "getScore" -> proxy<Score> { scoreMethod, _ ->
                        if (scoreMethod == "customName" || scoreMethod == "setScore") null
                        else error("Unexpected score call: $scoreMethod")
                    }
                    "getScoreboard" -> scoreboard
                    else -> error("Unexpected objective call: $method")
                }
            }
            scoreboard = proxy { method, _ ->
                when (method) {
                    "registerNewObjective" -> objective()
                    "resetScores" -> null
                    else -> error("Unexpected scoreboard call: $method")
                }
            }
            return objective
        }

        fun close() {
            serverField.set(null, previousServer)
        }

        data class PlayerState(val player: Player, val previousScoreboard: Scoreboard, val messages: List<Component>)

        private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>?) -> Any?): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
                call(method.name, args)
            } as T
    }
}
