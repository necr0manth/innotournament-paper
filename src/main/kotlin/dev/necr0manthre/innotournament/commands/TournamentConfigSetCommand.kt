package dev.necr0manthre.innotournament.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.SharedSuggestionProvider
import dev.necr0manthre.innotournament.tournament.BlockPosition
import dev.necr0manthre.innotournament.tournament.TournamentConfigEdit
import dev.necr0manthre.innotournament.tournament.TournamentConfigEditor
import dev.necr0manthre.innotournament.tournament.TournamentControl
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.craftbukkit.CraftServer

/** The original six `/tournament set` branches, attachable beneath CORE's tournament command. */
object TournamentConfigSetCommand {
    fun build(
        control: TournamentControl,
        editor: TournamentConfigEditor,
    ): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("set")
            .requires { it.sender.hasPermission("innotournament.admin") }
            .then(position("tournamentPreSpawn", "pos", control, editor, TournamentConfigEdit::PreSpawn))
            .then(dimension("tournamentPreSpawnDimension", control, editor, TournamentConfigEdit::PreSpawnDimension))
            .then(position("tournamentSpawn", "pos", control, editor, TournamentConfigEdit::Spawn))
            .then(dimension("tournamentSpawnDimension", control, editor, TournamentConfigEdit::SpawnDimension))
            .then(
                Commands.literal("startBoxStructureResourceLocation")
                    .then(
                        Commands.argument<NamespacedKey>("template", ArgumentTypes.namespacedKey())
                            .suggests { _, builder ->
                                SharedSuggestionProvider.suggestResource(structureTemplates(), builder)
                            }
                            .executes { context ->
                                apply(
                                    context,
                                    control,
                                    editor,
                                    TournamentConfigEdit.StartBoxStructure(
                                        context.getArgument("template", NamespacedKey::class.java)
                                    ),
                                )
                            }
                    )
            )
            .then(position("startBoxPos", "pos", control, editor, TournamentConfigEdit::StartBoxPosition))

    private fun position(
        branch: String,
        argument: String,
        control: TournamentControl,
        editor: TournamentConfigEditor,
        edit: (BlockPosition) -> TournamentConfigEdit,
    ) = Commands.literal(branch)
        .then(
            Commands.argument<BlockPositionResolver>(argument, ArgumentTypes.blockPosition())
                .executes { context ->
                    val resolved = context.getArgument(argument, BlockPositionResolver::class.java)
                        .resolve(context.source)
                    apply(
                        context,
                        control,
                        editor,
                        edit(BlockPosition(resolved.blockX(), resolved.blockY(), resolved.blockZ())),
                    )
                }
        )

    private fun dimension(
        branch: String,
        control: TournamentControl,
        editor: TournamentConfigEditor,
        edit: (NamespacedKey) -> TournamentConfigEdit,
    ) = Commands.literal(branch)
        .then(
            Commands.argument<World>("dimension", ArgumentTypes.world())
                .executes { context ->
                    val world = context.getArgument("dimension", World::class.java)
                    apply(context, control, editor, edit(world.key))
                }
        )

    private fun apply(
        context: CommandContext<CommandSourceStack>,
        control: TournamentControl,
        editor: TournamentConfigEditor,
        edit: TournamentConfigEdit,
    ): Int {
        val path = control.selectedPath ?: run {
            context.source.sender.sendMessage(Component.text("No tournament selected", NamedTextColor.RED))
            return 0
        }
        editor.apply(path, edit)
        context.source.sender.sendMessage(Component.text("Tournament settings updated", NamedTextColor.GREEN))
        return Command.SINGLE_SUCCESS
    }

    private fun structureTemplates() =
        (Bukkit.getServer() as CraftServer).server.structureManager.listTemplates().iterator().asSequence().toList()
}
