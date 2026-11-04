package dev.necr0manthre.innotournament.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.teams.PluginTeam
import dev.necr0manthre.innotournament.teams.PluginTeam.TeamManager
import dev.necr0manthre.innotournament.teams.TeamCreateResult
import dev.necr0manthre.innotournament.teams.invites.InviteSystem
import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.util.CommandRegisterer
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player

class TeamsCommand(private val teamManager: TeamManager) {
    fun register(
        registrar: Commands
    ) {
        val registerer = CommandRegisterer(registrar, "teams", "p")

        registerer.add(
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
                    updateSidebars()
                    Command.SINGLE_SUCCESS
                }
        )

        registerer.add(
            Commands.literal("myTeam")
                .executes { ctx ->
                    val player = player(ctx) ?: return@executes 1
                    val team = checkTeam(ctx) ?: return@executes 1
                    ctx.source.sender.sendMessage(myTeamMessage(player, team))
                    Command.SINGLE_SUCCESS
                }
        )

        registerer.add(
            Commands.literal("join")
                .then(
                    Commands.argument<String>("team", StringArgumentType.greedyString())
                        .suggestTeams()
                        .executes { ctx ->
                            val player = player(ctx) ?: return@executes 1
                            val team = teamManager.getTeamById(StringArgumentType.getString(ctx, "team"))
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

        registerer.add(
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
                            if (teamManager.getTeamById(id) != null) {
                                ctx.source.sender.sendMessage(Component.text("Team already exists", NamedTextColor.RED))
                                return@executes 1
                            }

                            val result = teamManager.createNewTeam(creator, id, displayName)
                            val team = (result as? TeamCreateResult.Created)?.team
                            if (team == null) {
                                ctx.source.sender.sendMessage(
                                    Component.text("Unable to create team: $result", NamedTextColor.RED)
                                )
                                return@executes 1
                            }
                            ctx.source.sender.sendMessage(
                                Component.text("Created team ").append(team.displayName)
                            )
                            updateSidebars()
                            Command.SINGLE_SUCCESS
                        }
                )
        )

        registerer.add(
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

        registerer.add(
            Commands.literal("reject")
                .then(
                    Commands.argument<String>("player", StringArgumentType.word())
                        .suggestPlayers()
                        .executes { ctx ->
                            val team = checkTeam(ctx) ?: return@executes 1
                            if (checkOwner(ctx) == null) return@executes 1
                            val target = getTargetPlayer(ctx) ?: return@executes 1

                            if (!InviteSystem.isPlayerInvitedToTeam(target, team)) {
                                ctx.source.sender.sendMessage(
                                    Component.text(
                                        "This player has no active invitation to your team",
                                        NamedTextColor.RED
                                    )
                                )
                                return@executes 1
                            }

                            InviteSystem.rejectInvite(target, team)
                            ctx.source.sender.sendMessage(
                                Component.text("You revoked the invitation for ")
                                    .append(target.displayName)
                            )
                            target.player?.sendMessage(
                                Component.text("Your invitation to team ")
                                    .append(team.displayName)
                                    .append(Component.text(" was revoked"))
                            )
                            Command.SINGLE_SUCCESS
                        }
                )
        )

        registerer.add(
            Commands.literal("invite")
                .then(
                    Commands.argument<String>("player", StringArgumentType.word())
                        .suggestPlayers()
                        .executes { ctx ->
                            val inviter = player(ctx) ?: return@executes 1
                            val invited = getTargetPlayer(ctx) ?: return@executes 1
                            if (invited === inviter) {
                                ctx.source.sender.sendMessage(
                                    Component.text(
                                        "You cannot invite yourself",
                                        NamedTextColor.RED
                                    )
                                )
                                return@executes 1
                            }

                            val existingTeam = inviter.team
                            val team = if (existingTeam != null) {
                                existingTeam
                            } else {
                                when (val created = teamManager.createNewTeam(inviter)) {
                                    is TeamCreateResult.Created -> created.team
                                    else -> {
                                        ctx.source.sender.sendMessage(
                                            Component.text("Unable to create team: $created", NamedTextColor.RED)
                                        )
                                        return@executes 1
                                    }
                                }
                            }
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

        registerer.add(
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

        registerer.add(
            Commands.literal("tryKick")
                .then(
                    Commands.argument<String>("player", StringArgumentType.word())
                        .suggestPlayers()
                        .executes { ctx ->
                            if (checkTeam(ctx) == null) return@executes 1
                            if (checkOwner(ctx) == null) return@executes 1

                            val target = getTargetPlayer(ctx) ?: return@executes 1
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

        registerer.add(
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

        registerer.add(
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

        registerer.add(
            Commands.literal("kick")
                .then(
                    Commands.argument<String>("player", StringArgumentType.word())
                        .suggestPlayers()
                        .executes { ctx ->
                            val team = checkTeam(ctx) ?: return@executes 1
                            if (checkOwner(ctx) == null) return@executes 1

                            val target = getTargetPlayer(ctx) ?: return@executes 1
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

        registerer.add(
            Commands.literal("owner")
                .then(
                    Commands.argument<String>("player", StringArgumentType.word())
                        .suggestPlayers()
                        .executes { ctx ->
                            val team = checkTeam(ctx) ?: return@executes 0
                            val owner = checkOwner(ctx) ?: return@executes 0
                            val target = getTargetPlayer(ctx) ?: return@executes 0

                            if (target.team !== team) {
                                ctx.source.sender.sendMessage(
                                    Component.text("That player is not on your team", NamedTextColor.RED)
                                )
                                return@executes 0
                            }
                            val alreadyOwner = target === owner
                            if (!team.transferOwnership(target)) {
                                ctx.source.sender.sendMessage(
                                    Component.text("Ownership transfer was cancelled", NamedTextColor.RED)
                                )
                                return@executes 0
                            }
                            if (alreadyOwner) {
                                ctx.source.sender.sendMessage(Component.text("That player is already the team owner"))
                                return@executes Command.SINGLE_SUCCESS
                            }

                            ctx.source.sender.sendMessage(
                                Component.text("You transferred ownership of team ")
                                    .append(team.displayName)
                                    .append(Component.text(" to "))
                                    .append(target.displayName)
                            )
                            target.player?.sendMessage(
                                Component.text("You are now owner of team ").append(team.displayName)
                            )
                            updateSidebars()
                            Command.SINGLE_SUCCESS
                        }
                )
        )

        registerer.add(
            Commands.literal("remove")
                .executes { ctx ->
                    val player = player(ctx) ?: return@executes 0
                    val team = player.team
                    if (team == null) {
                        ctx.source.sender.sendMessage(
                            Component.text("You're not on the team.", NamedTextColor.RED)
                        )
                        return@executes 0
                    }
                    if (team.owner !== player) {
                        ctx.source.sender.sendMessage(
                            Component.text("Only team owner can do this", NamedTextColor.RED)
                        )
                        return@executes 0
                    }

                    if (!teamManager.removeTeamFromCommand(team)) {
                        ctx.source.sender.sendMessage(
                            Component.text("Unable to remove team", NamedTextColor.RED)
                        )
                        return@executes 0
                    }

                    ctx.source.sender.sendMessage(
                        Component.text("Removed team ").append(team.displayName)
                    )
                    updateSidebars()
                    Command.SINGLE_SUCCESS
                }
                .then(
                    Commands.argument<String>("team", StringArgumentType.greedyString())
                        .requires { source -> source.sender.hasPermission("innotournament.admin") }
                        .suggestTeams()
                        .executes { ctx ->
                            val teamId = StringArgumentType.getString(ctx, "team")
                            val team = teamManager.getTeamById(teamId)
                            if (team == null) {
                                ctx.source.sender.sendMessage(Component.text("Team not found", NamedTextColor.RED))
                                return@executes 0
                            }

                            if (!teamManager.removeTeamFromCommand(team)) {
                                ctx.source.sender.sendMessage(
                                    Component.text("Unable to remove team", NamedTextColor.RED)
                                )
                                return@executes 0
                            }

                            ctx.source.sender.sendMessage(
                                Component.text("Removed team ").append(team.displayName)
                            )
                            updateSidebars()
                            Command.SINGLE_SUCCESS
                        }
                )
        )

        registerer.add(
            Commands.literal("removeAll")
                .requires { source -> source.sender.hasPermission("innotournament.admin") }
                .executes { ctx ->
                    teamManager.getAllTeams().forEach { teamManager.removeTeamFromCommand(it) }
                    ctx.source.sender.sendMessage(Component.text("All teams removed"))
                    Command.SINGLE_SUCCESS
                }
        )

        registerer.register()
    }

    private fun myTeamMessage(player: PluginPlayer, team: PluginTeam): Component {
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
        player: PluginPlayer,
        team: PluginTeam,
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

    private fun acceptAllLine(team: PluginTeam, isOwner: Boolean): Component {
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
        val team = teamManager.getTeamById(teamId)
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
        val teams = teamManager.getAllTeams()
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

    private fun sendJoinPlayer(player: PluginPlayer, team: PluginTeam) {
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

    private fun checkTeam(ctx: CommandContext<CommandSourceStack>): PluginTeam? {
        val player = player(ctx) ?: return null
        val team = player.team
        if (team == null) {
            ctx.source.sender.sendMessage(Component.text("You're not on the team.", NamedTextColor.RED))
            return null
        }
        return team
    }

    private fun checkOwner(ctx: CommandContext<CommandSourceStack>): PluginPlayer? {
        val player = player(ctx) ?: return null
        val team = player.team ?: return null
        if (team.owner == player) return player

        ctx.source.sender.sendMessage(Component.text("Only team owner can do this", NamedTextColor.RED))
        return null
    }

    private fun player(ctx: CommandContext<CommandSourceStack>): PluginPlayer? {
        val sender = ctx.source.sender
        if (sender !is Player) {
            sender.sendMessage(Component.text("This command requires a player", NamedTextColor.RED))
            return null
        }
        return PluginPlayer.get(sender)
    }

    private fun updateSidebars() {
        Tournament.get()?.updateSidebars()
    }

    private fun <S, T> RequiredArgumentBuilder<S, T>.suggestTeams(): RequiredArgumentBuilder<S, T> {
        return suggests { _, builder ->
            teamManager.getAllTeams().forEach { builder.suggest(it.id) }
            builder.buildFuture()
        }
    }

    private fun <S, T> RequiredArgumentBuilder<S, T>.suggestPlayers(): RequiredArgumentBuilder<S, T> {
        return suggests { _, builder ->
            Bukkit.getOnlinePlayers().forEach { builder.suggest(it.name) }
            teamManager.getAllTeams().forEach { team ->
                team.players.forEach { builder.suggest(it.name) }
            }
            builder.buildFuture()
        }
    }

    private fun getTargetPlayer(ctx: CommandContext<CommandSourceStack>): PluginPlayer? {
        val name = StringArgumentType.getString(ctx, "player")
        val target = Bukkit.getPlayerExact(name)
            ?: Bukkit.getOfflinePlayerIfCached(name)
            ?: Bukkit.getOfflinePlayers().firstOrNull { it.name?.equals(name, ignoreCase = true) == true }
        if (target != null) return PluginPlayer.get(target.uniqueId)

        ctx.source.sender.sendMessage(Component.text("Player not found in local server data", NamedTextColor.RED))
        return null
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
