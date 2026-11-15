package dev.necr0manthre.innotournament.commands

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import dev.necr0manthre.innotournament.presentation.map.MapDeliveryResult
import dev.necr0manthre.innotournament.presentation.map.MapItemDelivery
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player

/** Subnode for a shared `/inno` tree; player access is public and independent of admin permissions. */
object InnoMapCommand {
    fun build(delivery: MapItemDelivery): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("map")
            .executes { context ->
                val sender = context.source.sender
                if (sender !is Player) {
                    sender.sendMessage(Component.text("Only players can receive a map.", NamedTextColor.RED))
                    return@executes 0
                }

                val result = delivery.ensureCurrentWorldMap(sender)
                when (result) {
                    MapDeliveryResult.DELIVERED -> sender.sendMessage(
                        Component.text("Your map is ready.", NamedTextColor.GREEN)
                    )
                    MapDeliveryResult.ALREADY_CURRENT -> sender.sendMessage(
                        Component.text("You already have the current map for this world.", NamedTextColor.YELLOW)
                    )
                    MapDeliveryResult.DROPPED -> sender.sendMessage(
                        Component.text("Your inventory is full; the map was dropped nearby.", NamedTextColor.YELLOW)
                    )
                    MapDeliveryResult.UNAVAILABLE -> sender.sendMessage(
                        Component.text("No map is available in this world.", NamedTextColor.RED)
                    )
                }
                if (result == MapDeliveryResult.UNAVAILABLE) 0 else 1
            }
}
