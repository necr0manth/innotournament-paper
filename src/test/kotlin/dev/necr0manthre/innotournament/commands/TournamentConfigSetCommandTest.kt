package dev.necr0manthre.innotournament.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContextBuilder
import com.mojang.brigadier.context.ParsedArgument
import com.mojang.brigadier.tree.ArgumentCommandNode
import dev.necr0manthre.innotournament.tournament.TournamentConfigEditor
import dev.necr0manthre.innotournament.tournament.TournamentConfigRepository
import dev.necr0manthre.innotournament.tournament.TournamentControl
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver
import io.papermc.paper.math.Position
import net.kyori.adventure.text.Component
import org.bukkit.NamespacedKey
import org.bukkit.command.CommandSender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path

class TournamentConfigSetCommandTest {
    @TempDir lateinit var temp: Path

    companion object {
        @JvmStatic @BeforeAll fun bootstrapVanillaArguments() {
            PaperCommandTestBootstrap.ensure()
        }
    }

    @Test
    fun exposesOnlyTheOriginalSixBranchesAndInputNamesBehindPluginPermission() {
        val repository = TournamentConfigRepository()
        val tree = TournamentConfigSetCommand.build(
            TournamentControl(repository) { error("set must not create a tournament") },
            TournamentConfigEditor(repository),
        ).build()
        val branches = tree.children.associateBy { it.name }

        assertEquals(
            setOf(
                "tournamentPreSpawn",
                "tournamentPreSpawnDimension",
                "tournamentSpawn",
                "tournamentSpawnDimension",
                "startBoxStructureResourceLocation",
                "startBoxPos",
            ),
            branches.keys,
        )
        for (branch in setOf("tournamentPreSpawn", "tournamentSpawn", "startBoxPos")) {
            val argument = branches.getValue(branch).children.single() as ArgumentCommandNode<*, *>
            assertEquals("pos", argument.name)
            assertEquals(ArgumentTypes.blockPosition()::class, argument.type::class)
        }
        for (branch in setOf("tournamentPreSpawnDimension", "tournamentSpawnDimension")) {
            val argument = branches.getValue(branch).children.single() as ArgumentCommandNode<*, *>
            assertEquals("dimension", argument.name)
            assertEquals(ArgumentTypes.world()::class, argument.type::class)
        }
        val template = branches.getValue("startBoxStructureResourceLocation").children.single()
            as ArgumentCommandNode<*, *>
        assertEquals("template", template.name)
        assertEquals(ArgumentTypes.namespacedKey()::class, template.type::class)
        assertTrue(template.customSuggestions != null)
        assertFalse(tree.requirement.test(sourceWithPermissions("minecraft.command.function")))
        assertFalse(tree.requirement.test(sourceWithPermissions()))
        assertTrue(tree.requirement.test(sourceWithPermissions("innotournament.admin")))
    }

    @Test
    fun editsThePathSelectedAtExecutionAndRefusesWhenNothingIsSelected() {
        val repository = TournamentConfigRepository()
        val editor = TournamentConfigEditor(repository)
        val control = TournamentControl(repository) { error("set must not create a tournament") }
        val selected = config("selected.json")
        val other = config("other.json")
        val command = TournamentConfigSetCommand.build(control, editor).build()
        val templateNode = command.children
            .first { it.name == "startBoxStructureResourceLocation" }
            .children.single()
        val changedKey = NamespacedKey("example", "changed")

        control.select(selected)
        control.select(other)
        val execute = requireNotNull(templateNode.command)
        val dispatcher = CommandDispatcher<CommandSourceStack>()
        val selectedSource = sourceWithPermissions("innotournament.admin")
        val context = CommandContextBuilder(dispatcher, selectedSource, dispatcher.root, 0)
            .withArgument(
                "template",
                ParsedArgument<CommandSourceStack, NamespacedKey>(0, changedKey.toString().length, changedKey),
            )
            .build(changedKey.toString())

        assertEquals(1, execute.run(context))
        assertEquals("minecraft:start", repository.readSettings(selected).startBoxStructureResourceLocation.toString())
        assertEquals(changedKey, repository.readSettings(other).startBoxStructureResourceLocation)

        control.select(selected)
        val positionNode = command.children.first { it.name == "tournamentPreSpawn" }.children.single()
        val resolver = Proxy.newProxyInstance(
            BlockPositionResolver::class.java.classLoader,
            arrayOf(BlockPositionResolver::class.java),
        ) { _, method, _ -> if (method.name == "resolve") Position.block(-4, 71, 19) else null }
            as BlockPositionResolver
        val positionContext = CommandContextBuilder(dispatcher, selectedSource, dispatcher.root, 0)
            .withArgument(
                "pos",
                ParsedArgument<CommandSourceStack, BlockPositionResolver>(0, 5, resolver),
            )
            .build("~ ~ ~")
        assertEquals(1, positionNode.command!!.run(positionContext))
        assertEquals(-4, repository.readSettings(selected).tournamentPreSpawn.x)
        assertEquals(83, repository.readSettings(other).tournamentPreSpawn.y)

        val unselectedControl = TournamentControl(repository) { error("set must not create a tournament") }
        val unselectedTree = TournamentConfigSetCommand.build(unselectedControl, editor).build()
        val unselectedExecute = unselectedTree.children
            .first { it.name == "startBoxStructureResourceLocation" }
            .children.single().command!!
        val messages = mutableListOf<Component>()
        val unselectedSource = sourceWithPermissions("innotournament.admin", messages = messages)
        val refused = CommandContextBuilder(dispatcher, unselectedSource, dispatcher.root, 0)
            .withArgument(
                "template",
                ParsedArgument<CommandSourceStack, NamespacedKey>(0, changedKey.toString().length, changedKey),
            )
            .build(changedKey.toString())

        assertEquals(0, unselectedExecute.run(refused))
        assertTrue(messages.isNotEmpty())
        assertEquals("minecraft:start", repository.readSettings(selected).startBoxStructureResourceLocation.toString())
        assertEquals(null, unselectedControl.selectedPath)
    }

    private fun config(name: String): Path {
        val path = temp.resolve(name)
        val sample = requireNotNull(javaClass.getResourceAsStream("/tournament/original-tournament.json"))
            .bufferedReader().use { it.readText() }
        Files.writeString(path, sample)
        return path
    }

    private fun sourceWithPermissions(
        vararg permissions: String,
        messages: MutableList<Component> = mutableListOf(),
    ): CommandSourceStack {
        val sender = Proxy.newProxyInstance(
            CommandSender::class.java.classLoader,
            arrayOf(CommandSender::class.java),
        ) { _, method, args ->
            when (method.name) {
                "hasPermission" -> permissions.contains(args?.singleOrNull())
                "sendMessage" -> { messages += args!!.single() as Component; null }
                "toString" -> "TestCommandSender"
                else -> null
            }
        } as CommandSender
        return Proxy.newProxyInstance(
            CommandSourceStack::class.java.classLoader,
            arrayOf(CommandSourceStack::class.java),
        ) { _, method, _ -> if (method.name == "getSender") sender else null } as CommandSourceStack
    }
}
