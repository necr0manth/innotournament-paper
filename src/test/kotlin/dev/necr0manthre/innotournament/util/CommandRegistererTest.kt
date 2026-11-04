package dev.necr0manthre.innotournament.util

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.function.Predicate

class CommandRegistererTest {
    @Test
    fun publishesAllBranchesUnderEachAliasOnce() {
        val roots = mutableListOf<LiteralCommandNode<CommandSourceStack>>()
        val registrar = Proxy.newProxyInstance(
            Commands::class.java.classLoader,
            arrayOf(Commands::class.java)
        ) { _, method, args ->
            check(method.name == "register")
            val node = args?.singleOrNull() as? LiteralCommandNode<*>
                ?: error("Unexpected command registration call")
            @Suppress("UNCHECKED_CAST")
            val root = node as LiteralCommandNode<CommandSourceStack>
            roots.add(root)
            setOf(root.literal)
        } as Commands

        val registerer = CommandRegisterer(registrar, "teams", "p")
        val restricted = Predicate<CommandSourceStack> { it.sender.hasPermission("innotournament.admin") }
        registerer.add(Commands.literal("create").then(Commands.argument("name", StringArgumentType.word())))
        registerer.add(Commands.literal("list").build()) // Pre-built node overload
        registerer.add(Commands.literal("removeAll").requires(restricted))

        assertTrue(roots.isEmpty()) // Branch addition must never publish a partial root.
        assertEquals(listOf("teams", "p"), registerer.register())
        assertEquals(listOf("teams", "p"), roots.map { it.literal })
        roots.forEach { root ->
            assertEquals(setOf("create", "list", "removeAll"), root.children.map { it.name }.toSet())
            assertNotNull(root.getChild("create")?.getChild("name"))
            assertSame(restricted, root.getChild("removeAll")?.requirement)
        }
    }
}
