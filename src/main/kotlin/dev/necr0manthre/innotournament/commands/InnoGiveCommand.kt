package dev.necr0manthre.innotournament.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import dev.necr0manthre.innotournament.generators.GeneratorItems
import dev.necr0manthre.innotournament.generators.SpecialItemType
import dev.necr0manthre.innotournament.util.CommandRegisterer
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit

/** Administrative entrypoint for the four PDC-backed special items, not vanilla registry IDs. */
object InnoGiveCommand {
    private const val USAGE = "Usage: /inno give <player> <item_generator|book_generator|nether_generator|inno_dice> [count]"

    /** Subnode for a shared `/inno` tree; does not register or initialize the plugin. */
    fun build(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("give")
            .requires { it.sender.hasPermission("innotournament.admin") }
            .executes { ctx ->
                error(ctx, USAGE)
            }
            .then(
                Commands.argument<String>("player", StringArgumentType.word())
                    .suggests { _, builder ->
                        Bukkit.getOnlinePlayers().forEach { player ->
                            if (player.name.startsWith(builder.remaining, ignoreCase = true)) builder.suggest(player.name)
                        }
                        builder.buildFuture()
                    }
                    .executes { ctx -> error(ctx, USAGE) }
                    .then(
                        Commands.argument<String>("item", StringArgumentType.word())
                            .suggests { _, builder ->
                                SpecialItemType.entries.forEach { type ->
                                    if (type.id.startsWith(builder.remaining, ignoreCase = true)) builder.suggest(type.id)
                                }
                                builder.buildFuture()
                            }
                            .executes { ctx -> give(ctx, 1) }
                            .then(
                                Commands.argument<Int>("count", IntegerArgumentType.integer())
                                    .executes { ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "count")) }
                            )
                    )
            )

    /** Convenience registration when `/inno` has no other subcommands; integration may use [build]. */
    fun register(registrar: Commands) {
        val registerer = CommandRegisterer(registrar, "inno")
        registerer.add(build())
        registerer.register()
    }

    private fun give(ctx: CommandContext<CommandSourceStack>, count: Int): Int {
        val id = StringArgumentType.getString(ctx, "item")
        val type = SpecialItemType.entries.firstOrNull { it.id == id }
            ?: return error(ctx, "Unknown special item '$id'. Choose: ${SpecialItemType.entries.joinToString { it.id }}")
        if (count !in 1..type.material.maxStackSize) {
            return error(ctx, "Count for '${type.id}' must be between 1 and ${type.material.maxStackSize}.")
        }

        val name = StringArgumentType.getString(ctx, "player")
        val player = Bukkit.getOnlinePlayers().firstOrNull { it.name == name }
            ?: Bukkit.getPlayerExact(name)
            ?: return error(ctx, "Player '$name' is not online. Use an online player's exact name.")

        // The factory supplies both authoritative PDC keys and the visual selector on a fresh stack.
        val stack = GeneratorItems().create(type, count)
        val overflow = player.inventory.addItem(stack).values
        var dropped = 0
        for (leftover in overflow) {
            player.world.dropItemNaturally(player.location, leftover)
            dropped += leftover.amount
        }
        val suffix = if (dropped > 0) " ($dropped dropped by the player: inventory full)" else ""
        ctx.source.sender.sendMessage(Component.text("Gave $count ${type.id} to ${player.name}$suffix", NamedTextColor.GREEN))
        return Command.SINGLE_SUCCESS
    }

    private fun error(ctx: CommandContext<CommandSourceStack>, message: String): Int {
        ctx.source.sender.sendMessage(Component.text(message, NamedTextColor.RED))
        return 0
    }
}
