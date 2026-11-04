package dev.necr0manthre.innotournament.tournament

import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.teams.PluginTeam
import dev.necr0manthre.innotournament.teams.TeamCreateResult
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
        val sourceProgress = fixture.progress(setOf("first", "second"))
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
        assertEquals(listOf("first", "second"), teammateProgress.awarded.toList())
        assertEquals(listOf(sourceEntry), personalBridgeValues.map { it.player })
        assertEquals(listOf(fixture.advancement.key), teamBridgeValues.map { it.advancement?.key })
        assertEquals(setOf(fixture.advancement.key), fixture.tournament.teams().single().advancements)

        teammateProgress.awarded.clear()
        fixture.reconnect(teammate)
        assertEquals(setOf("first", "second"), teammateProgress.awarded)
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
        val sourceProgress = fixture.progress(setOf("first", "second"))
        val teammateProgress = fixture.progress(emptySet())
        fixture.bindProgress(source, sourceProgress)
        fixture.bindProgress(teammate, teammateProgress)
        fixture.tournament.checkTeam(fixture.team)
        fixture.prepare(teammate)

        val notices = mutableListOf<Any>()
        fixture.tournament.realPlayerAdvancements.subscribe(notices::add)
        fixture.tournament.firstTeamAdvancements.subscribe(notices::add)
        sourceProgress.awarded.add("first")
        fixture.complete(source)
        assertTrue(notices.isEmpty())
        assertTrue(teammateProgress.awarded.isEmpty())

        sourceProgress.awarded.add("second")
        val entry = checkNotNull(fixture.tournament.getPlayer(source))
        entry.javaClass.getDeclaredMethod("markPrepared").apply { isAccessible = true }.invoke(entry)
        fixture.complete(source)
        assertEquals(2, notices.size)
        assertEquals(setOf("first", "second"), teammateProgress.awarded)
        } finally {
            fixture.close()
        }
    }

    private inner class Fixture : AutoCloseable {
        private val online = LinkedHashMap<UUID, Player>()
        private val progressByPlayer = LinkedHashMap<UUID, AdvancementProgress>()
        private val previousServer = setServer()
        private val manager = PluginTeam.TeamManager()
        private val owner = member("adv-owner")
        val team = (manager.createNewTeam(owner, "adv-team", Component.text("adv-team")) as
            TeamCreateResult.Created).team
        val tournament = Tournament(
            manager,
            Location(null, 0.0, 0.0, 0.0),
            Location(null, 0.0, 0.0, 0.0),
            null,
            Location(null, 0.0, 0.0, 0.0),
            GameMode.SURVIVAL,
            EventPlan.of(emptyList()),
            EventPlan.of(emptyList()),
            doubleArrayOf(),
            0f
        )
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

        private fun member(name: String): PluginPlayer {
            val uuid = UUID.randomUUID()
            val player: Player = proxy { method, _ ->
                when (method) {
                    "getUniqueId" -> uuid
                    "getAdvancementProgress" -> progressByPlayer[uuid]
                    "getName" -> name
                    else -> null
                }
            }
            online[uuid] = player
            return PluginPlayer.get(uuid)
        }

        private fun setPlayerPrepared(player: TournamentPlayer) {
            player.javaClass.getDeclaredMethod("markPrepared").apply { isAccessible = true }.invoke(player)
        }

        private fun setServer(): Any? {
            val server: Server = proxy { method, args ->
                when (method) {
                    "getPlayer" -> online[args!![0] as UUID]
                    "getOfflinePlayer" -> null
                    "getAdvancement" -> advancement
                    else -> null
                }
            }
            val field = Bukkit::class.java.getDeclaredField("server").apply { isAccessible = true }
            return field.get(null).also { field.set(null, server) }
        }

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
                "isDone" -> awarded.containsAll(setOf("first", "second"))
                "awardCriteria" -> {
                    val changed = awarded.add(args!![0] as String)
                    if (changed && awarded.containsAll(setOf("first", "second"))) onComplete?.invoke()
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
