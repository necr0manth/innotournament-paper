package dev.necr0manthre.innotournament.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.components.TournamentPlayer
import dev.necr0manthre.innotournament.tournament.components.TournamentTeam
import dev.necr0manthre.innotournament.util.CommandRegisterer
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.minecraft.ChatFormatting
import net.minecraft.commands.arguments.TeamArgument
import net.minecraft.server.level.ServerPlayer
import org.bukkit.entity.Player


object TeamsCommand {
    fun register(
        registrar: Commands
    ) {
        val registerer = CommandRegisterer(registrar, "teams", "p")
        registerer.register(
            Commands.literal("leave")
                .executes { ctx ->
                    val player = player(ctx) ?: return@executes 1
                    val team = player.team ?: return@executes 1
                    val oldOwner = team.owner
                    val success: Boolean = team.tryKickPlayerFromTeam(player)
                    if (success) ctx.source.sender.sendMessage(
                        Component.text("You left from team ")
                            .append(team.displayName)
                    )
                    else return@executes 1
                    if (team.players.isEmpty())
                        TournamentTeam.removeTeam(team)
                    else {
                        for (player1 in team.players) {
                            player1.player?.sendMessage(
                                Component.empty()
                                    .append(player.displayName)
                                    .append(Component.text(" left from your team"))
                            )
                        }
                        if (oldOwner != team.owner) {
                            team.owner?.player?.sendMessage(
                                Component.text("You are owner of team ")
                                    .color(NamedTextColor.GOLD)
                                    .append(team.displayName)
                            )
                        }
                    }
                    1
                }
        )
        registerer.register(
            Commands.literal("myTeam")
                .executes { ctx ->
                    if (!TeamsCommand.checkTeam(ctx)) return@executes 1
                    val player: Entity = TeamsCommand.player(ctx).orElseThrow()
                    val team: Entity = TeamsCommand.team(ctx).orElseThrow()

                    val ans: MutableComponent = Component.empty()
                    ans.append(
                        Component.text("\n===============\n")
                            .withStyle(ChatFormatting.GRAY)
                    )
                    val isOwner = TeamOwner.getTeamOwner(team) === player

                    ans.append("Your team:\n")

                    ans.append(
                        Component.text("Name: ").withStyle(ChatFormatting.AQUA)
                            .withStyle(ChatFormatting.ITALIC)
                    )
                    ans.append(TeamManager.getPlayerTeam(team).getDisplayName())
                    if (isOwner) {
                        ans.append(" ")
                        ans.append(
                            Component.text("[Edit]").withStyle(
                                Style.EMPTY
                                    .applyFormat(ChatFormatting.GRAY)
                                    .withClickEvent(
                                        SuggestCommand(
                                            "/teams edit name " + TeamManager.getPlayerTeam(
                                                team
                                            ).getName()
                                        )
                                    )
                                    .withHoverEvent(
                                        HoverEvent.ShowText(
                                            Component.text(
                                                "Click to edit name of your team"
                                            )
                                        )
                                    )
                            )
                        )
                    }
                    ans.append("\n")

                    ans.append(
                        Component.text("Members: \n").withStyle(ChatFormatting.DARK_AQUA)
                            .withStyle(ChatFormatting.BOLD)
                    )
                    ans.append(" ")
                    ans.append(
                        PlayerManager.Companion.getDisplayName(player)
                            .orElse(Component.text("Unknown player"))
                    )
                    ans.append(
                        Component.text(if (isOwner) " (Owner, You) " else " (You) ")
                            .withStyle(ChatFormatting.GOLD).withStyle(ChatFormatting.ITALIC)
                    )
                    ans.append(
                        Component.text("[Leave]").withStyle(ChatFormatting.RED)
                            .withStyle(
                                Style.EMPTY
                                    .withClickEvent(RunCommand("/teams tryLeave"))
                                    .withHoverEvent(
                                        HoverEvent.ShowText(
                                            Component.text(
                                                "Click to leave your team"
                                            )
                                        )
                                    )
                            )
                    )
                    ans.append("\n")

                    for (player1 in TeamManager.getPlayers(team)) {
                        if (player1 === player) continue
                        ans.append(" ")
                        ans.append(
                            PlayerManager.Companion.getDisplayName(player1)
                                .orElse(Component.text("Unknown player"))
                        )
                        if (isOwner) {
                            ans.append(
                                Component.text(" [Kick]").withStyle(ChatFormatting.RED)
                                    .withStyle(
                                        Style.EMPTY
                                            .withClickEvent(
                                                RunCommand(
                                                    "/teams tryKick " + PlayerManager.Companion.getGameProfile(
                                                        player1
                                                    ).map<Any?>(GameProfile::getName).orElseThrow()
                                                )
                                            )
                                            .withHoverEvent(
                                                HoverEvent.ShowText(
                                                    Component.text(
                                                        "Click to kick "
                                                    ).append(
                                                        PlayerManager.Companion.getDisplayName(player1)
                                                            .orElse(Component.text("Unknown player"))
                                                    ).append(" from your team")
                                                )
                                            )
                                    )
                            )
                        }
                        if (player1 === TeamOwner.getTeamOwner(team)) {
                            ans.append(
                                Component.text(" (Owner)").withStyle(ChatFormatting.GOLD)
                                    .withStyle(ChatFormatting.ITALIC)
                            )
                        }
                        ans.append("\n")
                    }
                    if (TeamSettings.Companion.getSettings(team).acceptAll) {
                        ans.append(
                            Component.text("Accept all: ").withStyle(ChatFormatting.AQUA)
                                .append(
                                    Component.text("true").withStyle(ChatFormatting.GREEN)
                                )
                                .withStyle(
                                    Style.EMPTY
                                        .withHoverEvent(
                                            HoverEvent.ShowText(
                                                Component.text(
                                                    "Your team accepts all player, invites and requests and not needed"
                                                )
                                            )
                                        )
                                )
                        )
                        if (isOwner) {
                            ans.append(
                                Component.text(" [Change]")
                                    .withStyle(ChatFormatting.GRAY)
                                    .withStyle(
                                        Style.EMPTY
                                            .withClickEvent(RunCommand("/teams acceptAll false"))
                                            .withHoverEvent(
                                                HoverEvent.ShowText(
                                                    Component.text(
                                                        "Click to make your team not accept all players, instead you will use invites or requests"
                                                    )
                                                )
                                            )
                                    )
                            )
                        }
                    } else {
                        ans.append(
                            Component.text("Accept all: ").withStyle(ChatFormatting.AQUA)
                                .append(
                                    Component.text("false")
                                        .withStyle(ChatFormatting.DARK_GRAY)
                                )
                                .withStyle(
                                    Style.EMPTY
                                        .withHoverEvent(
                                            HoverEvent.ShowText(
                                                Component.text(
                                                    "Your team accepts players only from invites or requests"
                                                )
                                            )
                                        )
                                )
                        )
                        if (isOwner) {
                            ans.append(
                                Component.text(" [Change]")
                                    .withStyle(ChatFormatting.GRAY)
                                    .withStyle(
                                        Style.EMPTY
                                            .withClickEvent(RunCommand("/teams acceptAll true"))
                                            .withHoverEvent(
                                                HoverEvent.ShowText(
                                                    Component.text(
                                                        "Click to make your team accept all players who typed /teams join <Your team name>"
                                                    )
                                                )
                                            )
                                    )
                            )
                        }
                    }

                    ans.append(
                        Component.text("\n===============").withStyle(ChatFormatting.GRAY)
                    )
                    ctx.getSource().sendSystemMessage(ans)
                    1
                }
        )

        registerer.register(
            Commands.literal("join")
                .then(
                    Commands.argument<String>("team", TeamArgument.team())
                        .executes { ctx ->
                            val player = ctx.source.executor as? Player ?: return@executes Command.SINGLE_SUCCESS
                            val team: Entity =
                                TeamsCommand.teamManager(ctx).getEntity(TeamArgument.getTeam(ctx, "team"))
                            if (!TeamSettings.Companion.getSettings(team).acceptAll && !InviteSystem.isPlayerInvitedToTeam(
                                    player,
                                    team
                                ) ||
                                !TeamManager.tryAddPlayerToTeam(team, player).isSuccess()
                            ) {
                                ctx.getSource()
                                    .sendFailure(Component.text("You can't join this team"))
                                return@Command 1
                            }
                            sendJoinPlayer(ctx, player, team)
                            TeamsCommand.updateSidebars(ctx)
                            1
                        }
                )
        )
        registerer.register(
            Commands.literal("create")
                .then(
                    Commands.argument<Component?>(
                        "displayName",
                        ComponentArgument.textComponent(registryAccess)
                    )
                        .executes(Command { ctx: CommandContext<CommandSourceStack?>? ->
                            val scoreboard: ServerScoreboard = ctx.getSource().getServer().scoreboard
                            val displayName: Component =
                                ComponentArgument.getResolvedComponent(ctx, "displayName")
                            val name = displayName.string
                            if (scoreboard.getPlayerTeam(name) != null) throw ADD_DUPLICATE_EXCEPTION.create()
                            val team: PlayerTeam = scoreboard.addPlayerTeam(name)
                            team.setDisplayName(displayName)
                            val teamEntity: Entity = TeamsCommand.teamManager(ctx).getEntity(team)
                            val playerEntity: Entity = TeamsCommand.player(ctx).orElseThrow()
                            TeamManager.forceAddPlayer(teamEntity, playerEntity)
                            TeamOwner.setTeamOwner(teamEntity, playerEntity)
                            ctx.getSource().sendSuccess(java.util.function.Supplier {
                                Component.translatable(
                                    "commands.team.add.success",
                                    displayName
                                )
                            }, true)
                            1
                        })
                )
        )
        registerer.register(
            Commands.literal("list")
                .executes(Command { context: CommandContext<CommandSourceStack?>? ->
                    TeamsCommand.executeListTeams(
                        context.getSource()
                    )
                })
                .then(
                    Commands.argument<String?>("team", TeamArgument.team())
                        .executes(Command { context: CommandContext<CommandSourceStack?>? ->
                            TeamsCommand.executeListMembers(
                                context.getSource(),
                                TeamArgument.getTeam(context, "team")
                            )
                        })
                )
        )
        registerer.register(
            Commands.literal("reject")
                .then(
                    Commands.argument<net.minecraft.commands.arguments.selector.EntitySelector?>(
                        "player",
                        EntityArgument.player()
                    )
                        .executes(Command { ctx: CommandContext<CommandSourceStack?>? ->
                            val team: java.util.Optional<Entity> = TeamsCommand.team(ctx)
                            if (team.isEmpty) {
                                ctx.getSource()
                                    .sendFailure(Component.text("You can't do it because you don't have team"))
                                return@executes 1
                            }
                            val huy: Entity = PlayerManager.Companion.getEntity(EntityArgument.getPlayer(ctx, "player"))
                            if (!InviteSystem.isPlayerInvitedToTeam(huy, team.get())) {
                                ctx.getSource()
                                    .sendFailure(Component.text("You can't do it because this player has no join request to your team"))
                                return@executes 1
                            }
                            InviteSystem.rejectInvite(huy, team.get())
                            ctx.getSource().sendSuccess(java.util.function.Supplier {
                                Component.text("You rejected join request of ")
                                    .append(PlayerManager.Companion.getDisplayName(huy).orElseThrow())
                            }, false)
                            PlayerManager.Companion.getServerPlayer(huy)
                                .ifPresent(java.util.function.Consumer { p: ServerPlayer? ->
                                    p.sendSystemMessage(
                                        Component.text("Your join request to team ")
                                            .append(TeamManager.getPlayerTeam(team.get()).getDisplayName())
                                            .append(" was rejected")
                                    )
                                })
                            1
                        })
                )
        )
        registerer.register(
            Commands.literal("invite")
                .then(
                    Commands.argument<net.minecraft.commands.arguments.selector.EntitySelector?>(
                        "player",
                        EntityArgument.player()
                    )
                        .executes(Command { ctx: CommandContext<CommandSourceStack?>? ->
                            val inviterOpt: java.util.Optional<Entity> = TeamsCommand.player(ctx)
                            if (inviterOpt.isEmpty) return@executes 1
                            val inviter: Entity = inviterOpt.get()
                            val invitedSp: ServerPlayer = EntityArgument.getPlayer(ctx, "player")
                            val invited: Entity = PlayerManager.Companion.getEntity(invitedSp)
                            if (invited === inviter) {
                                ctx.getSource()
                                    .sendFailure(Component.text("You cannot invite yourself"))
                                return@executes 1
                            }
                            val teamOpt: java.util.Optional<Entity> = TeamsCommand.team(ctx)
                            val server: MinecraftServer = TeamsCommand.server(ctx)
                            val teamEntity: Entity = teamOpt.orElseGet(java.util.function.Supplier {
                                val scoreboard: ServerScoreboard = server.getScoreboard()
                                val base: String = PlayerManager.Companion.getName(inviter).orElse("team")
                                var name = base
                                var i = 1
                                while (scoreboard.getPlayerTeam(name) != null) name = base + "_" + (i++)
                                val pTeam: PlayerTeam = scoreboard.addPlayerTeam(name)
                                val entity: Entity = TeamsCommand.teamManager(ctx).getEntity(pTeam)
                                TeamOwner.setTeamOwner(entity, inviter)
                                TeamManager.forceAddPlayer(entity, inviter)
                                entity
                            })
                            InviteSystem.invitePlayerToTeam(invited, teamEntity, 1000)
                            PlayerManager.Companion.getServerPlayer(invited)
                                .ifPresent(java.util.function.Consumer { p: ServerPlayer? ->
                                    p.sendSystemMessage(
                                        Component.empty()
                                            .append(PlayerManager.Companion.getDisplayName(inviter).orElseThrow())
                                            .append(Component.text(" invites you to team "))
                                            .append(TeamManager.getPlayerTeam(teamEntity).getDisplayName())
                                            .append(" ")
                                            .append(
                                                Component.text("[Accept]").withStyle(
                                                    Style.EMPTY.withColor(0x00FF00)
                                                        .withClickEvent(
                                                            RunCommand(
                                                                "/teams join " + TeamManager.getPlayerTeam(
                                                                    teamEntity
                                                                ).getName()
                                                            )
                                                        )
                                                )
                                            )
                                    )
                                })
                            ctx.getSource().sendSuccess(java.util.function.Supplier {
                                Component.text("Invite sent")
                            }, false)
                            1
                        })
                )
        )

        registerer.register(
            Commands.literal("tryLeave")
                .executes(Command { ctx: CommandContext<CommandSourceStack?>? ->
                    if (!TeamsCommand.checkTeam(ctx)) return@executes 1
                    ctx.getSource().sendSuccess(java.util.function.Supplier {
                        Component.text("Are you sure? ")
                            .append(
                                Component.text("[Leave]").withStyle(ChatFormatting.RED)
                                    .withStyle(
                                        Style.EMPTY
                                            .withHoverEvent(
                                                HoverEvent.ShowText(
                                                    Component.text(
                                                        "Click to leave your team"
                                                    )
                                                )
                                            )
                                            .withClickEvent(RunCommand("/teams leave"))
                                    )
                            )
                    }, false)
                    1
                })
        )

        registerer.register(
            Commands.literal("tryKick")
                .then(
                    Commands.argument<net.minecraft.commands.arguments.selector.EntitySelector?>(
                        "player",
                        EntityArgument.player()
                    )
                        .executes(Command { ctx: CommandContext<CommandSourceStack?>? ->
                            if (!TeamsCommand.checkTeam(ctx)) return@executes 1
                            if (!TeamsCommand.checkOwner(ctx)) return@executes 1
                            val target: Entity =
                                PlayerManager.Companion.getEntity(EntityArgument.getPlayer(ctx, "player"))
                            val nameComp: Component =
                                PlayerManager.Companion.getDisplayName(target)
                                    .orElse(Component.text("Unknown"))
                            val hover = HoverEvent.ShowText(
                                Component.text("Click to kick ").append(nameComp.copy())
                                    .append(" from your team")
                            )
                            val click: RunCommand =
                                RunCommand("/teams kick " + PlayerManager.Companion.getName(target).orElse(""))
                            val action: MutableComponent =
                                Component.text(" [Kick ").append(nameComp).append(" ]")
                                    .withStyle(ChatFormatting.RED)
                                    .withStyle(UnaryOperator { style: Style? ->
                                        style!!.withHoverEvent(
                                            hover
                                        ).withClickEvent(click)
                                    })
                            ctx.getSource().sendSuccess(java.util.function.Supplier {
                                Component.text("Are you sure? ").append(action)
                            }, false)
                            1
                        })
                )
        )

        registerer.register(
            Commands.literal("edit")
                .then(
                    Commands.literal("name")
                        .then(
                            Commands.argument<Component?>(
                                "name",
                                ComponentArgument.textComponent(registryAccess)
                            )
                                .executes(Command { ctx: CommandContext<CommandSourceStack?>? ->
                                    if (!TeamsCommand.checkTeam(ctx)) return@executes 1
                                    if (!TeamsCommand.checkOwner(ctx)) return@executes 1
                                    if (!TournamentUtils.changeTeamName(
                                            TeamsCommand.team(ctx).orElseThrow(),
                                            ComponentArgument.getResolvedComponent(ctx, "name")
                                        )
                                    ) {
                                        ctx.getSource()
                                            .sendFailure(Component.text("Team with this name already exists"))
                                    }
                                    TeamsCommand.updateSidebars(ctx)
                                    1
                                })
                        )
                )
        )

        registerer.register(
            Commands.literal("acceptAll")
                .then(
                    Commands.argument<Boolean?>("acceptAll", BoolArgumentType.bool())
                        .executes(Command { ctx: CommandContext<CommandSourceStack?>? ->
                            if (!TeamsCommand.checkTeam(ctx)) return@executes 1
                            if (!TeamsCommand.checkOwner(ctx)) return@executes 1
                            val t: Entity = TeamsCommand.team(ctx).orElseThrow()
                            TeamSettings.Companion.getSettings(t).acceptAll = BoolArgumentType.getBool(ctx, "acceptAll")
                            ctx.getSource().sendSuccess(java.util.function.Supplier {
                                Component.text(
                                    "Your team now accepts all: " + TeamSettings.Companion.getSettings(
                                        t
                                    ).acceptAll
                                )
                            }, false)
                            1
                        })
                )
        )

        registerer.register(
            Commands.literal("kick")
                .then(
                    Commands.argument<net.minecraft.commands.arguments.selector.EntitySelector?>(
                        "player",
                        EntityArgument.player()
                    )
                        .executes(Command { ctx: CommandContext<CommandSourceStack?>? ->
                            if (!TeamsCommand.checkTeam(ctx)) return@executes 1
                            if (!TeamsCommand.checkOwner(ctx)) return@executes 1
                            val t: Entity = TeamsCommand.team(ctx).orElseThrow()
                            val target: Entity =
                                PlayerManager.Companion.getEntity(EntityArgument.getPlayer(ctx, "player"))
                            val result: PlayerKickResult = TeamManager.tryKickPlayerFromTeam(t, target)
                            if (!result.isSuccess()) {
                                ctx.getSource()
                                    .sendFailure(Component.text("Unable to kick player"))
                                return@executes 1
                            }
                            PlayerManager.Companion.getServerPlayer(target)
                                .ifPresent(java.util.function.Consumer { p: ServerPlayer? ->
                                    p.sendSystemMessage(
                                        Component.text("You were kicked from team")
                                    )
                                })
                            TeamManager.getPlayers(t).forEach(java.util.function.Consumer { member: Entity ->
                                if (member !== target) {
                                    PlayerManager.Companion.getServerPlayer(member)
                                        .ifPresent(java.util.function.Consumer { p: ServerPlayer? ->
                                            p.sendSystemMessage(
                                                Component.empty()
                                                    .append(
                                                        PlayerManager.Companion.getDisplayName(target)
                                                            .orElse(Component.text("Unknown"))
                                                    )
                                                    .append(" was kicked from team")
                                            )
                                        })
                                }
                            })
                            TeamsCommand.updateSidebars(ctx)
                            1
                        })
                )
        )
        registerer.register(
            Commands.literal("removeAll")
                .requires(java.util.function.Predicate { commandSourceStack: CommandSourceStack? ->
                    commandSourceStack!!.hasPermission(
                        4
                    )
                })
                .executes(Command { ctx: CommandContext<CommandSourceStack?>? ->
                    val server: MinecraftServer = ctx.getSource().getServer()
                    val scoreboard: ServerScoreboard = server.getScoreboard()
                    for (team in scoreboard.getPlayerTeams()) {
                        scoreboard.removePlayerTeam(team)
                    }
                    ctx.getSource().sendSuccess(
                        java.util.function.Supplier { Component.text("All teams removed") },
                        true
                    )
                    Command.SINGLE_SUCCESS
                })
        )
    }

    private fun executeListMembers(source: CommandSourceStack, team: PlayerTeam): Int {
        val collection: MutableCollection<String?> = team.getPlayers()
        if (collection.isEmpty()) {
            source.sendSuccess(java.util.function.Supplier {
                Component.translatable(
                    "commands.team.list.members.empty",
                    team.getDisplayName()
                )
            }, false)
        } else {
            source.sendSuccess(
                java.util.function.Supplier {
                    Component.translatable(
                        "commands.team.list.members.success",
                        team.getDisplayName(),
                        collection.size,
                        ComponentUtils.formatList(collection)
                    )
                }, false
            )
        }

        return collection.size
    }

    private fun executeListTeams(source: CommandSourceStack): Int {
        val collection =
            source.server.scoreboard.playerTeams
        if (collection.isEmpty()) {
            source.sendSuccess(
                { Component.translatable("commands.team.list.teams.empty") },
                false
            )
        } else {
            source.sendSuccess({
                Component.translatable(
                    "commands.team.list.teams.success",
                    collection.size,
                    ComponentUtils.formatList<PlayerTeam?>(
                        collection,
                        java.util.function.Function { obj: PlayerTeam? -> obj.getDisplayName() })
                )
            }, false)
        }

        return collection.size
    }

    private fun sendJoinPlayer(
        ctx: CommandContext<CommandSourceStack>,
        player: TournamentPlayer,
        team: TournamentTeam
    ) {
        player.player?.sendMessage(
            Component.text("You joined team ")
                .append(team.team.displayName())
        )
        for (player1 in team.players) {
            if (player1 === player) continue
            player1.player?.sendMessage(
                Component.empty()
                    .append(player.displayName)
                    .append(Component.text(" joined your team"))
            )
        }
    }

    private fun checkTeam(ctx: CommandContext<CommandSourceStack>): Boolean {
        if (team(ctx) == null) {
            ctx.source.sender.sendMessage(Component.text("You're not on the team."))
            return false
        }
        return true
    }

    private fun checkOwner(ctx: CommandContext<CommandSourceStack>): Boolean {
        val team = team(ctx)
        val player = player(ctx)
        if (team == null || player == null) return false
        return team.owner == player
    }

    private fun player(ctx: CommandContext<CommandSourceStack>): TournamentPlayer? {
        val player = ctx.source.executor as? Player ?: return null
        return TournamentPlayer.getTournamentData(player)
    }

    private fun team(ctx: CommandContext<CommandSourceStack>): TournamentTeam? {
        val player = ctx.source.executor as? Player ?: return null
        return TournamentPlayer.getTournamentData(player).team
    }

    private fun updateSidebars(ctx: CommandContext<CommandSourceStack>) {
        val tournament = Tournament.get(ctx.source.server)
        tournament?.updateSidebars()
    }
}
