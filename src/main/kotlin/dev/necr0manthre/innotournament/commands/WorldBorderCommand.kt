package dev.necr0manthre.innotournament.commands

import com.mojang.brigadier.Command
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.entity.Player
import kotlin.math.abs

object WorldBorderCommand {
    fun register(
        commands: Commands
    ) {
        commands.register(
            Commands.literal("worldborderinfo")
                .executes { ctx ->
                    val player = ctx.source.executor as? Player ?: return@executes 0
                    val worldBorder = player.world.worldBorder
                    val centerX = worldBorder.center.x
                    val centerZ = worldBorder.center.z
                    val r = worldBorder.size / 2
                    val x: Double = player.x
                    val z: Double = player.z
                    val xp = abs(centerX + r - x)
                    val xn = abs(centerX - r - x)
                    val zp = abs(centerZ + r - z)
                    val zn = abs(centerZ - r - z)
                    val min = kotlin.math.min(xp, kotlin.math.min(xn, kotlin.math.min(zp, zn)))
                    ctx.source.sender.sendMessage("Worldborder is " + min + " blocks from you to " + (if (xp == min) "x+" else if (xn == min) "x-" else if (zp == min) "z+" else "z-"))
                    Command.SINGLE_SUCCESS
                }.build()
        )
    }
}
