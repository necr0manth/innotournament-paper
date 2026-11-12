package dev.necr0manthre.innotournament.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.CommandContextBuilder
import com.mojang.brigadier.context.ParsedArgument
import com.mojang.brigadier.tree.LiteralCommandNode
import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.teams.PluginTeam
import dev.necr0manthre.innotournament.teams.PluginTeam.TeamManager
import dev.necr0manthre.innotournament.teams.TeamCreateResult
import dev.necr0manthre.innotournament.teams.invites.InviteSystem
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.PaperCommands
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.minecraft.commands.CommandBuildContext
import net.minecraft.core.RegistryAccess
import net.minecraft.world.flag.FeatureFlags
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.Server
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.UUID

class PlayerNameLookupTest {
    @Test
    fun exactSpellingKeepsCaseVariantPlayersSeparate() {
        PaperCommandTestBootstrap.ensure()
        val ids = List(3) { UUID.randomUUID() }
        val names = listOf("Owner", "alice", "Alice")
        val players = names.mapIndexed { index, name ->
            proxy<Player> { method, _ ->
                when (method) {
                    "getUniqueId" -> ids[index]
                    "getName" -> name
                    "displayName" -> Component.text(name)
                    "sendMessage" -> null
                    else -> error("Unexpected player call: $method")
                }
            }
        }
        var online = players
        var historyReads = 0
        val server = proxy<Server> { method, args ->
            when (method) {
                "getOnlinePlayers" -> online
                "getPlayerExact" -> online.firstOrNull { it.name.equals(args!!.single() as String, true) }
                "getOfflinePlayerIfCached" -> players[1].takeIf { it.name.equals(args!!.single() as String, true) }
                "getOfflinePlayers" -> { historyReads++; players.toTypedArray() }
                "getPlayer", "getOfflinePlayer" -> players.firstOrNull { it.uniqueId == args!!.single() }
                else -> error("Unexpected server call: $method")
            }
        }
        val source = proxy<CommandSourceStack> { method, _ ->
            check(method == "getSender")
            players[0]
        }
        val manager = TeamManager()
        val command = TeamsCommand(manager, InviteSystem(manager))
        val dispatcher = CommandDispatcher<CommandSourceStack>()
        val registrar = proxy<Commands> { method, args ->
            check(method == "register")
            @Suppress("UNCHECKED_CAST")
            val node = args!!.single() as LiteralCommandNode<CommandSourceStack>
            dispatcher.root.addChild(node)
            setOf(node.literal)
        }
        val lookup = TeamsCommand::class.java.getDeclaredMethod("getTargetPlayer", CommandContext::class.java)
            .apply { isAccessible = true }
        fun resolve(name: String): PluginPlayer? {
            val ctx = CommandContextBuilder(dispatcher, source, dispatcher.root, 0)
                .withArgument("player", ParsedArgument<CommandSourceStack, String>(0, name.length, name))
                .build(name)
            return lookup.invoke(command, ctx) as PluginPlayer?
        }
        val serverField = Bukkit::class.java.getDeclaredField("server").apply { isAccessible = true }
        val previousServer = serverField.get(null)
        serverField.set(null, server)
        val buildContext = PaperCommands::class.java.getDeclaredField("buildContext").apply { isAccessible = true }
        val previousContext = buildContext.get(PaperCommands.INSTANCE)
        buildContext.set(PaperCommands.INSTANCE, CommandBuildContext.simple(RegistryAccess.EMPTY, FeatureFlags.DEFAULT_FLAGS))
        var team: PluginTeam? = null
        try {
            assertSame(PluginPlayer.get(ids[1]), resolve("alice"))
            assertSame(PluginPlayer.get(ids[2]), resolve("Alice"))
            assertSame(PluginPlayer.get(ids[1]), resolve("ALICE"))
            assertEquals(0, historyReads)
            team = (manager.createNewTeam(PluginPlayer.get(ids[0]), "case", Component.text("Case"))
                as TeamCreateResult.Created).team
            team.tryAddPlayerToTeam(PluginPlayer.get(ids[1]))
            team.tryAddPlayerToTeam(PluginPlayer.get(ids[2]))
            command.register(registrar)
            assertEquals(1, dispatcher.execute("teams kick Alice", source))
            assertNull(PluginPlayer.get(ids[2]).team)
            assertSame(team, PluginPlayer.get(ids[1]).team)

            online = emptyList()
            assertSame(PluginPlayer.get(ids[1]), resolve("alice"))
            assertEquals(0, historyReads)
            assertSame(PluginPlayer.get(ids[2]), resolve("Alice"))
            assertEquals(1, historyReads)
        } finally {
            team?.let(manager::removeTeam)
            buildContext.set(PaperCommands.INSTANCE, previousContext)
            serverField.set(null, previousServer)
        }
    }

    @Test
    fun looksUpNamesInPriorityOrderAndUsesNamesInMemberButtons() {
        val calls = mutableListOf<String>()
        val messages = mutableListOf<Component>()
        val onlineId = UUID(1, 1)
        val cachedId = UUID(1, 2)
        val historyId = UUID(1, 3)
        val online = proxy<Player> { method, _ ->
            when (method) {
                "getUniqueId" -> onlineId
                "getName" -> "Online"
                "displayName" -> Component.text("Online")
                else -> error("Unexpected player call: $method")
            }
        }
        fun offline(id: UUID, name: String?) = proxy<OfflinePlayer> { method, _ ->
            when (method) {
                "getUniqueId" -> id
                "getName" -> name
                else -> error("Unexpected offline player call: $method")
            }
        }
        val cached = offline(cachedId, "Cached")
        val history = arrayOf(offline(UUID(1, 4), null), offline(historyId, "Known"), offline(UUID(1, 5), "Known"))
        val server = proxy<Server> { method, args ->
            calls += method
            when (method) {
                "getOnlinePlayers" -> listOf(online)
                "getPlayerExact" -> if (args?.single() == "Online") online else null
                "getOfflinePlayerIfCached" -> if (args?.single() == "Cached") cached else null
                "getOfflinePlayers" -> history
                "getPlayer" -> if (args?.single() == onlineId) online else null
                "getOfflinePlayer" -> if (args?.single() == cachedId) cached else error("Unexpected offline player")
                else -> error("Unexpected server call: $method")
            }
        }
        val sender = proxy<CommandSender> { method, args ->
            check(method == "sendMessage")
            messages += args!!.single() as Component
            null
        }
        val source = proxy<CommandSourceStack> { method, _ ->
            check(method == "getSender")
            sender
        }
        val manager = TeamManager()
        val command = TeamsCommand(manager, InviteSystem(manager))
        val lookup = TeamsCommand::class.java.getDeclaredMethod("getTargetPlayer", CommandContext::class.java)
            .apply { isAccessible = true }
        fun resolve(name: String): PluginPlayer? {
            val dispatcher = CommandDispatcher<CommandSourceStack>()
            val ctx = CommandContextBuilder(dispatcher, source, dispatcher.root, 0)
                .withArgument("player", ParsedArgument<CommandSourceStack, String>(0, name.length, name))
                .build(name)
            return lookup.invoke(command, ctx) as PluginPlayer?
        }
        val serverField = Bukkit::class.java.getDeclaredField("server").apply { isAccessible = true }
        val previousServer = serverField.get(null)
        serverField.set(null, server)
        try {
            assertSame(PluginPlayer.get(onlineId), resolve("Online"))
            assertEquals(listOf("getOnlinePlayers"), calls)
            calls.clear()
            assertSame(PluginPlayer.get(cachedId), resolve("Cached"))
            assertEquals(listOf("getOnlinePlayers", "getPlayerExact", "getOfflinePlayerIfCached"), calls)
            calls.clear()
            assertSame(PluginPlayer.get(historyId), resolve("kNoWn"))
            assertEquals(listOf("getOnlinePlayers", "getPlayerExact", "getOfflinePlayerIfCached", "getOfflinePlayers"), calls)
            assertNull(resolve("Missing"))
            assertNull(resolve(historyId.toString()))
            assertEquals(List(2) { Component.text("Player not found in local server data", NamedTextColor.RED) }, messages)

            val team = (manager.createNewTeam(PluginPlayer.get(onlineId), "names", Component.text("Names"))
                as TeamCreateResult.Created).team
            try {
                team.tryAddPlayerToTeam(PluginPlayer.get(cachedId))
                val memberLine = TeamsCommand::class.java.getDeclaredMethod(
                    "memberLine", PluginPlayer::class.java, PluginTeam::class.java,
                    Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType
                ).apply { isAccessible = true }
                val line = memberLine.invoke(command, PluginPlayer.get(cachedId), team, false, true) as Component
                assertEquals(listOf(ClickEvent.runCommand("/teams tryKick Cached")), clickEvents(line))
            } finally {
                manager.removeTeam(team)
            }
        } finally {
            serverField.set(null, previousServer)
        }
    }

    private fun clickEvents(component: Component): List<ClickEvent> {
        return listOfNotNull(component.clickEvent()) + component.children().flatMap(::clickEvents)
    }

    private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>?) -> Any?): T {
        return Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            call(method.name, args)
        } as T
    }
}
