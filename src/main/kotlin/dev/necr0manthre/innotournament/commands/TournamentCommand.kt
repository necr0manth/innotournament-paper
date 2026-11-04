package dev.necr0manthre.innotournament.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import dev.necr0manthre.innotournament.tournament.TournamentConfigEditor
import dev.necr0manthre.innotournament.tournament.TournamentControl
import dev.necr0manthre.innotournament.util.CommandRegisterer
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import java.nio.file.Files
import java.nio.file.Path

class TournamentCommand(
    private val control: TournamentControl,
    private val editor: TournamentConfigEditor,
    private val dataFolder: Path
) {
    fun register(registrar: Commands) {
        val registerer = CommandRegisterer(registrar, "tournament")

        registerer.add(
            Commands.literal("help")
                .executes { ctx ->
                    ctx.source.sender.sendMessage(
                        Component.text("All info about tournament is here ")
                            .append(
                                Component.text("https://telegra.ph/Majnkraft-turnir-09-21")
                                    .clickEvent(ClickEvent.openUrl("https://telegra.ph/Majnkraft-turnir-09-21"))
                            )
                    )
                    Command.SINGLE_SUCCESS
                }
        )

        registerer.add(TournamentConfigSetCommand.build(control, editor))

        registerer.add(
            Commands.literal("select")
                .requires { it.sender.hasPermission("innotournament.admin") }
                .then(
                    Commands.argument<String>("filePath", StringArgumentType.string())
                        .suggests { _, builder ->
                            val folder = tournamentsFolder()
                            if (Files.isDirectory(folder)) {
                                Files.list(folder).use { files ->
                                    files.filter { it.fileName.toString().endsWith(".json") }
                                        .forEach { builder.suggest(it.fileName.toString()) }
                                }
                            }
                            builder.buildFuture()
                        }
                        .executes { ctx ->
                            val folder = tournamentsFolder()
                            val path = folder.resolve(StringArgumentType.getString(ctx, "filePath"))
                                .toAbsolutePath().normalize()
                            if (!path.startsWith(folder)) {
                                ctx.source.sender.sendMessage(
                                    Component.text("Tournament files must be inside $folder", NamedTextColor.RED)
                                )
                                return@executes 0
                            }
                            control.select(path)
                            ctx.source.sender.sendMessage(Component.text("Selected tournament $path"))
                            Command.SINGLE_SUCCESS
                        }
                )
        )

        registerer.add(
            Commands.literal("prepare")
                .requires { it.sender.hasPermission("innotournament.admin") }
                .executes { ctx ->
                    try {
                        control.prepare()
                        ctx.source.sender.sendMessage(Component.text("Tournament prepared", NamedTextColor.GREEN))
                        Command.SINGLE_SUCCESS
                    } catch (failure: Exception) {
                        ctx.source.sender.sendMessage(
                            Component.text("Something went wrong while preparing tournament: ${failure.message}", NamedTextColor.RED)
                        )
                        0
                    }
                }
        )

        registerer.add(
            Commands.literal("start")
                .requires { it.sender.hasPermission("innotournament.admin") }
                .executes { ctx ->
                    try {
                        control.start()
                        ctx.source.sender.sendMessage(Component.text("Tournament started", NamedTextColor.GREEN))
                        Command.SINGLE_SUCCESS
                    } catch (failure: Exception) {
                        ctx.source.sender.sendMessage(
                            Component.text("Something went wrong while starting tournament: ${failure.message}", NamedTextColor.RED)
                        )
                        0
                    }
                }
        )

        registerer.add(
            Commands.literal("stop")
                .requires { it.sender.hasPermission("innotournament.admin") }
                .executes { ctx ->
                    control.stop()
                    ctx.source.sender.sendMessage(Component.text("Tournament stopped", NamedTextColor.YELLOW))
                    Command.SINGLE_SUCCESS
                }
        )

        registerer.register()
    }

    private fun tournamentsFolder(): Path = dataFolder.resolve("tournaments").toAbsolutePath().normalize()
}
