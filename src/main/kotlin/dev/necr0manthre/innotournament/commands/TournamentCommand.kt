package dev.necr0manthre.innotournament.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import dev.necr0manthre.innotournament.Innotournament
import dev.necr0manthre.innotournament.teams.PluginTeam.TeamManager
import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.TournamentData
import dev.necr0manthre.innotournament.tournament.TournamentPhase
import dev.necr0manthre.innotournament.util.CommandRegisterer
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class TournamentCommand(private val teamManager: TeamManager) {
    private var selectedTournamentPath: Path? = null

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

        registerer.add(
            Commands.literal("select")
                .requires { it.sender.hasPermission("minecraft.command.function") }
                .then(
                    Commands.argument<String>("filePath", StringArgumentType.string())
                        .suggests { _, builder ->
                            Files.createDirectories(tournamentsFolder())
                            Files.list(tournamentsFolder()).use { files ->
                                files.filter { it.fileName.toString().endsWith(".json") }
                                    .forEach { builder.suggest(it.fileName.toString()) }
                            }
                            builder.buildFuture()
                        }
                        .executes { ctx ->
                            selectedTournamentPath = tournamentsFolder().resolve(StringArgumentType.getString(ctx, "filePath"))
                            ctx.source.sender.sendMessage(Component.text("Selected tournament $selectedTournamentPath"))
                            Command.SINGLE_SUCCESS
                        }
                )
        )

        registerer.add(
            Commands.literal("prepare")
                .requires { it.sender.hasPermission("minecraft.command.function") }
                .executes { ctx ->
                    val tournament = ensureTournament(ctx) ?: return@executes 0
                    try {
                        tournament.prepare()
                        ctx.source.sender.sendMessage(Component.text("Tournament prepared", NamedTextColor.GREEN))
                    } catch (e: Exception) {
                        ctx.source.sender.sendMessage(
                            Component.text("Something went wrong while preparing tournament: ${e.message}", NamedTextColor.RED)
                        )
                    }
                    Command.SINGLE_SUCCESS
                }
        )

        registerer.add(
            Commands.literal("start")
                .requires { it.sender.hasPermission("minecraft.command.function") }
                .executes { ctx ->
                    val tournament = ensureTournament(ctx) ?: return@executes 0
                    try {
                        if (tournament.phase == TournamentPhase.CREATED) tournament.prepare()
                        tournament.start()
                        ctx.source.sender.sendMessage(Component.text("Tournament started", NamedTextColor.GREEN))
                    } catch (e: Exception) {
                        ctx.source.sender.sendMessage(
                            Component.text("Something went wrong while starting tournament: ${e.message}", NamedTextColor.RED)
                        )
                    }
                    Command.SINGLE_SUCCESS
                }
        )

        registerer.add(
            Commands.literal("stop")
                .requires { it.sender.hasPermission("minecraft.command.function") }
                .executes {
                    Tournament.setTournament(null)
                    Command.SINGLE_SUCCESS
                }
        )

        registerer.register()
    }

    private fun ensureTournament(ctx: com.mojang.brigadier.context.CommandContext<CommandSourceStack>): Tournament? {
        Tournament.get()?.let { return it }
        val path = selectedTournamentPath
        if (path == null) {
            ctx.source.sender.sendMessage(Component.text("No tournament selected", NamedTextColor.RED))
            return null
        }
        if (!Files.exists(path)) {
            ctx.source.sender.sendMessage(Component.text("Selected tournament does not exist", NamedTextColor.RED))
            return null
        }

        return try {
            TournamentData.load(path).createTournament(path, teamManager).also { Tournament.setTournament(it) }
        } catch (e: IOException) {
            ctx.source.sender.sendMessage(Component.text("Cannot read tournament data: ${e.message}", NamedTextColor.RED))
            null
        }
    }

    private fun tournamentsFolder(): Path {
        return Innotournament.instance.dataFolder.toPath().resolve("tournaments")
    }
}
