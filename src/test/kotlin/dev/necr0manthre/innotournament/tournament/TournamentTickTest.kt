package dev.necr0manthre.innotournament.tournament

import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.teams.PluginTeam
import dev.necr0manthre.innotournament.teams.TeamCreateResult
import dev.necr0manthre.innotournament.tournament.events.EventPlan
import dev.necr0manthre.innotournament.tournament.events.ITournamentEventListener
import dev.necr0manthre.innotournament.tournament.events.events.PlayerTickEvent
import dev.necr0manthre.innotournament.tournament.events.events.TimeEvent
import dev.necr0manthre.innotournament.tournament.events.event_data.PlayerHolder
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.plugin.PluginManager
import org.bukkit.scheduler.BukkitScheduler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.UUID

class TournamentTickTest {
    @Test
    fun snapshotsPlayersAsAnImmutableList() {
        val source = mutableListOf<TournamentPlayer>()
        val tick = TournamentTick(TournamentPhase.PREPARED, 1, source)

        source.clear()

        assertEquals(emptyList<TournamentPlayer>(), tick.players)
        assertThrows(UnsupportedOperationException::class.java) {
            (tick.players as MutableList<TournamentPlayer>).clear()
        }
    }

    @Test
    fun timeZeroAndOneFireOnTheFirstFrameAndLaterTimesUseTheStageCounter() {
        val tournament = tournament()
        setPhase(tournament, TournamentPhase.PREPARED)
        val trace = mutableListOf<String>()
        tournament.ticks.subscribe {
            assertTrue(it.players.isEmpty())
            trace.add("frame:${it.stageTick}")
        }

        fun timeEvent(time: Long, label: String) = TimeEvent(time).subscribe(tournament).also { instance ->
            instance.addListener(ITournamentEventListener { _, _ -> trace.add(label) })
        }
        val zero = timeEvent(0, "time:0")
        val one = timeEvent(1, "time:1")
        val three = timeEvent(3, "time:3")

        repeat(3) { dispatchNextTick(tournament) }

        assertEquals(
            listOf("frame:1", "time:0", "time:1", "frame:2", "frame:3", "time:3"),
            trace
        )
        assertEquals(3L, stageTick(tournament))
        zero.unsubscribe()
        one.unsubscribe()
        three.unsubscribe()
    }

    @Test
    fun playerTickCountsEligibleFramesPerParticipationAndRechecksStaleEntries() {
        val previousServer = setServer(onlineServer())
        try {
            val manager = PluginTeam.TeamManager()
            val owner = PluginPlayer.get(UUID.randomUUID())
            val team = (manager.createNewTeam(owner, "tick-owner", Component.text("tick-owner")) as
                TeamCreateResult.Created).team
            val tournament = tournament(manager)
            setPhase(tournament, TournamentPhase.STARTED)
            tournament.checkTeam(team)
            val original = checkNotNull(tournament.getPlayer(owner))
            markPrepared(original)
            original.setLives(0)

            val delivered = mutableListOf<TournamentPlayer>()
            var invalidateBeforeDelivery = false
            val staleFrameCallback: (TournamentTick) -> Unit = {
                if (invalidateBeforeDelivery) {
                    invalidateBeforeDelivery = false
                    assertTrue(team.tryKickPlayerFromTeam(owner).isSuccess)
                }
            }
            tournament.ticks.subscribe(staleFrameCallback)
            val instance = PlayerTickEvent(2).subscribe(tournament)
            instance.addListener(ITournamentEventListener { _, holder: PlayerHolder -> delivered.add(holder.player) })

            dispatchNextTick(tournament)
            dispatchNextTick(tournament)
            dispatchNextTick(tournament)
            assertEquals(listOf(original, original), delivered)

            clearPlayer(tournament, owner)
            tournament.checkTeam(team)
            val replacement = checkNotNull(tournament.getPlayer(owner))
            assertFalse(original === replacement)
            markPrepared(replacement)
            dispatchNextTick(tournament)
            assertSame(replacement, delivered.last())
            assertEquals(3, delivered.size)

            invalidateBeforeDelivery = true
            dispatchNextTick(tournament)
            assertEquals(3, delivered.size)
            tournament.ticks.unsubscribe(staleFrameCallback)

            instance.unsubscribe()
        } finally {
            restoreServer(previousServer)
        }
    }

    @Test
    fun offlineFramesPauseAndPreparedFramesDoNotDeliverPlayerTicks() {
        var online = true
        val previousServer = setServer(onlineServer { online })
        try {
            val manager = PluginTeam.TeamManager()
            val owner = PluginPlayer.get(UUID.randomUUID())
            val team = (manager.createNewTeam(owner, "tick-offline", Component.text("tick-offline")) as
                TeamCreateResult.Created).team
            val tournament = tournament(manager)
            setPhase(tournament, TournamentPhase.STARTED)
            tournament.checkTeam(team)
            val player = checkNotNull(tournament.getPlayer(owner))
            markPrepared(player)

            val delivered = mutableListOf<TournamentPlayer>()
            val instance = PlayerTickEvent(2).subscribe(tournament)
            instance.addListener(ITournamentEventListener { _, holder: PlayerHolder -> delivered.add(holder.player) })
            dispatchNextTick(tournament)
            online = false
            dispatchNextTick(tournament)
            dispatchNextTick(tournament)
            online = true
            dispatchNextTick(tournament)
            assertEquals(listOf(player), delivered)
            dispatchNextTick(tournament)
            assertEquals(listOf(player, player), delivered)

            setPhase(tournament, TournamentPhase.PREPARED)
            dispatchNextTick(tournament)
            assertEquals(2, delivered.size)
            instance.unsubscribe()
        } finally {
            restoreServer(previousServer)
        }
    }

    @Test
    fun finishRequiresTwoAliveTeamsToHaveCoexisted() {
        val previousServer = setServer(onlineServer { false })
        try {
            val manager = PluginTeam.TeamManager()
            val oneOwner = PluginPlayer.get(UUID.randomUUID())
            val oneTeam = createTeam(manager, oneOwner, "only-one")
            val oneTournament = tournament(manager)
            setPhase(oneTournament, TournamentPhase.STARTED)
            oneTournament.checkTeam(oneTeam)
            invokeServerTick(oneTournament)
            assertEquals(TournamentPhase.STARTED, oneTournament.phase)

            val twoOwner = PluginPlayer.get(UUID.randomUUID())
            val twoTeam = createTeam(manager, twoOwner, "was-two")
            val secondOwner = PluginPlayer.get(UUID.randomUUID())
            val secondTeam = createTeam(manager, secondOwner, "second-alive")
            val twoTournament = tournament(manager)
            setPhase(twoTournament, TournamentPhase.STARTED)
            twoTournament.checkTeam(twoTeam)
            twoTournament.checkTeam(secondTeam)
            activateTournamentPresentationForTest(twoTournament)
            invokeServerTick(twoTournament)
            assertEquals(TournamentPhase.STARTED, twoTournament.phase)

            assertTrue(secondTeam.tryKickPlayerFromTeam(secondOwner).isSuccess)
            invokeServerTick(twoTournament)
            assertEquals(TournamentPhase.ENDED, twoTournament.phase)

            val ticks = mutableListOf<TournamentTick>()
            twoTournament.ticks.subscribe(ticks::add)
            val endedTick = stageTick(twoTournament)
            invokeServerTick(twoTournament)
            assertEquals(endedTick, stageTick(twoTournament))
            assertTrue(ticks.isEmpty())
            Tournament::class.java.getDeclaredField("phase").apply { isAccessible = true }
                .set(twoTournament, TournamentPhase.REMOVED)
            invokeServerTick(twoTournament)
            assertEquals(endedTick, stageTick(twoTournament))
            assertTrue(ticks.isEmpty())
        } finally {
            restoreServer(previousServer)
        }
    }

    @Test
    fun losingLastLifeEndsTournamentOnTheNextTickAndAwardsTheBonusOnce() {
        val previousServer = setServer(onlineServer { false })
        try {
            val manager = PluginTeam.TeamManager()
            val lastLifeOwner = PluginPlayer.get(UUID.randomUUID())
            val lastLifeTeam = createTeam(manager, lastLifeOwner, "last-life")
            val winnerOwner = PluginPlayer.get(UUID.randomUUID())
            val winnerTeam = createTeam(manager, winnerOwner, "winner")
            val tournament = testTournament(manager, lastTeamScore = 7f)
            setPhase(tournament, TournamentPhase.STARTED)
            tournament.checkTeam(lastLifeTeam)
            tournament.checkTeam(winnerTeam)
            val lastLifePlayer = checkNotNull(tournament.getPlayer(lastLifeOwner))
            val winner = checkNotNull(tournament.getPlayer(winnerOwner))
            markPrepared(lastLifePlayer)
            markPrepared(winner)
            lastLifePlayer.setLives(1)
            winner.setLives(1)
            activateTournamentPresentationForTest(tournament)

            invokeServerTick(tournament)
            assertEquals(TournamentPhase.STARTED, tournament.phase)

            var endEvents = 0
            tournament.onEnd.subscribe { endEvents++ }
            Tournament::class.java.getDeclaredMethod("handlePlayerDeath", PluginPlayer::class.java)
                .apply { isAccessible = true }.invoke(tournament, lastLifeOwner)
            assertEquals(0, lastLifePlayer.lives)
            assertEquals(TournamentPhase.STARTED, tournament.phase)

            invokeServerTick(tournament)

            assertEquals(TournamentPhase.ENDED, tournament.phase)
            assertEquals(1, endEvents)
            assertEquals(7.0, checkNotNull(tournament.getTeam(winnerTeam)).score)
            assertEquals(setOf(winnerTeam.id), checkNotNull(tournament.result).winnerTeamIds)
            invokeServerTick(tournament)
            assertEquals(1, endEvents)
            assertEquals(7.0, checkNotNull(tournament.getTeam(winnerTeam)).score)
        } finally {
            restoreServer(previousServer)
        }
    }

    private fun tournament(manager: PluginTeam.TeamManager = PluginTeam.TeamManager()) = testTournament(manager)

    private fun createTeam(manager: PluginTeam.TeamManager, owner: PluginPlayer, id: String) =
        (manager.createNewTeam(owner, id, Component.text(id)) as TeamCreateResult.Created).team

    private fun setPhase(tournament: Tournament, phase: TournamentPhase) {
        Tournament::class.java.getDeclaredField("phase").apply { isAccessible = true }.set(tournament, phase)
        Tournament::class.java.getDeclaredField("stageTick").apply { isAccessible = true }.setLong(tournament, 0L)
    }

    private fun stageTick(tournament: Tournament): Long =
        Tournament::class.java.getDeclaredField("stageTick").apply { isAccessible = true }.getLong(tournament)

    private fun dispatchNextTick(tournament: Tournament) {
        Tournament::class.java.getDeclaredMethod("dispatchNextTick").apply { isAccessible = true }.invoke(tournament)
    }

    private fun invokeServerTick(tournament: Tournament) {
        Tournament::class.java.getDeclaredMethod("serverTick").apply { isAccessible = true }.invoke(tournament)
    }

    private fun markPrepared(player: TournamentPlayer) {
        markTournamentPlayerPrepared(player)
    }

    private fun clearPlayer(tournament: Tournament, player: PluginPlayer) {
        Tournament::class.java.getDeclaredMethod("clearPlayer", PluginPlayer::class.java, PluginTeam::class.java)
            .apply { isAccessible = true }.invoke(tournament, player, null)
    }

    private fun setServer(server: Server): Any? {
        val field = Bukkit::class.java.getDeclaredField("server").apply { isAccessible = true }
        return field.get(null).also { field.set(null, server) }
    }

    private fun restoreServer(previous: Any?) {
        Bukkit::class.java.getDeclaredField("server").apply { isAccessible = true }.set(null, previous)
    }

    private fun onlineServer(online: () -> Boolean = { true }): Server = Proxy.newProxyInstance(
        Server::class.java.classLoader,
        arrayOf(Server::class.java)
    ) { _, method, args ->
        when (method.name) {
            "isPrimaryThread" -> true
            "getPluginManager" -> Proxy.newProxyInstance(
                PluginManager::class.java.classLoader,
                arrayOf(PluginManager::class.java)
            ) { _, managerMethod, _ -> if (managerMethod.name == "registerEvents") null else null }
            "getScheduler" -> Proxy.newProxyInstance(
                BukkitScheduler::class.java.classLoader,
                arrayOf(BukkitScheduler::class.java)
            ) { _, schedulerMethod, _ ->
                when (schedulerMethod.name) {
                    "scheduleSyncRepeatingTask" -> 1
                    "cancelTask" -> null
                    else -> null
                }
            }
            "getPlayer" -> if (online()) playerProxy(args!![0] as UUID) else null
            "getOnlinePlayers" -> if (online()) emptyList<Player>() else emptyList<Player>()
            "getOfflinePlayer" -> offlinePlayerProxy()
            "broadcast" -> 0
            else -> when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Double.TYPE -> 0.0
                java.lang.Float.TYPE -> 0f
                else -> null
            }
        }
    } as Server

    private fun playerProxy(uuid: UUID): Player = Proxy.newProxyInstance(
        Player::class.java.classLoader,
        arrayOf(Player::class.java)
    ) { _, method, _ ->
        when (method.name) {
            "getUniqueId" -> uuid
            else -> null
        }
    } as Player

    private fun offlinePlayerProxy(): org.bukkit.OfflinePlayer = Proxy.newProxyInstance(
        org.bukkit.OfflinePlayer::class.java.classLoader,
        arrayOf(org.bukkit.OfflinePlayer::class.java)
    ) { _, method, _ -> if (method.name == "getName") "Saved" else null } as org.bukkit.OfflinePlayer
}
