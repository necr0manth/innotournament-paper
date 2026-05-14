package dev.necr0manthre.innotournament.util

import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.tree.CommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands


class CommandRegisterer(val registrar: Commands, private vararg val aliases: String) {

    fun register(argumentBuilder: ArgumentBuilder<CommandSourceStack?, *>?): List<String> {
        return buildList {
            for (alias in aliases) {
                addAll(registrar.register(Commands.literal(alias).then(argumentBuilder).build()))
            }
        }
    }

    fun register(argument: CommandNode<CommandSourceStack?>?): List<String> {
        return buildList {
            for (alias in aliases) {
                addAll(registrar.register(Commands.literal(alias).then(argument).build()))
            }
        }
    }
}
