package dev.necr0manthre.innotournament.tournament

import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.teams.PluginTeam
import dev.necr0manthre.innotournament.teams.TeamCreateResult
import dev.necr0manthre.innotournament.teams.events.TeamChange
import dev.necr0manthre.innotournament.tournament.events.EventPlan
import dev.necr0manthre.innotournament.tournament.events.event_data.PlayerHolder
import dev.necr0manthre.innotournament.tournament.events.event_data.TeamAdvancement
import dev.necr0manthre.innotournament.tournament.events.events.AdvancementEvent
import dev.necr0manthre.innotournament.tournament.events.events.TeamAdvancementEvent
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.Server
import org.bukkit.advancement.Advancement
import org.bukkit.advancement.AdvancementProgress
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerAdvancementDoneEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.UUID

class TournamentAdvancementTest {
    @Test
    fun realCompletionsPublishInOrderAndSyncWithoutPublishingAgain() {
        val fixture = Fixture()
        try {
        val source = fixture.addMember("adv-source")
        val teammate = fixture.addMember("adv-teammate")
        val sourceProgress = fixture.progress(setOf("first"))
        val teammateProgress = fixture.progress(emptySet())
        fixture.bindProgress(source, sourceProgress)
        fixture.bindProgress(teammate, teammateProgress)
        val sourceEntry = fixture.prepare(source)
        fixture.prepare(teammate)
        sourceEntry.setLives(0)

        val trace = mutableListOf<String>()
        val personal = mutableListOf<RealPlayerAdvancement>()
        val firstTeam = mutableListOf<FirstTeamAdvancement>()
        fixture.tournament.realPlayerAdvancements.subscribe {
            trace += "player"
            personal += it
        }
        fixture.tournament.firstTeamAdvancements.subscribe {
            trace += "team"
            firstTeam += it
        }
        val personalBridge = AdvancementEvent(fixture.advancement).subscribe(fixture.tournament)
        val teamBridge = TeamAdvancementEvent(fixture.advancement).subscribe(fixture.tournament)
        val personalBridgeValues = mutableListOf<PlayerHolder>()
        val teamBridgeValues = mutableListOf<TeamAdvancement>()
        personalBridge.addListener { _, value -> personalBridgeValues += value }
        teamBridge.addListener { _, value -> teamBridgeValues += value }

        fixture.complete(source)

        assertEquals(listOf("player", "team"), trace)
        assertEquals(listOf(sourceEntry), personal.map { it.player })
        assertEquals(listOf(fixture.tournament.teams().single()), firstTeam.map { it.team })
        assertEquals(setOf("first"), teammateProgress.awarded)
        assertTrue(teammateProgress.proxy.isDone)
        assertEquals(listOf(sourceEntry), personalBridgeValues.map { it.player })
        assertEquals(listOf(fixture.advancement.key), teamBridgeValues.map { it.advancement?.key })
        assertEquals(setOf(fixture.advancement.key), fixture.tournament.teams().single().advancements)

        teammateProgress.awarded.clear()
        fixture.reconnect(teammate)
        assertEquals(setOf("first"), teammateProgress.awarded)
        assertEquals(listOf("player", "team"), trace)

        fixture.complete(source)
        assertEquals(listOf("player", "team"), trace)

        fixture.clearParticipation(source)
        fixture.tournament.checkTeam(fixture.team)
        fixture.complete(source)
        assertEquals(listOf("player", "team"), trace)
        val newEntry = fixture.prepare(source)
        fixture.complete(source)
        assertEquals(listOf("player", "team", "player"), trace)
        assertSame(newEntry, personal.last().player)
        assertEquals(2, personalBridgeValues.size)
        assertEquals(1, teamBridgeValues.size)

        personalBridge.unsubscribe()
        teamBridge.unsubscribe()
        } finally {
            fixture.close()
        }
    }

    @Test
    fun incompleteOrUnpreparedAdvancementDoesNotPublishOrSync() {
        val fixture = Fixture()
        try {
        val source = fixture.addMember("adv-incomplete")
        val teammate = fixture.addMember("adv-incomplete-teammate")
        val unprepared = fixture.addMember("adv-unprepared")
        val sourceProgress = fixture.progress(emptySet())
        val teammateProgress = fixture.progress(emptySet())
        val unpreparedProgress = fixture.progress(setOf("first"))
        fixture.bindProgress(source, sourceProgress)
        fixture.bindProgress(teammate, teammateProgress)
        fixture.bindProgress(unprepared, unpreparedProgress)
        fixture.tournament.checkTeam(fixture.team)
        fixture.prepare(source)
        fixture.prepare(teammate)

        val notices = mutableListOf<Any>()
        fixture.tournament.realPlayerAdvancements.subscribe(notices::add)
        fixture.tournament.firstTeamAdvancements.subscribe(notices::add)
        fixture.complete(source)
        assertTrue(notices.isEmpty())
        assertTrue(teammateProgress.awarded.isEmpty())

        sourceProgress.awarded.add("first")
        assertTrue(sourceProgress.proxy.isDone)
        assertEquals(setOf("second"), sourceProgress.proxy.remainingCriteria)
        fixture.complete(source)
        assertEquals(2, notices.size)
        assertEquals(setOf("first"), teammateProgress.awarded)

        fixture.complete(unprepared)
        assertEquals(2, notices.size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun newOnlineAndPendingLoginParticipationsReceiveTeamProgressWithoutNewRewards() {
        val fixture = Fixture()
        try {
            val source = fixture.addMember("adv-reset-source")
            val sourceProgress = fixture.progress(setOf("first"))
            fixture.bindProgress(source, sourceProgress)
            val sourceEntry = fixture.prepare(source)
            val notices = mutableListOf<Any>()
            fixture.tournament.realPlayerAdvancements.subscribe(notices::add)
            fixture.tournament.firstTeamAdvancements.subscribe(notices::add)
            fixture.complete(source)
            val expectedNotices = notices.toList()
            fixture.tournament.addScores(fixture.tournament.teams().single(), 12.0)

            val joinListener: (TeamChange) -> Unit = fixture.tournament::onPlayerJoinTeam
            fixture.manager.afterJoin.subscribe(joinListener)

            val onlineJoin = fixture.member("adv-reset-online")
            val onlineProgress = fixture.progress(emptySet())
            fixture.bindProgress(onlineJoin, onlineProgress)
            fixture.team.tryAddPlayerToTeam(onlineJoin)

            assertSame(sourceEntry, fixture.tournament.getPlayer(source))
            assertTrue(checkNotNull(fixture.tournament.getPlayer(onlineJoin)).prepared)
            assertEquals(setOf("first"), onlineProgress.awarded)
            assertEquals(expectedNotices, notices)
            assertEquals(12.0, fixture.tournament.teams().single().score)

            val pendingLogin = fixture.member("adv-reset-pending", isOnline = false)
            val pendingProgress = fixture.progress(emptySet())
            fixture.bindProgress(pendingLogin, pendingProgress)
            fixture.team.tryAddPlayerToTeam(pendingLogin)
            val pendingParticipation = checkNotNull(fixture.tournament.getPlayer(pendingLogin))
            assertTrue(!pendingParticipation.prepared)
            assertTrue(pendingProgress.awarded.isEmpty())

            fixture.setOnline(pendingLogin, true)
            fixture.tournament.checkPlayer(fixture.player(pendingLogin))

            assertSame(pendingParticipation, fixture.tournament.getPlayer(pendingLogin))
            assertTrue(pendingParticipation.prepared)
            assertEquals(setOf("first"), pendingProgress.awarded)
            assertEquals(expectedNotices, notices)
            assertEquals(12.0, fixture.tournament.teams().single().score)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun completedLeaveImmediatelySpectatesAndFreshJoinResetsWithoutStaleLeaveClearingIt() {
        val fixture = Fixture()
        try {
            val player = fixture.member("leave-and-rejoin")
            val joinListener: (TeamChange) -> Unit = fixture.tournament::onPlayerJoinTeam
            val leaveListener: (TeamChange) -> Unit = fixture.tournament::onPlayerLeaveTeam
            fixture.manager.afterJoin.subscribe(joinListener)
            fixture.manager.afterLeave.subscribe(leaveListener)
            fixture.bindProgress(player, fixture.progress(emptySet()))
            assertTrue(fixture.team.tryAddPlayerToTeam(player).isSuccess)

            val original = checkNotNull(fixture.tournament.getPlayer(player))
            assertTrue(original.prepared)
            assertEquals(GameMode.SURVIVAL, fixture.gameMode(player))
            fixture.tournament.addScores(original.team, 9.0)

            assertTrue(fixture.team.tryKickPlayerFromTeam(player).isSuccess)
            assertEquals(null, fixture.tournament.getPlayer(player))
            assertEquals(GameMode.SPECTATOR, fixture.gameMode(player))
            assertEquals(9.0, original.team.score)

            val otherOwner = fixture.member("other-team-owner")
            fixture.bindProgress(otherOwner, fixture.progress(emptySet()))
            val otherTeam = (fixture.manager.createNewTeam(
                otherOwner, "other-team", Component.text("other-team")
            ) as TeamCreateResult.Created).team
            assertTrue(otherTeam.tryAddPlayerToTeam(player).isSuccess)
            val fresh = checkNotNull(fixture.tournament.getPlayer(player))
            assertTrue(fresh !== original)
            assertTrue(fresh.prepared)
            assertEquals(3, fresh.lives)
            assertEquals(GameMode.SURVIVAL, fixture.gameMode(player))

            fixture.tournament.onPlayerLeaveTeam(TeamChange(player, fixture.team, 1, 0))
            assertSame(fresh, fixture.tournament.getPlayer(player))
            assertEquals(GameMode.SURVIVAL, fixture.gameMode(player))
            assertEquals(9.0, original.team.score)
        } finally {
            fixture.close()
        }
    }

    private inner class Fixture : AutoCloseable {
        private val online = LinkedHashMap<UUID, Player>()
        private val onlineIds = LinkedHashSet<UUID>()
        private val progressByPlayer = LinkedHashMap<UUID, AdvancementProgress>()
        private val gameModes = LinkedHashMap<UUID, GameMode>()
        private val previousServer = setServer()
        val manager = PluginTeam.TeamManager()
        private val owner = member("adv-owner")
        val team = (manager.createNewTeam(owner, "adv-team", Component.text("adv-team")) as
            TeamCreateResult.Created).team
        val tournament = testTournament(manager)
        val advancement: Advancement = proxy { method, _ ->
            if (method == "getKey") NamespacedKey.minecraft("test_advancement") else unexpected(method)
        }
        private val handler: Any = Tournament::class.java.getDeclaredField("advancementHandler")
            .apply { isAccessible = true }.get(tournament)

        init {
            Tournament::class.java.getDeclaredField("phase").apply { isAccessible = true }
                .set(tournament, TournamentPhase.STARTED)
            tournament.checkTeam(team)
            checkNotNull(tournament.getPlayer(owner))
        }

        fun addMember(id: String): PluginPlayer {
            val player = member(id)
            check(team.tryAddPlayerToTeam(player).isSuccess)
            return player
        }

        fun setOnline(player: PluginPlayer, value: Boolean) {
            if (value) onlineIds.add(player.uuid) else onlineIds.remove(player.uuid)
        }

        fun player(player: PluginPlayer): Player = online.getValue(player.uuid)

        fun gameMode(player: PluginPlayer): GameMode? = gameModes[player.uuid]

        fun prepare(player: PluginPlayer): TournamentPlayer {
            tournament.checkTeam(team)
            return checkNotNull(tournament.getPlayer(player)).also(::setPlayerPrepared)
        }

        fun clearParticipation(player: PluginPlayer) {
            Tournament::class.java.getDeclaredMethod(
                "clearPlayer", PluginPlayer::class.java, PluginTeam::class.java
            ).apply { isAccessible = true }.invoke(tournament, player, null)
        }

        fun progress(initial: Set<String>): FakeProgress = FakeProgress(initial.toMutableSet())

        fun bindProgress(player: PluginPlayer, progress: FakeProgress) {
            val bukkitPlayer = online.getValue(player.uuid)
            progressByPlayer[player.uuid] = progress.proxy
            progress.onComplete = { complete(player) }
        }

        fun complete(player: PluginPlayer) {
            val event = PlayerAdvancementDoneEvent(online.getValue(player.uuid), advancement)
            handler.javaClass.getDeclaredMethod("handle", PlayerAdvancementDoneEvent::class.java)
                .apply { isAccessible = true }.invoke(handler, event)
        }

        fun reconnect(player: PluginPlayer) {
            handler.javaClass.getDeclaredMethod("handleJoin", Player::class.java)
                .apply { isAccessible = true }.invoke(handler, online.getValue(player.uuid))
        }

        fun member(name: String, isOnline: Boolean = true): PluginPlayer {
            val uuid = UUID.randomUUID()
            val player: Player = proxy { method, args ->
                when (method) {
                    "getUniqueId" -> uuid
                    "getAdvancementProgress" -> progressByPlayer[uuid]
                    "getName" -> name
                    "getMaxHealth" -> 20.0
                    "setHealth", "setFoodLevel", "setSaturation", "setRespawnLocation",
                    "setVelocity", "setExp", "setLevel", "setTotalExperience" -> null
                    "clearActivePotionEffects" -> true
                    "setGameMode" -> { gameModes[uuid] = args!![0] as GameMode; null }
                    "getGameMode" -> gameModes[uuid]
                    "getInventory" -> proxy<org.bukkit.inventory.PlayerInventory> { inventoryMethod, _ ->
                        if (inventoryMethod == "clear") null else unexpected(inventoryMethod)
                    }
                    "getServer" -> serverProxy
                    "teleport" -> true
                    else -> null
                }
            }
            online[uuid] = player
            if (isOnline) onlineIds.add(uuid)
            return PluginPlayer.get(uuid)
        }

        private fun setPlayerPrepared(player: TournamentPlayer) {
            markTournamentPlayerPrepared(player)
        }

        private fun setServer(): Any? {
            serverProxy = proxy { method, args ->
                when (method) {
                    "getPlayer" -> online[args!![0] as UUID].takeIf { args[0] in onlineIds }
                    "getOfflinePlayer" -> null
                    "getAdvancement" -> advancement
                    "advancementIterator" -> listOf(advancement).iterator()
                    else -> null
                }
            }
            val field = Bukkit::class.java.getDeclaredField("server").apply { isAccessible = true }
            return field.get(null).also { field.set(null, serverProxy) }
        }

        private lateinit var serverProxy: Server

        override fun close() {
            Bukkit::class.java.getDeclaredField("server").apply { isAccessible = true }
                .set(null, previousServer)
        }
    }

    private inner class FakeProgress(val awarded: MutableSet<String>) {
        var onComplete: (() -> Unit)? = null
        val proxy: AdvancementProgress = proxy { method, args ->
            when (method) {
                "getAwardedCriteria" -> awarded.toSet()
                "getRemainingCriteria" -> setOf("first", "second") - awarded
                "isDone" -> awarded.isNotEmpty()
                "revokeCriteria" -> awarded.remove(args!![0] as String)
                "awardCriteria" -> {
                    val changed = awarded.add(args!![0] as String)
                    if (changed && awarded.isNotEmpty()) onComplete?.invoke()
                    changed
                }
                else -> unexpected(method)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> proxy(noinline handler: (String, Array<out Any?>?) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            handler(method.name, args)
        } as T

    private fun unexpected(method: String): Nothing = error("Unexpected API call: $method")
}
