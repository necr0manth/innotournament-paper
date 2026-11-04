package dev.necr0manthre.innotournament.util

import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.tree.CommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands


class CommandRegisterer(private val registrar: Commands, private vararg val aliases: String) {
    private val branches = mutableListOf<(LiteralArgumentBuilder<CommandSourceStack>) -> Unit>()

    fun add(argumentBuilder: ArgumentBuilder<CommandSourceStack, *>) {
        branches.add { root -> root.then(argumentBuilder) }
    }

    fun add(argument: CommandNode<CommandSourceStack>) {
        branches.add { root -> root.then(argument) }
    }

    fun register(): List<String> {
        return buildList {
            for (alias in aliases) {
                val root = Commands.literal(alias)
                branches.forEach { it(root) }
                addAll(registrar.register(root.build()))
            }
        }
    }
}
