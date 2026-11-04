package dev.necr0manthre.innotournament.commands

import com.mojang.brigadier.tree.LiteralCommandNode
import dev.necr0manthre.innotournament.teams.PluginTeam.TeamManager
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.command.CommandSender
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class TournamentCommandPermissionTest {
    @Test
    fun administrativeNodesRequireThePluginPermission() {
        var root: LiteralCommandNode<CommandSourceStack>? = null
        val registrar = Proxy.newProxyInstance(
            Commands::class.java.classLoader,
            arrayOf(Commands::class.java)
        ) { _, method, args ->
            check(method.name == "register")
            @Suppress("UNCHECKED_CAST")
            val commandRoot = args?.singleOrNull() as? LiteralCommandNode<CommandSourceStack>
                ?: error("Unexpected command registration call")
            root = commandRoot
            setOf(commandRoot.literal)
        } as Commands

        TournamentCommand(TeamManager()).register(registrar)

        val commands = root!!.children.associateBy { it.name }
        val protected = mapOf(
            "select" to commands.getValue("select"),
            "prepare" to commands.getValue("prepare"),
            "start" to commands.getValue("start"),
            "stop" to commands.getValue("stop")
        )
        protected.values.forEach { node ->
            assertFalse(node!!.requirement.test(sourceWithPermissions("minecraft.command.function")))
            assertFalse(node.requirement.test(sourceWithPermissions("minecraft.command.team")))
            assertFalse(node.requirement.test(sourceWithPermissions()))
            assertTrue(node.requirement.test(sourceWithPermissions("innotournament.admin")))
            assertTrue(
                node.requirement.test(
                    sourceWithPermissions("innotournament.admin", "minecraft.command.function")
                )
            )
        }
    }

    private fun sourceWithPermissions(vararg permissions: String): CommandSourceStack {
        val sender = Proxy.newProxyInstance(
            CommandSender::class.java.classLoader,
            arrayOf(CommandSender::class.java)
        ) { _, method, args ->
            when (method.name) {
                "hasPermission" -> permissions.contains(args?.singleOrNull())
                "toString" -> "TestCommandSender"
                "hashCode" -> System.identityHashCode(this)
                "equals" -> this === args?.singleOrNull()
                else -> null
            }
        } as CommandSender
        return Proxy.newProxyInstance(
            CommandSourceStack::class.java.classLoader,
            arrayOf(CommandSourceStack::class.java)
        ) { _, method, _ ->
            if (method.name == "getSender") sender else null
        } as CommandSourceStack
    }
}
