package dev.necr0manthre.innotournament

import dev.necr0manthre.innotournament.commands.InnoGiveCommand
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

@Suppress("UnstableApiUsage")
class Innotournament : JavaPlugin() {
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

        val profiles = GeneratorProfileReader().load(dataFolder)
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
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            InnoGiveCommand.register(event.registrar())
        }
    }

    override fun onDisable() {
        if (::generatorTickTask.isInitialized) generatorTickTask.cancel()
        HandlerList.unregisterAll(this)
        if (::generatorPlacementLifecycle.isInitialized) HandlerList.unregisterAll(generatorPlacementLifecycle)
        if (::generatorProtectionListener.isInitialized) HandlerList.unregisterAll(generatorProtectionListener)
        if (::generatorInteractionListener.isInitialized) HandlerList.unregisterAll(generatorInteractionListener)
        if (::generatorPlacementLifecycle.isInitialized) generatorPlacementLifecycle.close()
        if (::generatorRuntime.isInitialized) generatorRuntime.close()
    }

    private lateinit var sharedChunkTickets: PluginChunkTickets

    companion object {
        lateinit var instance: Innotournament
    }
}
