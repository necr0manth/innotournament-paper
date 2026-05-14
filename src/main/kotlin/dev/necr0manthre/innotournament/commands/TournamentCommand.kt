package dev.necr0manthre.innotournament.commands

import dev.architectury.platform.Platform

object TournamentCommand {
    private var selectedTournamentPath: java.nio.file.Path? = null

    fun register(
        dispatcher: com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack?>,
        registryAccess: CommandBuildContext?,
        environment: CommandSelection?
    ) {
        dispatcher.register(
            net.minecraft.commands.Commands.literal("tournament").then(
                net.minecraft.commands.Commands.literal("help")
                    .executes(com.mojang.brigadier.Command { ctx: CommandContext<net.minecraft.commands.CommandSourceStack?>? ->
                        ctx.getSource().sendSuccess(java.util.function.Supplier {
                            net.minecraft.network.chat.Component.literal("All info about tournament is here ").append(
                                net.minecraft.network.chat.Component.literal("https://telegra.ph/Majnkraft-turnir-09-21")
                                    .withStyle(UnaryOperator { style: net.minecraft.network.chat.Style? ->
                                        style!!.withClickEvent(OpenUrl(java.net.URI.create("https://telegra.ph/Majnkraft-turnir-09-21")))
                                    })
                            )
                        }, false)
                        1
                    })
            )
        )
        dispatcher.register(
            net.minecraft.commands.Commands.literal("tournament")
                .then(
                    net.minecraft.commands.Commands.literal("select")
                        .requires(java.util.function.Predicate { commandSourceStack: net.minecraft.commands.CommandSourceStack? ->
                            commandSourceStack!!.hasPermission(
                                4
                            )
                        })
                        .then(
                            net.minecraft.commands.Commands.argument<kotlin.String?>(
                                "filePath",
                                StringArgumentType.string()
                            )
                                .suggests(SuggestionProvider { context: CommandContext<net.minecraft.commands.CommandSourceStack?>?, builder: SuggestionsBuilder? ->
                                    val path: Unit /* TODO: class org.jetbrains.kotlin.nj2k.types.JKJavaNullPrimitiveType */? =
                                        Platform.getConfigFolder().resolve("inno/tournaments")
                                    if (!java.nio.file.Files.exists(path)) {
                                        try {
                                            java.nio.file.Files.createDirectory(path)
                                        } catch (ignored: IOException) {
                                        }
                                    }
                                    try {
                                        java.nio.file.Files.list(path).use { list ->
                                            list.forEach { p: java.nio.file.Path? ->
                                                if (p.toString().endsWith(".json")) builder.suggest(
                                                    p!!.getFileName().toString()
                                                )
                                            }
                                        }
                                    } catch (ignored: IOException) {
                                    }
                                    builder.buildFuture()
                                })
                                .executes(com.mojang.brigadier.Command { ctx: CommandContext<net.minecraft.commands.CommandSourceStack?>? ->
                                    val filePath: kotlin.String? = StringArgumentType.getString(ctx, "filePath")
                                    TournamentCommand.selectedTournamentPath =
                                        Platform.getConfigFolder().resolve("inno/tournaments").resolve(filePath!!)
                                    1
                                })
                        )
                )
                .then(
                    net.minecraft.commands.Commands.literal("prepare")
                        .requires(java.util.function.Predicate { commandSourceStack: net.minecraft.commands.CommandSourceStack? ->
                            commandSourceStack!!.hasPermission(
                                4
                            )
                        })
                        .executes(com.mojang.brigadier.Command { ctx: CommandContext<net.minecraft.commands.CommandSourceStack?>? ->
                            if (Tournament.Companion.get(
                                    ctx.getSource().getServer()
                                ) == null
                            ) TournamentCommand.useSelectedData(
                                ctx,
                                java.util.function.Consumer { selectedData: TournamentData? -> })
                            if (Tournament.Companion.get(ctx.getSource().getServer()) != null) try {
                                Tournament.Companion.get(ctx.getSource().getServer())
                                    .prepare(ctx.getSource().getServer())
                                ctx.getSource().sendSuccess(java.util.function.Supplier {
                                    net.minecraft.network.chat.Component.literal("Tournament prepared")
                                }, true)
                            } catch (e: java.lang.Exception) {
                                ctx.getSource()
                                    .sendFailure(net.minecraft.network.chat.Component.literal("Something went wrong while preparing tournament: " + e.message))
                                ctx.getSource()
                                    .sendSystemMessage(net.minecraft.network.chat.Component.literal("Reseting tournament..."))
                                try {
                                    Tournament.Companion.setTournament(ctx.getSource().getServer(), null)
                                    TournamentCommand.useSelectedData(
                                        ctx,
                                        java.util.function.Consumer { selectedData: TournamentData? -> })
                                    Tournament.Companion.get(ctx.getSource().getServer())
                                        .prepare(ctx.getSource().getServer())
                                    ctx.getSource().sendSuccess(java.util.function.Supplier {
                                        net.minecraft.network.chat.Component.literal("Tournament was prepared!!!")
                                    }, true)
                                } catch (e2: java.lang.Exception) {
                                    ctx.getSource()
                                        .sendFailure(net.minecraft.network.chat.Component.literal("Something went wrong, yes, again: " + e2.message))
                                }
                            }
                            1
                        }
                        ))
                .then(
                    net.minecraft.commands.Commands.literal("start")
                        .requires(java.util.function.Predicate { commandSourceStack: net.minecraft.commands.CommandSourceStack? ->
                            commandSourceStack!!.hasPermission(
                                4
                            )
                        })
                        .executes(com.mojang.brigadier.Command { ctx: CommandContext<net.minecraft.commands.CommandSourceStack?>? ->
                            if (TournamentCommand.selectedTournamentPath == null) {
                                ctx.getSource()
                                    .sendFailure(net.minecraft.network.chat.Component.literal("No tournament selected"))
                                return@executes 1
                            } else if (Tournament.Companion.get(ctx.getSource().getServer()) == null) {
                                TournamentCommand.useSelectedData(
                                    ctx,
                                    java.util.function.Consumer { selectedData: TournamentData? -> })
                                if (Tournament.Companion.get(
                                        ctx.getSource().getServer()
                                    ) != null
                                ) Tournament.Companion.get(ctx.getSource().getServer())
                                    .prepare(ctx.getSource().getServer())
                            }
                            try {
                                Tournament.Companion.get(ctx.getSource().getServer()).start()
                                ctx.getSource().sendSuccess(java.util.function.Supplier {
                                    net.minecraft.network.chat.Component.literal("Tournament started")
                                }, true)
                            } catch (e: java.lang.Exception) {
                                ctx.getSource()
                                    .sendFailure(net.minecraft.network.chat.Component.literal("Something went wrong while starting tournament: " + e.message))
                                ctx.getSource()
                                    .sendSystemMessage(net.minecraft.network.chat.Component.literal("Reseting tournament..."))
                                try {
                                    Tournament.Companion.setTournament(ctx.getSource().getServer(), null)
                                    TournamentCommand.useSelectedData(
                                        ctx,
                                        java.util.function.Consumer { selectedData: TournamentData? -> })
                                    Tournament.Companion.get(ctx.getSource().getServer())
                                        .prepare(ctx.getSource().getServer())
                                    Tournament.Companion.get(ctx.getSource().getServer()).start()
                                    ctx.getSource().sendSuccess(java.util.function.Supplier {
                                        net.minecraft.network.chat.Component.literal("Tournament started!!!")
                                    }, true)
                                } catch (e2: java.lang.Exception) {
                                    ctx.getSource()
                                        .sendFailure(net.minecraft.network.chat.Component.literal("Something went wrong, yes, again: " + e2.message))
                                }
                            }
                            1
                        })
                )
                .then(
                    net.minecraft.commands.Commands.literal("stop")
                        .requires(java.util.function.Predicate { commandSourceStack: net.minecraft.commands.CommandSourceStack? ->
                            commandSourceStack!!.hasPermission(
                                4
                            )
                        })
                        .executes(com.mojang.brigadier.Command { ctx: CommandContext<net.minecraft.commands.CommandSourceStack?>? ->
                            Tournament.Companion.setTournament(ctx.getSource().getServer(), null)
                            1
                        })
                )
                .then(
                    net.minecraft.commands.Commands.literal("set")
                        .requires(java.util.function.Predicate { commandSourceStack: net.minecraft.commands.CommandSourceStack? ->
                            commandSourceStack!!.hasPermission(
                                4
                            )
                        })
                        .then(
                            net.minecraft.commands.Commands.literal("tournamentPreSpawn")
                                .then(
                                    net.minecraft.commands.Commands.argument<net.minecraft.commands.arguments.coordinates.Coordinates?>(
                                        "pos",
                                        BlockPosArgument.blockPos()
                                    )
                                        .executes(com.mojang.brigadier.Command { ctx: CommandContext<net.minecraft.commands.CommandSourceStack?>? ->
                                            TournamentCommand.useSelectedData(
                                                ctx,
                                                java.util.function.Consumer { selectedData: TournamentData? ->
                                                    selectedData.tournamentPreSpawn =
                                                        BlockPosArgument.getBlockPos(ctx, "pos")
                                                })
                                        }
                                        )))
                        .then(
                            net.minecraft.commands.Commands.literal("tournamentPreSpawnDimension")
                                .then(
                                    net.minecraft.commands.Commands.argument<net.minecraft.resources.Identifier?>(
                                        "dimension",
                                        DimensionArgument.dimension()
                                    )
                                        .executes(com.mojang.brigadier.Command { ctx: CommandContext<net.minecraft.commands.CommandSourceStack?>? ->
                                            TournamentCommand.useSelectedData(
                                                ctx,
                                                java.util.function.Consumer { selectedData: TournamentData? ->
                                                    try {
                                                        selectedData.tournamentPreSpawnDimension =
                                                            DimensionArgument.getDimension(ctx, "dimension").dimension()
                                                    } catch (e: com.mojang.brigadier.exceptions.CommandSyntaxException) {
                                                        throw java.lang.RuntimeException(e)
                                                    }
                                                })
                                        })
                                )
                        )
                        .then(
                            net.minecraft.commands.Commands.literal("tournamentSpawn")
                                .then(
                                    net.minecraft.commands.Commands.argument<net.minecraft.commands.arguments.coordinates.Coordinates?>(
                                        "pos",
                                        BlockPosArgument.blockPos()
                                    )
                                        .executes(com.mojang.brigadier.Command { ctx: CommandContext<net.minecraft.commands.CommandSourceStack?>? ->
                                            TournamentCommand.useSelectedData(
                                                ctx,
                                                java.util.function.Consumer { selectedData: TournamentData? ->
                                                    selectedData.tournamentSpawn =
                                                        BlockPosArgument.getBlockPos(ctx, "pos")
                                                })
                                        }
                                        )))
                        .then(
                            net.minecraft.commands.Commands.literal("tournamentSpawnDimension")
                                .then(
                                    net.minecraft.commands.Commands.argument<net.minecraft.resources.Identifier?>(
                                        "dimension",
                                        DimensionArgument.dimension()
                                    )
                                        .executes(com.mojang.brigadier.Command { ctx: CommandContext<net.minecraft.commands.CommandSourceStack?>? ->
                                            TournamentCommand.useSelectedData(
                                                ctx,
                                                java.util.function.Consumer { selectedData: TournamentData? ->
                                                    try {
                                                        selectedData.tournamentSpawnDimension =
                                                            DimensionArgument.getDimension(ctx, "dimension").dimension()
                                                    } catch (e: com.mojang.brigadier.exceptions.CommandSyntaxException) {
                                                        throw java.lang.RuntimeException(e)
                                                    }
                                                })
                                        })
                                )
                        )
                        .then(
                            net.minecraft.commands.Commands.literal("startBoxStructureResourceLocation")
                                .then(
                                    net.minecraft.commands.Commands.argument<T?>(
                                        "template",
                                        ResourceLocationArgument.id()
                                    )
                                        .suggests(SuggestionProvider { commandContext: CommandContext<net.minecraft.commands.CommandSourceStack?>?, suggestionsBuilder: SuggestionsBuilder? ->
                                            val structureTemplateManager: StructureTemplateManager =
                                                (commandContext.getSource()).getLevel().getStructureManager()
                                            SharedSuggestionProvider.suggestResource(
                                                structureTemplateManager.listTemplates(),
                                                suggestionsBuilder
                                            )
                                        })
                                        .executes(com.mojang.brigadier.Command { ctx: CommandContext<net.minecraft.commands.CommandSourceStack?>? ->
                                            TournamentCommand.useSelectedData(
                                                ctx,
                                                java.util.function.Consumer { selectedData: TournamentData? ->
                                                    selectedData.startBoxStructureResourceLocation =
                                                        ResourceLocationArgument.getId(ctx, "template")
                                                })
                                        })
                                )
                        )
                        .then(
                            net.minecraft.commands.Commands.literal("startBoxPos")
                                .then(
                                    net.minecraft.commands.Commands.argument<net.minecraft.commands.arguments.coordinates.Coordinates?>(
                                        "pos",
                                        BlockPosArgument.blockPos()
                                    )
                                        .executes(com.mojang.brigadier.Command { ctx: CommandContext<net.minecraft.commands.CommandSourceStack?>? ->
                                            TournamentCommand.useSelectedData(
                                                ctx,
                                                java.util.function.Consumer { selectedData: TournamentData? ->
                                                    selectedData.startBoxPos = BlockPosArgument.getBlockPos(ctx, "pos")
                                                })
                                        }
                                        ))))
        )
    }

    private fun useSelectedData(
        ctx: CommandContext<net.minecraft.commands.CommandSourceStack?>,
        action: java.util.function.Consumer<TournamentData?>
    ): kotlin.Int {
        if (TournamentCommand.selectedTournamentPath == null) {
            ctx.getSource().sendFailure(net.minecraft.network.chat.Component.literal("No tournament selected"))
            return 1
        }
        if (!java.nio.file.Files.exists(TournamentCommand.selectedTournamentPath)) {
            ctx.getSource()
                .sendFailure(net.minecraft.network.chat.Component.literal("Selected tournament does not exist"))
            return 1
        }
        val selectedData: TournamentData
        try {
            val dataResult: DataResult<com.mojang.datafixers.util.Pair<TournamentData, com.google.gson.JsonElement?>?> =
                TournamentData.Companion.CODEC.decode<com.google.gson.JsonElement?>(
                    JsonOps.INSTANCE,
                    com.google.gson.JsonParser.parseReader(java.nio.file.Files.newBufferedReader(TournamentCommand.selectedTournamentPath))
                )
            selectedData = dataResult.getPartialOrThrow().getFirst()
            if (dataResult.error().isPresent()) {
                ctx.getSource().sendFailure(
                    net.minecraft.network.chat.Component.literal(
                        "Something got wrong while reading tournament data: " + dataResult.error().get().message()
                    )
                )
                ctx.getSource()
                    .sendSystemMessage(net.minecraft.network.chat.Component.literal("... but anyway data was loaded"))
            }
        } catch (e: IOException) {
            ctx.getSource()
                .sendFailure(net.minecraft.network.chat.Component.literal("Cannot read tournament data: " + e.message))
            return 1
        }
        action.accept(selectedData)
        val json: com.google.gson.JsonElement? = TournamentData.Companion.CODEC.encode<com.google.gson.JsonElement?>(
            selectedData,
            JsonOps.INSTANCE,
            com.google.gson.JsonObject()
        ).getOrThrow()
        try {
            java.nio.file.Files.writeString(
                TournamentCommand.selectedTournamentPath,
                com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json)
            )
            if (Tournament.Companion.get(
                    ctx.getSource().getServer()
                ) == null || Tournament.Companion.get(ctx.getSource().getServer()).getPhase() === 0
            ) Tournament.Companion.setTournament(
                ctx.getSource().getServer(),
                selectedData.createTournament(TournamentCommand.selectedTournamentPath)
            )
        } catch (e: IOException) {
            ctx.getSource()
                .sendFailure(net.minecraft.network.chat.Component.literal("Cannot write tournament data: " + e.message))
        }
        return 1
    }
}
