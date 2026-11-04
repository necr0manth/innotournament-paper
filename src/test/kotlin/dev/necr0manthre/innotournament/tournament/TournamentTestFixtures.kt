package dev.necr0manthre.innotournament.tournament

import dev.necr0manthre.innotournament.teams.PluginTeam.TeamManager
import dev.necr0manthre.innotournament.tournament.environment.PluginChunkTickets
import dev.necr0manthre.innotournament.tournament.environment.ResolvedWorldBindings
import dev.necr0manthre.innotournament.tournament.environment.TournamentEnvironment
import dev.necr0manthre.innotournament.tournament.environment.WorldPoint
import dev.necr0manthre.innotournament.tournament.events.EventPlan
import org.bukkit.GameMode
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.OfflinePlayer
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.entity.Player
import net.kyori.adventure.text.Component
import org.bukkit.plugin.Plugin
import org.bukkit.scoreboard.ScoreboardManager
import org.bukkit.structure.StructureManager
import java.lang.reflect.Proxy
import java.util.UUID

internal fun testTournament(
    teamManager: TeamManager,
    scoreModifiers: Collection<Double> = emptyList(),
    lastTeamScore: Float = 0f,
    prePlan: EventPlan = EventPlan.of(emptyList()),
    mainPlan: EventPlan = EventPlan.of(emptyList())
): Tournament {
    val worldId = UUID.randomUUID()
    val world = testProxy<World> { method, args ->
        when (method) {
            "getKey" -> NamespacedKey.minecraft("overworld")
            "getUID" -> worldId
            "equals" -> (args?.singleOrNull() as? World)?.uid == worldId
            "hashCode" -> worldId.hashCode()
            "toString" -> "test world"
            else -> unexpectedTestCall(method)
        }
    }
    val plugin = testProxy<Plugin> { method, _ ->
        if (method == "getName") "test" else unexpectedTestCall(method)
    }
    val settings = TournamentSettings(
        BlockPosition(0, 64, 0), NamespacedKey.minecraft("overworld"),
        BlockPosition(0, 64, 0), NamespacedKey.minecraft("overworld"),
        NamespacedKey("innotournament", "start_box"), BlockPosition(0, 64, 0), GameMode.SURVIVAL,
        java.nio.file.Path.of("pre.json"), java.nio.file.Path.of("main.json"),
        scoreModifiers, lastTeamScore
    )
    val point = WorldPoint(world, 0.0, 64.0, 0.0)
    val environment = TournamentEnvironment(
        ResolvedWorldBindings(world, world, world, world, world),
        point,
        point,
        PluginChunkTickets(plugin),
        plugin,
        testProxy<StructureManager> { method, _ -> unexpectedTestCall(method) },
        null,
        point
    )
    val presentation = TournamentPresentation(
        plugin,
        TournamentSidebarTransport(testProxy<ScoreboardManager> { method, _ -> unexpectedTestCall(method) })
    ) { { _ -> TournamentLiveSidebarBuilder.Sidebar(Component.text("Live"), emptyList()) } }

    return Tournament(
        teamManager,
        LoadedTournamentDefinition(settings, prePlan, mainPlan),
        environment,
        TournamentPlayerEffects(environment),
        presentation,
        plugin,
        {},
        {}
    )
}

internal fun markTournamentPlayerPrepared(player: TournamentPlayer) {
    val field = player.javaClass.getDeclaredField("preparation").apply { isAccessible = true }
    val ready = field.type.enumConstants.single { (it as Enum<*>).name == "READY" }
    field.set(player, ready)
}

internal fun activateTournamentPresentationForTest(tournament: Tournament) {
    val field = Tournament::class.java.getDeclaredField("presentation").apply { isAccessible = true }
    (field.get(tournament) as TournamentPresentation).activate()
}

internal fun <T> withPrimaryTestServer(action: () -> T): T {
    val field = Bukkit::class.java.getDeclaredField("server").apply { isAccessible = true }
    val previous = field.get(null)
    val server = testProxy<Server> { method, _ ->
        when (method) {
            "isPrimaryThread" -> true
            "getOnlinePlayers" -> emptyList<Player>()
            "getOfflinePlayer" -> testProxy<OfflinePlayer> { method, _ ->
                if (method == "getName") "Saved" else unexpectedTestCall(method)
            }
            else -> unexpectedTestCall(method)
        }
    }
    field.set(null, server)
    return try {
        action()
    } finally {
        field.set(null, previous)
    }
}

@Suppress("UNCHECKED_CAST")
private inline fun <reified T> testProxy(noinline handler: (String, Array<out Any?>?) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
        handler(method.name, args)
    } as T

private fun unexpectedTestCall(method: String): Nothing = error("Unexpected test fixture API call: $method")
