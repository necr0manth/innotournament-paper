package dev.necr0manthre.innotournament.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import dev.necr0manthre.innotournament.teams.invites.InviteSystem
import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.TournamentPlayer
import dev.necr0manthre.innotournament.tournament.TournamentTeam
import dev.necr0manthre.innotournament.util.CommandRegisterer
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.minecraft.server.MinecraftServer
import org.bukkit.Bukkit
import org.bukkit.craftbukkit.CraftServer
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
                    val team = checkTeam(ctx) ?: return@executes 1
                    val oldOwner = team.owner

                    if (!team.tryKickPlayerFromTeam(player).isSuccess) return@executes 1
                    ctx.source.sender.sendMessage(
                        Component.text("You left from team ")
                            .append(team.displayName)
                    )

                    if (team.players.isEmpty()) {
                        TournamentTeam.removeTeam(team)
                    } else {
                        team.players.forEach { member ->
                            member.player?.sendMessage(
                                Component.empty()
                                    .append(player.displayName)
                                    .append(Component.text(" left from your team"))
                            )
                        }
                        if (oldOwner != team.owner) {
                            team.owner?.player?.sendMessage(
                                Component.text("You are owner of team ", NamedTextColor.GOLD)
                                    .append(team.displayName)
                            )
                        }
                    }
                    updateSidebars()
                    Command.SINGLE_SUCCESS
                }
        )

        registerer.register(
            Commands.literal("myTeam")
                .executes { ctx ->
                    val player = player(ctx) ?: return@executes 1
                    val team = checkTeam(ctx) ?: return@executes 1
                    ctx.source.sender.sendMessage(myTeamMessage(player, team))
                    Command.SINGLE_SUCCESS
                }
        )

        registerer.register(
            Commands.literal("join")
                .then(
                    Commands.argument<String>("team", StringArgumentType.greedyString())
                        .suggestTeams()
                        .executes { ctx ->
                            val player = player(ctx) ?: return@executes 1
                            val team = TournamentTeam.getTeamById(StringArgumentType.getString(ctx, "team"))
                                ?: run {
                                    ctx.source.sender.sendMessage(Component.text("Team not found", NamedTextColor.RED))
                                    return@executes 1
                                }

                            if (!team.acceptAll && !InviteSystem.isPlayerInvitedToTeam(player, team)) {
                                ctx.source.sender.sendMessage(
                                    Component.text(
                                        "You can't join this team",
                                        NamedTextColor.RED
                                    )
                                )
                                return@executes 1
                            }
                            if (!team.tryAddPlayerToTeam(player).isSuccess) {
                                ctx.source.sender.sendMessage(
                                    Component.text(
                                        "You can't join this team",
                                        NamedTextColor.RED
                                    )
                                )
                                return@executes 1
                            }

                            InviteSystem.rejectInvite(player, team)
                            sendJoinPlayer(player, team)
                            updateSidebars()
                            Command.SINGLE_SUCCESS
                        }
                )
        )

        registerer.register(
            Commands.literal("create")
                .then(
                    Commands.argument("displayName", ArgumentTypes.component())
                        .executes { ctx ->
                            val creator = player(ctx) ?: return@executes 1
                            if (creator.team != null) {
                                ctx.source.sender.sendMessage(
                                    Component.text(
                                        "You already have a team",
                                        NamedTextColor.RED
                                    )
                                )
                                return@executes 1
                            }

                            val displayName = ctx.getArgument("displayName", Component::class.java)
                            val id = idFromDisplayName(displayName)
                            if (TournamentTeam.getTeamById(id) != null) {
                                ctx.source.sender.sendMessage(Component.text("Team already exists", NamedTextColor.RED))
                                return@executes 1
                            }

                            val team = TournamentTeam.createNewTeam(creator, id, displayName)
                            ctx.source.sender.sendMessage(
                                Component.text("Created team ").append(team.displayName)
                            )
                            updateSidebars()
                            Command.SINGLE_SUCCESS
                        }
                )
        )

        registerer.register(
            Commands.literal("list")
                .executes { ctx -> executeListTeams(ctx.source) }
                .then(
                    Commands.argument<String>("team", StringArgumentType.greedyString())
                        .suggestTeams()
                        .executes { ctx ->
                            executeListMembers(
                                ctx.source,
                                StringArgumentType.getString(ctx, "team")
                            )
                        }
                )
        )

        registerer.register(
            Commands.literal("reject")
                .then(
                    Commands.argument("player", ArgumentTypes.player())
                        .executes { ctx ->
                            val team = checkTeam(ctx) ?: return@executes 1
                            if (checkOwner(ctx) == null) return@executes 1
                            val target = ctx.getPlayerArgument("player")

                            if (!InviteSystem.isPlayerInvitedToTeam(target, team)) {
                                ctx.source.sender.sendMessage(
                                    Component.text(
                                        "You can't do it because this player has no join request to your team",
                                        NamedTextColor.RED
                                    )
                                )
                                return@executes 1
                            }

                            InviteSystem.rejectInvite(target, team)
                            ctx.source.sender.sendMessage(
                                Component.text("You rejected join request of ")
                                    .append(target.displayName)
                            )
                            target.player?.sendMessage(
                                Component.text("Your join request to team ")
                                    .append(team.displayName)
                                    .append(Component.text(" was rejected"))
                            )
                            Command.SINGLE_SUCCESS
                        }
                )
        )

        registerer.register(
            Commands.literal("invite")
                .then(
                    Commands.argument("player", ArgumentTypes.player())
                        .executes { ctx ->
                            val inviter = player(ctx) ?: return@executes 1
                            val invited = ctx.getPlayerArgument("player")
                            if (invited === inviter) {
                                ctx.source.sender.sendMessage(
                                    Component.text(
                                        "You cannot invite yourself",
                                        NamedTextColor.RED
                                    )
                                )
                                return@executes 1
                            }

                            val team = team(ctx) ?: TournamentTeam.createNewTeam(inviter)
                            InviteSystem.invitePlayerToTeam(invited, team, 1000)
                            invited.player?.sendMessage(
                                Component.empty()
                                    .append(inviter.displayName)
                                    .append(Component.text(" invites you to team "))
                                    .append(team.displayName)
                                    .append(Component.text(" "))
                                    .append(
                                        Component.text("[Accept]", NamedTextColor.GREEN)
                                            .clickEvent(ClickEvent.runCommand("/teams join ${team.id}"))
                                    )
                            )
                            ctx.source.sender.sendMessage(Component.text("Invite sent"))
                            Command.SINGLE_SUCCESS
                        }
                )
        )

        registerer.register(
            Commands.literal("tryLeave")
                .executes { ctx ->
                    if (checkTeam(ctx) == null) return@executes 1
                    ctx.source.sender.sendMessage(
                        Component.text("Are you sure? ")
                            .append(
                                Component.text("[Leave]", NamedTextColor.RED)
                                    .hoverEvent(HoverEvent.showText(Component.text("Click to leave your team")))
                                    .clickEvent(ClickEvent.runCommand("/teams leave"))
                            )
                    )
                    Command.SINGLE_SUCCESS
                }
        )

        registerer.register(
            Commands.literal("tryKick")
                .then(
                    Commands.argument("player", ArgumentTypes.player())
                        .executes { ctx ->
                            if (checkTeam(ctx) == null) return@executes 1
                            if (checkOwner(ctx) == null) return@executes 1

                            val target = ctx.getPlayerArgument("player")
                            val action = Component.text(" [Kick ", NamedTextColor.RED)
                                .append(target.displayName)
                                .append(Component.text(" ]", NamedTextColor.RED))
                                .hoverEvent(
                                    HoverEvent.showText(
                                        Component.text("Click to kick ")
                                            .append(target.displayName)
                                            .append(Component.text(" from your team"))
                                    )
                                )
                                .clickEvent(ClickEvent.runCommand("/teams kick ${target.name}"))

                            ctx.source.sender.sendMessage(
                                Component.text("Are you sure? ").append(action)
                            )
                            Command.SINGLE_SUCCESS
                        }
                )
        )

        registerer.register(
            Commands.literal("edit")
                .then(
                    Commands.literal("name")
                        .then(
                            Commands.argument("name", ArgumentTypes.component())
                                .executes { ctx ->
                                    val team = checkTeam(ctx) ?: return@executes 1
                                    if (checkOwner(ctx) == null) return@executes 1
                                    val name = ctx.getArgument("name", Component::class.java)

                                    if (!team.changeTeamName(name)) {
                                        ctx.source.sender.sendMessage(
                                            Component.text("Team with this name already exists", NamedTextColor.RED)
                                        )
                                        return@executes 1
                                    }

                                    updateSidebars()
                                    Command.SINGLE_SUCCESS
                                }
                        )
                )
        )

        registerer.register(
            Commands.literal("acceptAll")
                .then(
                    Commands.argument<Boolean>("acceptAll", BoolArgumentType.bool())
                        .executes { ctx ->
                            val team = checkTeam(ctx) ?: return@executes 1
                            if (checkOwner(ctx) == null) return@executes 1

                            team.acceptAll = BoolArgumentType.getBool(ctx, "acceptAll")
                            ctx.source.sender.sendMessage(
                                Component.text("Your team now accepts all: ${team.acceptAll}")
                            )
                            Command.SINGLE_SUCCESS
                        }
                )
        )

        registerer.register(
            Commands.literal("kick")
                .then(
                    Commands.argument("player", ArgumentTypes.player())
                        .executes { ctx ->
                            val team = checkTeam(ctx) ?: return@executes 1
                            if (checkOwner(ctx) == null) return@executes 1

                            val target = ctx.getPlayerArgument("player")
                            val result = team.tryKickPlayerFromTeam(target)
                            if (!result.isSuccess) {
                                ctx.source.sender.sendMessage(
                                    Component.text(
                                        "Unable to kick player",
                                        NamedTextColor.RED
                                    )
                                )
                                return@executes 1
                            }

                            target.player?.sendMessage(Component.text("You were kicked from team"))
                            team.players.forEach { member ->
                                if (member !== target) {
                                    member.player?.sendMessage(
                                        Component.empty()
                                            .append(target.displayName)
                                            .append(Component.text(" was kicked from team"))
                                    )
                                }
                            }
                            updateSidebars()
                            Command.SINGLE_SUCCESS
                        }
                )
        )

        registerer.register(
            Commands.literal("removeAll")
                .requires { source -> source.sender.hasPermission("minecraft.command.team") }
                .executes { ctx ->
                    TournamentTeam.getAllTeams().toList().forEach(TournamentTeam::removeTeam)
                    ctx.source.sender.sendMessage(Component.text("All teams removed"))
                    Command.SINGLE_SUCCESS
                }
        )
    }

    private fun myTeamMessage(player: TournamentPlayer, team: TournamentTeam): Component {
        val isOwner = team.owner == player
        val ans = Component.text()
            .append(Component.text("\n===============\n", NamedTextColor.GRAY))
            .append(Component.text("Your team:\n"))
            .append(
                Component.text("Name: ", NamedTextColor.AQUA)
                    .decorate(TextDecoration.ITALIC)
            )
            .append(team.displayName)

        if (isOwner) {
            ans.append(Component.text(" "))
                .append(
                    Component.text("[Edit]", NamedTextColor.GRAY)
                        .clickEvent(ClickEvent.suggestCommand("/teams edit name ${team.id}"))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to edit name of your team")))
                )
        }

        ans.append(Component.text("\n"))
            .append(Component.text("Members: \n", NamedTextColor.DARK_AQUA).decorate(TextDecoration.BOLD))
            .append(memberLine(player, team, isSelf = true, canKick = false))

        team.players.forEach { member ->
            if (member !== player) {
                ans.append(memberLine(member, team, isSelf = false, canKick = isOwner))
            }
        }

        ans.append(acceptAllLine(team, isOwner))
            .append(Component.text("\n===============", NamedTextColor.GRAY))

        return ans.build()
    }

    private fun memberLine(
        player: TournamentPlayer,
        team: TournamentTeam,
        isSelf: Boolean,
        canKick: Boolean
    ): Component {
        val line = Component.text()
            .append(Component.text(" "))
            .append(player.displayName)

        if (team.owner == player) {
            line.append(
                Component.text(if (isSelf) " (Owner, You) " else " (Owner) ", NamedTextColor.GOLD)
                    .decorate(TextDecoration.ITALIC)
            )
        } else if (isSelf) {
            line.append(
                Component.text(" (You) ", NamedTextColor.GOLD)
                    .decorate(TextDecoration.ITALIC)
            )
        }

        if (isSelf) {
            line.append(
                Component.text("[Leave]", NamedTextColor.RED)
                    .clickEvent(ClickEvent.runCommand("/teams tryLeave"))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to leave your team")))
            )
        } else if (canKick) {
            line.append(
                Component.text(" [Kick]", NamedTextColor.RED)
                    .clickEvent(ClickEvent.runCommand("/teams tryKick ${player.name}"))
                    .hoverEvent(
                        HoverEvent.showText(
                            Component.text("Click to kick ")
                                .append(player.displayName)
                                .append(Component.text(" from your team"))
                        )
                    )
            )
        }

        return line.append(Component.text("\n")).build()
    }

    private fun acceptAllLine(team: TournamentTeam, isOwner: Boolean): Component {
        val line = Component.text()
            .append(Component.text("Accept all: ", NamedTextColor.AQUA))
            .append(
                Component.text(
                    team.acceptAll.toString(),
                    if (team.acceptAll) NamedTextColor.GREEN else NamedTextColor.DARK_GRAY
                )
            )
            .hoverEvent(
                HoverEvent.showText(
                    if (team.acceptAll) {
                        Component.text("Your team accepts all players; invites and requests are not needed")
                    } else {
                        Component.text("Your team accepts players only from invites or requests")
                    }
                )
            )

        if (isOwner) {
            line.append(
                Component.text(" [Change]", NamedTextColor.GRAY)
                    .clickEvent(ClickEvent.runCommand("/teams acceptAll ${!team.acceptAll}"))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to change accept-all mode")))
            )
        }

        return line.build()
    }

    private fun executeListMembers(source: CommandSourceStack, teamId: String): Int {
        val team = TournamentTeam.getTeamById(teamId)
        if (team == null) {
            source.sender.sendMessage(Component.text("Team not found", NamedTextColor.RED))
            return 0
        }

        if (team.players.isEmpty()) {
            source.sender.sendMessage(
                Component.text("Team ")
                    .append(team.displayName)
                    .append(Component.text(" has no members"))
            )
        } else {
            source.sender.sendMessage(
                Component.text("Team ")
                    .append(team.displayName)
                    .append(Component.text(" has ${team.players.size} members: "))
                    .append(formatList(team.players.map { it.displayName }))
            )
        }

        return team.players.size
    }

    private fun executeListTeams(source: CommandSourceStack): Int {
        val teams = TournamentTeam.getAllTeams()
        if (teams.isEmpty()) {
            source.sender.sendMessage(Component.text("There are no teams"))
        } else {
            source.sender.sendMessage(
                Component.text("There are ${teams.size} teams: ")
                    .append(formatList(teams.map { it.displayName }))
            )
        }

        return teams.size
    }

    private fun sendJoinPlayer(player: TournamentPlayer, team: TournamentTeam) {
        player.player?.sendMessage(
            Component.text("You joined team ")
                .append(team.displayName)
        )
        team.players.forEach { member ->
            if (member !== player) {
                member.player?.sendMessage(
                    Component.empty()
                        .append(player.displayName)
                        .append(Component.text(" joined your team"))
                )
            }
        }
    }

    private fun checkTeam(ctx: CommandContext<CommandSourceStack>): TournamentTeam? {
        val team = team(ctx)
        if (team == null) {
            ctx.source.sender.sendMessage(Component.text("You're not on the team.", NamedTextColor.RED))
            return null
        }
        return team
    }

    private fun checkOwner(ctx: CommandContext<CommandSourceStack>): TournamentPlayer? {
        val team = team(ctx)
        val player = player(ctx)
        if (team == null || player == null) return null
        if (team.owner == player) return player

        ctx.source.sender.sendMessage(Component.text("Only team owner can do this", NamedTextColor.RED))
        return null
    }

    private fun player(ctx: CommandContext<CommandSourceStack>): TournamentPlayer? {
        val player = ctx.source.executor as? Player ?: return null
        return TournamentPlayer.get(player)
    }

    private fun team(ctx: CommandContext<CommandSourceStack>): TournamentTeam? {
        return player(ctx)?.team
    }

    private fun updateSidebars() {
        Tournament.get(nmsServer())?.updateSidebars()
    }

    private fun nmsServer(): MinecraftServer {
        return (Bukkit.getServer() as CraftServer).server
    }

    private fun <S, T> RequiredArgumentBuilder<S, T>.suggestTeams(): RequiredArgumentBuilder<S, T> {
        return suggests { _, builder ->
            TournamentTeam.getAllTeams().forEach { builder.suggest(it.id) }
            builder.buildFuture()
        }
    }

    private fun CommandContext<CommandSourceStack>.getPlayerArgument(name: String): TournamentPlayer {
        return TournamentPlayer.get(
            getArgument(name, PlayerSelectorArgumentResolver::class.java)
                .resolve(source)
                .single()
        )
    }

    private fun idFromDisplayName(displayName: Component): String {
        return PlainTextComponentSerializer.plainText().serialize(displayName)
            .ifBlank { "team" }
    }

    private fun formatList(components: List<Component>): Component {
        val builder = Component.text()
        components.forEachIndexed { index, component ->
            if (index > 0) builder.append(Component.text(", "))
            builder.append(component)
        }
        return builder.build()
    }
}
