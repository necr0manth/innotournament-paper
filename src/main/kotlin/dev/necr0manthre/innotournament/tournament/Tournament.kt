package dev.necr0manthre.innotournament.tournament

import dev.necr0manthre.innotournament.Innotournament
import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.teams.PluginTeam
import dev.necr0manthre.innotournament.teams.events.PlayerTeamEvent
import dev.necr0manthre.innotournament.teams.events.TeamEvents
import dev.necr0manthre.innotournament.tournament.events.EventSubscriptionView
import dev.necr0manthre.innotournament.tournament.events.ITournamentEventListener
import dev.necr0manthre.innotournament.tournament.events.SimpleEvent
import dev.necr0manthre.innotournament.tournament.events.TournamentEvent
import dev.necr0manthre.innotournament.tournament.events.TournamentEventInstance
import dev.necr0manthre.innotournament.tournament.events.asSubscriptionView
import com.destroystokyo.paper.event.server.ServerTickEndEvent
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.block.structure.Mirror
import org.bukkit.block.structure.StructureRotation
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerRespawnEvent
import java.util.Random
import kotlin.math.min

class Tournament(
    val tournamentSpawn: Location,
    var tournamentPreSpawn: Location,
    var startBoxStructureResourceLocation: NamespacedKey?,
    var startBoxPos: Location,
    private val preStartGameMode: GameMode,
    private val preStartEvents: Map<TournamentEvent<*>, List<ITournamentEventListener<*>>>,
    private val mainEvents: Map<TournamentEvent<*>, List<ITournamentEventListener<*>>>,
    var teamScoreModifiers: DoubleArray,
    val lastTeamScore: Float
) {
    private val onPrepareEvent = SimpleEvent<Any?>()
    private val onStartEvent = SimpleEvent<Any?>()
    private val onEndEvent = SimpleEvent<Any?>()
    private val onRemoveEvent = SimpleEvent<Any?>()
    private val playerRunOutOfLivesEvent = SimpleEvent<TournamentPlayer>()
    val onPrepare: EventSubscriptionView<Any?> = onPrepareEvent.asSubscriptionView()
    val onStart: EventSubscriptionView<Any?> = onStartEvent.asSubscriptionView()
    val onEnd: EventSubscriptionView<Any?> = onEndEvent.asSubscriptionView()
    val onRemove: EventSubscriptionView<Any?> = onRemoveEvent.asSubscriptionView()
    val playerRunOutOfLives: EventSubscriptionView<TournamentPlayer> = playerRunOutOfLivesEvent.asSubscriptionView()
    val advancementHandler = TournamentAdvancementHandler(this)

    var phase: Int = 0
        private set
    var startTime: Long = 0L

    private val preStartEventInstances = mutableSetOf<TournamentEventInstance<*>>()
    private val mainEventInstances = mutableSetOf<TournamentEventInstance<*>>()
    private val removedEvents = mutableSetOf<TournamentEventInstance<*>>()
    private val players = LinkedHashMap<PluginPlayer, TournamentPlayer>()
    private val teams = LinkedHashMap<PluginTeam, TournamentTeam>()
    private var listener: Listener? = null
    private var teamJoinListener: ((PlayerTeamEvent) -> Unit)? = null
    private var teamLeaveListener: ((PlayerTeamEvent) -> Unit)? = null

    fun getServer(): Server = Bukkit.getServer()

    fun updateSidebars() {
        // Scoreboard/sidebar presentation is intentionally separate from team ownership.
    }

    fun remove() {
        println("$this is being removed")
        onRemoveEvent.invoke(null)
        removeEvents(preStartEventInstances)
        removeEvents(mainEventInstances)
        advancementHandler.unregister()
        listener?.let { HandlerList.unregisterAll(it) }
        listener = null
        unregisterTeamEventListeners()
        clearParticipationState()
        phase = 0
        println("$this is removed")
    }

    fun placeStartBox() {
        val structureKey = startBoxStructureResourceLocation ?: return
        val structure = Bukkit.getStructureManager().loadStructure(structureKey)
            ?: run {
                Innotournament.instance.logger.warning("Unknown start box structure: $structureKey")
                return
            }
        structure.place(startBoxPos, true, StructureRotation.NONE, Mirror.NONE, 0, 1.0f, Random())
    }

    fun removeStartBox() {
        val structureKey = startBoxStructureResourceLocation ?: return
        val structure = Bukkit.getStructureManager().loadStructure(structureKey)
            ?: run {
                Innotournament.instance.logger.warning("Unknown start box structure: $structureKey")
                return
            }
        val world = startBoxPos.world ?: return
        val size = structure.size
        for (x in 0 until size.blockX) {
            for (y in 0 until size.blockY) {
                for (z in 0 until size.blockZ) {
                    world.getBlockAt(startBoxPos.blockX + x, startBoxPos.blockY + y, startBoxPos.blockZ + z)
                        .type = Material.AIR
                }
            }
        }
    }

    fun prepare(server: Any? = null) {
        check(phase != 1) { "Tournament is already prepared" }
        check(phase != 2) { "Tournament is already started" }
        check(phase != 3) { "Tournament is already ended" }

        registerListener()
        registerTeamEventListeners()
        setWorldBorderAndSpawn(tournamentPreSpawn)
        phase = 1
        subscribeAll(preStartEvents, preStartEventInstances)
        Bukkit.getOnlinePlayers().forEach(::checkPlayer)
        onPrepareEvent.invoke(null)
        advancementHandler.register()
    }

    fun start() {
        check(phase != 0) { "Tournament is not prepared yet" }
        check(phase != 2) { "Tournament is already started" }
        check(phase != 3) { "Tournament is already ended" }

        subscribeAll(mainEvents, mainEventInstances)
        removeEvents(preStartEventInstances)
        phase = 2
        startTime = timeWorld()?.fullTime ?: 0L
        setWorldBorderAndSpawn(tournamentSpawn)
        PluginTeam.getAllTeams().forEach(::setupTeam)
        onStartEvent.invoke(null)
        Bukkit.getOnlinePlayers().forEach(::checkPlayer)
        checkAllTeams()
    }

    fun checkPlayer(player: Player) {
        val pluginPlayer = PluginPlayer.get(player)
        if (phase == 1 && !player.hasPermission("innotournament.admin")) {
            player.gameMode = preStartGameMode
            return
        }

        if (phase != 2) return
        val pluginTeam = pluginPlayer.team
        if (pluginTeam == null) {
            clearPlayer(pluginPlayer)
            player.gameMode = GameMode.SPECTATOR
            return
        }

        if (pluginPlayer.tournamentState.tournament !== this) {
            setupPlayer(player)
        }

        val tournamentPlayer = getPlayer(pluginPlayer)
        if (tournamentPlayer.lives == 0) {
            player.gameMode = GameMode.SPECTATOR
        }

        setupTeamIfNeeded(pluginTeam)
    }

    fun checkAllTeams() {
        PluginTeam.getAllTeams().forEach(::checkTeam)
    }

    fun checkTeam(team: PluginTeam) {
        if (phase != 2) return
        val tournamentTeam = setupTeamIfNeeded(team)
        if (team.owner == null || !team.players.contains(team.owner)) {
            team.owner = team.players.firstOrNull()
        }
        if (team.players.isEmpty()) {
            tournamentTeam.score = 0.0
        }
    }

    val elapsedTime: Long
        get() = if (phase >= 2) (timeWorld()?.fullTime ?: startTime) - startTime else 0L

    fun setupPlayer(player: Player): TournamentPlayer {
        val pluginPlayer = PluginPlayer.get(player)
        val playerData = getPlayer(pluginPlayer)
        playerData.lives = 3
        player.health = player.maxHealth
        player.inventory.clear()
        player.foodLevel = 20
        player.saturation = 1f
        player.gameMode = GameMode.SURVIVAL
        player.teleport(centerBlock(tournamentSpawn))
        return playerData
    }

    fun setupTeam(team: PluginTeam): TournamentTeam {
        val tournamentTeam = getTeam(team)
        tournamentTeam.score = 0.0
        tournamentTeam.advancements.clear()
        tournamentTeam.acceptAll = false
        return tournamentTeam
    }

    fun addScoresToPlayer(player: TournamentPlayer?, score: Double) {
        if (player == null || player.lives == 0) return
        addScores(player.team, score)
    }

    fun addScores(team: TournamentTeam?, score: Double) {
        if (team == null) return
        team.score += score * scoreModifier(team.players.size)
        updateSidebars()
    }

    fun getTopTeams(n: Int): MutableList<TournamentTeam> {
        val sorted = teams.values
            .sortedByDescending { it.score }
            .toMutableList()
        return sorted.subList(0, min(n, sorted.size))
    }

    fun onPlayerJoinTeam(event: PlayerTeamEvent) {
        if (phase != 2) return
        val pluginTeam = event.team ?: return
        val team = getTeam(pluginTeam)
        val previousSize = (pluginTeam.players.size - 1).coerceAtLeast(0)
        val oldModifier = scoreModifier(previousSize)
        if (oldModifier != 0.0) team.score *= scoreModifier(pluginTeam.players.size) / oldModifier
        event.player.player?.let(::checkPlayer)
        checkTeam(pluginTeam)
        updateSidebars()
    }

    fun onPlayerLeaveTeam(event: PlayerTeamEvent) {
        if (phase != 2) return
        val pluginTeam = event.team ?: return
        val team = teams[pluginTeam] ?: return
        if (pluginTeam.players.isEmpty()) {
            team.score = 0.0
        } else {
            val oldModifier = scoreModifier(pluginTeam.players.size + 1)
            if (oldModifier != 0.0) team.score *= scoreModifier(pluginTeam.players.size) / oldModifier
        }
        checkTeam(pluginTeam)
        updateSidebars()
    }

    fun getPlayer(player: PluginPlayer): TournamentPlayer {
        return players.getOrPut(player) {
            TournamentPlayer(player, this).also { TournamentSystem.setPlayerState(player, it) }
        }
    }

    fun getTeam(team: PluginTeam): TournamentTeam {
        return teams.getOrPut(team) {
            TournamentTeam(team, this).also { TournamentSystem.setTeamState(team, it) }
        }
    }

    fun getPlayer(player: Player): TournamentPlayer {
        return getPlayer(PluginPlayer.get(player))
    }

    private fun subscribeAll(
        events: Map<TournamentEvent<*>, List<ITournamentEventListener<*>>>,
        instances: MutableSet<TournamentEventInstance<*>>
    ) {
        events.forEach { (event, listeners) ->
            val instance = event.subscribe(this)
            listeners.forEach { instance.addWildcardListener(it) }
            instances.add(instance)
        }
    }

    private fun removeEvents(events: MutableSet<TournamentEventInstance<*>>) {
        events.toList().forEach(::removeEvent)
        events.clear()
    }

    private fun removeEvent(event: TournamentEventInstance<*>) {
        if (removedEvents.add(event)) event.unsubscribe()
    }

    private fun scoreModifier(teamSize: Int): Double {
        if (teamScoreModifiers.isEmpty()) return 1.0
        return teamScoreModifiers[teamSize.coerceIn(teamScoreModifiers.indices)]
    }

    private fun registerListener() {
        if (listener != null) return
        listener = object : Listener {
            @EventHandler
            fun onTick(event: ServerTickEndEvent) {
                tick()
            }

            @EventHandler
            fun onDeath(event: PlayerDeathEvent) {
                val data = players[PluginPlayer.get(event.entity)] ?: return
                if (phase == 2 && data.lives > 0) {
                    data.lives--
                    updateSidebars()
                    if (data.lives == 0) playerRunOutOfLivesEvent.invoke(data)
                }
            }

            @EventHandler
            fun onRespawn(event: PlayerRespawnEvent) {
                if (phase == 1) {
                    event.respawnLocation = centerBlock(tournamentPreSpawn)
                } else if (phase == 2) {
                    event.respawnLocation = centerBlock(tournamentSpawn)
                }
            }
        }.also { Bukkit.getPluginManager().registerEvents(it, Innotournament.instance) }
    }

    private fun tick() {
        if (phase != 2) return
        Bukkit.getOnlinePlayers().forEach(::checkPlayer)
        checkAllTeams()

        val aliveTeams = teams.values.filter { team ->
            team.players.any { it.lives > 0 }
        }
        if (aliveTeams.size > 1) return

        aliveTeams.singleOrNull()?.let { it.score += lastTeamScore }
        broadcastFinalStandings()
        onEndEvent.invoke(null)
        phase = 3
        removeEvents(mainEventInstances)
        advancementHandler.unregister()
        unregisterTeamEventListeners()
        clearParticipationState()
    }

    private fun broadcastFinalStandings() {
        val standings = teams.values.sortedByDescending { it.score }
        if (standings.isEmpty()) {
            Bukkit.broadcast(Component.text("Tournament ended"))
            return
        }

        val message = Component.text()
            .append(Component.text("Tournament ended. Results: "))
        standings.forEachIndexed { index, team ->
            if (index > 0) message.append(Component.text(", "))
            message.append(Component.text("${index + 1}. "))
                .append(team.displayName)
                .append(Component.text(" (${team.score})"))
        }
        Bukkit.broadcast(message.build())
    }

    private fun registerTeamEventListeners() {
        if (teamJoinListener != null || teamLeaveListener != null) return
        teamJoinListener = ::onPlayerJoinTeam
        teamLeaveListener = ::onPlayerLeaveTeam
        TeamEvents.afterJoin.subscribe(teamJoinListener!!)
        TeamEvents.afterLeave.subscribe(teamLeaveListener!!)
    }

    private fun unregisterTeamEventListeners() {
        teamJoinListener?.let { TeamEvents.afterJoin.unsubscribe(it) }
        teamLeaveListener?.let { TeamEvents.afterLeave.unsubscribe(it) }
        teamJoinListener = null
        teamLeaveListener = null
    }

    private fun setupTeamIfNeeded(team: PluginTeam): TournamentTeam {
        return teams[team] ?: setupTeam(team)
    }

    private fun clearPlayer(player: PluginPlayer) {
        players.remove(player)
        TournamentSystem.clearPlayerState(player, this)
    }

    private fun clearParticipationState() {
        players.keys.toList().forEach { TournamentSystem.clearPlayerState(it, this) }
        teams.keys.toList().forEach { TournamentSystem.clearTeamState(it, this) }
        players.clear()
        teams.clear()
    }

    private fun setWorldBorderAndSpawn(location: Location) {
        val world = location.world ?: return
        world.setSpawnLocation(centerBlock(location))
        world.worldBorder.center = Location(world, location.x, location.y, location.z)
    }

    private fun centerBlock(location: Location): Location {
        val world = location.world
        return Location(world, location.blockX + 0.5, location.blockY.toDouble(), location.blockZ + 0.5)
    }

    private fun timeWorld(): World? {
        return Bukkit.getWorlds().firstOrNull { it.environment == World.Environment.NORMAL }
            ?: Bukkit.getWorlds().firstOrNull()
    }

    companion object {
        private var tournament: Tournament? = null

        fun setTournament(tournament: Tournament?) {
            this.tournament?.remove()
            this.tournament = tournament
        }

        fun setTournament(server: Any?, tournament: Tournament?) {
            setTournament(tournament)
        }

        fun get(): Tournament? {
            return tournament
        }

        fun get(server: Any?): Tournament? {
            return get()
        }
    }
}
