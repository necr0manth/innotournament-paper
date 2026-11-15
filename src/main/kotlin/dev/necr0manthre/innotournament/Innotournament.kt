package dev.necr0manthre.innotournament

import dev.necr0manthre.innotournament.commands.InnoGiveCommand
import dev.necr0manthre.innotournament.commands.TeamsCommand
import dev.necr0manthre.innotournament.commands.TournamentCommand
import dev.necr0manthre.innotournament.commands.WorldBorderCommand
import dev.necr0manthre.innotournament.teams.PluginTeam.TeamManager
import dev.necr0manthre.innotournament.teams.invites.InviteSystem
import dev.necr0manthre.innotournament.teams.persistence.TeamsJsonStore
import dev.necr0manthre.innotournament.generators.GeneratorDisplays
import dev.necr0manthre.innotournament.generators.GeneratorInteractionListener
import dev.necr0manthre.innotournament.generators.GeneratorItems
import dev.necr0manthre.innotournament.generators.GeneratorPlacementLifecycle
import dev.necr0manthre.innotournament.generators.GeneratorPlacementStore
import dev.necr0manthre.innotournament.generators.GeneratorProfileReader
import dev.necr0manthre.innotournament.generators.GeneratorProtectionListener
import dev.necr0manthre.innotournament.generators.GeneratorRuntime
import dev.necr0manthre.innotournament.generators.ItemGeneratorSystem
import dev.necr0manthre.innotournament.init.InnoTournamentEventHandlers
import dev.necr0manthre.innotournament.init.InnoTournamentEvents
import dev.necr0manthre.innotournament.tournament.environment.PluginChunkTickets
import dev.necr0manthre.innotournament.util.ServerScheduler
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.event.HandlerList
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.nio.file.Path

@Suppress("UnstableApiUsage")
class Innotournament : JavaPlugin() {
    private lateinit var teamManager: TeamManager
    private lateinit var inviteSystem: InviteSystem
    private lateinit var teamSaveTask: BukkitTask
    private lateinit var teamsPath: Path
    private var runtimeReady = false
    private lateinit var generatorRuntime: GeneratorRuntime
    private lateinit var generatorPlacementLifecycle: GeneratorPlacementLifecycle
    private lateinit var generatorProtectionListener: GeneratorProtectionListener
    private lateinit var generatorInteractionListener: GeneratorInteractionListener
    private lateinit var generatorTickTask: BukkitTask

    override fun onEnable() {
        instance = this
        InnoTournamentEvents.init()
        InnoTournamentEventHandlers.init()
        ServerScheduler.init()

        teamsPath = dataFolder.toPath().resolve("teams.json")
        val restoredTeams = TeamsJsonStore.load(teamsPath)
        val profiles = GeneratorProfileReader().load(dataFolder)
        teamManager = TeamManager(restoredTeams)
        inviteSystem = InviteSystem(teamManager)
        sharedChunkTickets = PluginChunkTickets(this)
        val items = GeneratorItems()
        val placements = GeneratorPlacementStore()
        val displays = GeneratorDisplays(items)
        generatorRuntime = GeneratorRuntime(ItemGeneratorSystem(profiles), placements, displays, sharedChunkTickets)
        generatorPlacementLifecycle = GeneratorPlacementLifecycle(items, placements, generatorRuntime)
        generatorProtectionListener = GeneratorProtectionListener(placements)
        generatorInteractionListener = GeneratorInteractionListener(items, generatorPlacementLifecycle, generatorRuntime)

        server.pluginManager.registerEvents(generatorPlacementLifecycle, this)
        server.pluginManager.registerEvents(generatorProtectionListener, this)
        server.pluginManager.registerEvents(generatorInteractionListener, this)
        server.worlds.forEach(generatorPlacementLifecycle::discoverLoadedWorld)

        generatorTickTask = server.scheduler.runTaskTimer(this, Runnable(generatorRuntime::tick), 1L, 1L)
        inviteSystem.register(this)
        teamSaveTask = server.scheduler.runTaskTimer(this, Runnable(::saveTeams), 200L, 200L)
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val registrar = event.registrar()
            TeamsCommand(teamManager, inviteSystem).register(registrar)
            TournamentCommand(teamManager).register(registrar)
            WorldBorderCommand.register(registrar)
            InnoGiveCommand.register(registrar)
        }
        runtimeReady = true
    }

    override fun onDisable() {
        if (runtimeReady) saveTeams()
        if (::teamSaveTask.isInitialized) teamSaveTask.cancel()
        if (::inviteSystem.isInitialized) inviteSystem.close()
        runtimeReady = false
        if (::generatorTickTask.isInitialized) generatorTickTask.cancel()
        HandlerList.unregisterAll(this)
        if (::generatorPlacementLifecycle.isInitialized) HandlerList.unregisterAll(generatorPlacementLifecycle)
        if (::generatorProtectionListener.isInitialized) HandlerList.unregisterAll(generatorProtectionListener)
        if (::generatorInteractionListener.isInitialized) HandlerList.unregisterAll(generatorInteractionListener)
        if (::generatorPlacementLifecycle.isInitialized) generatorPlacementLifecycle.close()
        if (::generatorRuntime.isInitialized) generatorRuntime.close()
    }

    private lateinit var sharedChunkTickets: PluginChunkTickets

    private fun saveTeams() {
        TeamsJsonStore.save(teamsPath, teamManager.snapshot())
    }

    companion object {
        lateinit var instance: Innotournament
    }
}
