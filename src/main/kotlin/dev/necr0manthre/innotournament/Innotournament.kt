package dev.necr0manthre.innotournament

import dev.necr0manthre.innotournament.commands.InnoGiveCommand
import dev.necr0manthre.innotournament.commands.InnoMapCommand
import dev.necr0manthre.innotournament.commands.TeamsCommand
import dev.necr0manthre.innotournament.commands.TournamentCommand
import dev.necr0manthre.innotournament.commands.WorldBorderCommand
import dev.necr0manthre.innotournament.generators.*
import dev.necr0manthre.innotournament.init.InnoTournamentEventHandlers
import dev.necr0manthre.innotournament.init.InnoTournamentEvents
import dev.necr0manthre.innotournament.presentation.map.*
import dev.necr0manthre.innotournament.teams.PluginTeam.TeamManager
import dev.necr0manthre.innotournament.teams.invites.InviteSystem
import dev.necr0manthre.innotournament.teams.persistence.TeamsJsonStore
import dev.necr0manthre.innotournament.tournament.*
import dev.necr0manthre.innotournament.tournament.environment.*
import dev.necr0manthre.innotournament.util.CommandRegisterer
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.World
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.nio.file.Path
import kotlin.math.floor

@Suppress("UnstableApiUsage")
class Innotournament : JavaPlugin(), Listener {
    private lateinit var teamManager: TeamManager
    private lateinit var inviteSystem: InviteSystem
    private lateinit var generatorRuntime: GeneratorRuntime
    private lateinit var generatorPlacementLifecycle: GeneratorPlacementLifecycle
    private lateinit var generatorProtectionListener: GeneratorProtectionListener
    private lateinit var generatorInteractionListener: GeneratorInteractionListener
    private lateinit var mapPresentation: MapPresentation
    private lateinit var mapItemDelivery: MapItemDelivery
    private lateinit var journeyMapDelivery: JourneyMapDelivery
    private lateinit var tournamentControl: TournamentControl
    private lateinit var tournamentConfigEditor: TournamentConfigEditor
    private lateinit var generatorTickTask: BukkitTask
    private lateinit var teamSaveTask: BukkitTask
    private lateinit var teamsPath: Path
    private var selectedMapOwner: Tournament? = null
    private var runtimeReady = false

    override fun onEnable() {
        instance = this
        InnoTournamentEvents.init()
        InnoTournamentEventHandlers.init()

        teamsPath = dataFolder.toPath().resolve("teams.json")
        val restoredTeams = TeamsJsonStore.load(teamsPath)
        val profiles = GeneratorProfileReader().load(dataFolder)
        val mapStore = MapPresentationStore(dataFolder.toPath().resolve(MapPresentationStore.FILE_NAME))

        teamManager = TeamManager(restoredTeams)
        inviteSystem = InviteSystem(teamManager)

        sharedChunkTickets = PluginChunkTickets(this)
        val items = GeneratorItems()
        val placements = GeneratorPlacementStore()
        val displays = GeneratorDisplays(items)
        val generatorSystem = ItemGeneratorSystem(profiles)
        generatorRuntime = GeneratorRuntime(generatorSystem, placements, displays, sharedChunkTickets)
        generatorPlacementLifecycle = GeneratorPlacementLifecycle(items, placements, generatorRuntime)
        generatorProtectionListener = GeneratorProtectionListener(placements)
        generatorInteractionListener = GeneratorInteractionListener(items, generatorPlacementLifecycle, generatorRuntime)

        server.pluginManager.registerEvents(generatorPlacementLifecycle, this)
        server.pluginManager.registerEvents(generatorProtectionListener, this)
        server.pluginManager.registerEvents(generatorInteractionListener, this)
        server.worlds.forEach(generatorPlacementLifecycle::discoverLoadedWorld)

        mapPresentation = MapPresentation(this, generatorPlacementLifecycle, mapStore)
        mapItemDelivery = MapItemDelivery(this, mapPresentation)
        journeyMapDelivery = JourneyMapDelivery(
            this,
            generatorPlacementLifecycle,
            mapStore,
            config.getBoolean("journeymap.enabled", false)
        )

        inviteSystem.register(this)
        generatorTickTask = server.scheduler.runTaskTimer(this, Runnable(generatorRuntime::tick), 1L, 1L)
        teamSaveTask = server.scheduler.runTaskTimer(this, Runnable(::saveTeams), 200L, 200L)
        server.pluginManager.registerEvents(this, this)

        val tournamentRepository = TournamentConfigRepository()
        tournamentControl = TournamentControl(tournamentRepository, ::createTournament)
        tournamentConfigEditor = TournamentConfigEditor(tournamentRepository)
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val registrar = event.registrar()
            TeamsCommand(teamManager, inviteSystem).register(registrar)
            TournamentCommand(tournamentControl, tournamentConfigEditor, dataFolder.toPath()).register(registrar)
            WorldBorderCommand.register(registrar)
            val inno = CommandRegisterer(registrar, "inno")
            inno.add(InnoGiveCommand.build())
            inno.add(InnoMapCommand.build(mapItemDelivery))
            inno.register()
        }

        runtimeReady = true
    }

    override fun onDisable() {
        if (runtimeReady) {
            tournamentControl.stop(RemovalReason.SHUTDOWN)
            saveTeams()
        }
        if (::generatorTickTask.isInitialized) generatorTickTask.cancel()
        if (::teamSaveTask.isInitialized) teamSaveTask.cancel()
        if (::journeyMapDelivery.isInitialized) journeyMapDelivery.close()
        if (::mapPresentation.isInitialized) mapPresentation.close()
        if (::inviteSystem.isInitialized) inviteSystem.close()
        HandlerList.unregisterAll(this as Listener)
        if (::generatorPlacementLifecycle.isInitialized) HandlerList.unregisterAll(generatorPlacementLifecycle)
        if (::generatorProtectionListener.isInitialized) HandlerList.unregisterAll(generatorProtectionListener)
        if (::generatorInteractionListener.isInitialized) HandlerList.unregisterAll(generatorInteractionListener)
        if (::generatorPlacementLifecycle.isInitialized) generatorPlacementLifecycle.close()
        if (::generatorRuntime.isInitialized) generatorRuntime.close()
        runtimeReady = false
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (runtimeReady) mapItemDelivery.ensureCurrentWorldMap(event.player)
    }

    @EventHandler
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        if (runtimeReady) mapItemDelivery.ensureCurrentWorldMap(event.player)
    }

    private fun createTournament(definition: LoadedTournamentDefinition): Tournament {
        val settings = definition.settings
        val bindings = ResolvedWorldBindings.resolve(
            settings.tournamentPreSpawnDimension,
            settings.tournamentSpawnDimension,
            server.worlds
        )
        val preStartSpawn = WorldPoint(
            bindings.preStartWorld,
            settings.tournamentPreSpawn.x + 0.5,
            settings.tournamentPreSpawn.y.toDouble(),
            settings.tournamentPreSpawn.z + 0.5
        )
        val gameSpawn = WorldPoint(
            bindings.gameWorld,
            settings.tournamentSpawn.x + 0.5,
            settings.tournamentSpawn.y.toDouble(),
            settings.tournamentSpawn.z + 0.5
        )
        val startBoxOrigin = WorldPoint(
            bindings.preStartWorld,
            settings.startBoxPos.x.toDouble(),
            settings.startBoxPos.y.toDouble(),
            settings.startBoxPos.z.toDouble()
        )
        val environment = TournamentEnvironment(
            bindings,
            preStartSpawn,
            gameSpawn,
            sharedChunkTickets,
            this,
            server.structureManager,
            settings.startBoxStructureResourceLocation,
            startBoxOrigin
        )

        lateinit var tournament: Tournament
        val presentation = TournamentPresentation(
            this,
            TournamentSidebarTransport(checkNotNull(server.scoreboardManager)),
            {
                when (tournament.phase) {
                    TournamentPhase.PREPARED -> TournamentSidebarCapture.capturePrepared(teamManager)
                    TournamentPhase.STARTED -> TournamentSidebarCapture.captureStarted(tournament, teamManager)
                    else -> error("Cannot capture a live sidebar during ${tournament.phase}")
                }
            }
        )
        tournament = Tournament(
            teamManager,
            definition,
            environment,
            TournamentPlayerEffects(environment),
            presentation,
            this,
            mapItemDelivery::ensureCurrentWorldMap,
            { run ->
                val areas = mapAreas(bindings)
                mapItemDelivery.setCurrentAreas(areas)
                selectedMapOwner = run
                val selectedWorldIds = areas.mapTo(HashSet()) { it.worldId }
                server.onlinePlayers.filter { it.world.uid in selectedWorldIds }
                    .forEach(mapItemDelivery::ensureCurrentWorldMap)
                generatorRuntime.resetForWorlds(setOf(bindings.gameWorld.uid))
            }
        )
        tournament.onRemove.subscribe {
            if (selectedMapOwner === tournament) {
                mapItemDelivery.clearCurrentAreas()
                selectedMapOwner = null
            }
        }
        return tournament
    }

    private lateinit var sharedChunkTickets: PluginChunkTickets

    private fun mapAreas(bindings: ResolvedWorldBindings): List<MapAreaKey> = listOf(
        bindings.gameWorld,
        bindings.normalWorld,
        bindings.netherWorld,
        bindings.endWorld
    ).distinctBy { it.uid }.map { world ->
        val border = world.worldBorder
        val center = border.center
        MapAreaKey(
            world.uid,
            floor(center.x).toInt(),
            floor(center.z).toInt(),
            mapScaleForBorderSide(border.size)
        )
    }

    private fun saveTeams() {
        TeamsJsonStore.save(teamsPath, teamManager.snapshot())
    }

    companion object {
        lateinit var instance: Innotournament
    }
}
