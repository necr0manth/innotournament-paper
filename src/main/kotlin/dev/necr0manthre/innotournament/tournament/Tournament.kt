package dev.necr0manthre.innotournament.tournament

import com.destroystokyo.paper.event.server.ServerTickEndEvent
import dev.necr0manthre.innotournament.Innotournament
import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.teams.PluginTeam
import dev.necr0manthre.innotournament.teams.events.TeamChange
import dev.necr0manthre.innotournament.teams.TeamManager
import dev.necr0manthre.innotournament.tournament.events.*
import net.kyori.adventure.text.Component
import org.bukkit.*
import org.bukkit.block.structure.Mirror
import org.bukkit.block.structure.StructureRotation
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerRespawnEvent
import java.util.*
import kotlin.math.min

class Tournament(
    private val teamManager: TeamManager,
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
    sealed interface PlayerTournamentState {
        val tournament: Tournament?
    }

    sealed interface TeamTournamentState {
        val tournament: Tournament?
    }

    private class PlayerTournamentStateImpl : PlayerTournamentState {
        var tournamentPlayer: TournamentPlayer? = null

        override val tournament: Tournament?
            get() = tournamentPlayer?.tournament
    }

    private class TeamTournamentStateImpl : TeamTournamentState {
        var tournamentTeam: TournamentTeam? = null
        var initialized: Boolean = false

        override val tournament: Tournament?
            get() = tournamentTeam?.tournament
    }

    private fun PluginPlayer.getTournamentState(): PlayerTournamentStateImpl =
        tournamentState as PlayerTournamentStateImpl

    private fun PluginTeam.getTournamentState(): TeamTournamentStateImpl =
        tournamentState as TeamTournamentStateImpl

    private fun clearPlayerState(player: PluginPlayer) {
        val state = player.getTournamentState()
        if (state.tournamentPlayer?.tournament === this) state.tournamentPlayer = null
    }

    private fun clearTeamState(team: PluginTeam) {
        val state = team.getTournamentState()
        if (state.tournamentTeam?.tournament === this) state.tournamentTeam = null
    }

    private fun isPlayerBoundToAnotherTournament(player: PluginPlayer): Boolean {
        val tournament = player.getTournamentState().tournament
        return tournament != null && tournament !== this
    }

    private val onPrepareEvent = SimpleEvent<Unit>()
    private val onStartEvent = SimpleEvent<Unit>()
    private val onEndEvent = SimpleEvent<Unit>()
    private val onRemoveEvent = SimpleEvent<Unit>()
    private val playerRunOutOfLivesEvent = SimpleEvent<TournamentPlayer>()
    val onPrepare: EventSubscriptionView<Unit> = onPrepareEvent.asSubscriptionView()
    val onStart: EventSubscriptionView<Unit> = onStartEvent.asSubscriptionView()
    val onEnd: EventSubscriptionView<Unit> = onEndEvent.asSubscriptionView()
    val onRemove: EventSubscriptionView<Unit> = onRemoveEvent.asSubscriptionView()
    val playerRunOutOfLives: EventSubscriptionView<TournamentPlayer> = playerRunOutOfLivesEvent.asSubscriptionView()
    val advancementHandler = TournamentAdvancementHandler(this)

    var phase: TournamentPhase = TournamentPhase.CREATED
        private set
    var startTime: Long = 0L

    private val preStartEventInstances = mutableSetOf<TournamentEventInstance<*>>()
    private val mainEventInstances = mutableSetOf<TournamentEventInstance<*>>()
    private val removedEvents = mutableSetOf<TournamentEventInstance<*>>()
    private val players = HashSet<TournamentPlayer>()
    private val teams = HashSet<TournamentTeam>()
    private var listener: Listener? = null
    private var teamJoinListener: ((TeamChange) -> Unit)? = null
    private var teamLeaveListener: ((TeamChange) -> Unit)? = null
    private var teamRemoveListener: ((PluginTeam) -> Unit)? = null

    fun getServer(): Server = Bukkit.getServer()

    fun updateSidebars() {
        // Scoreboard/sidebar presentation is intentionally separate from team ownership.
    }

    fun remove() {
        println("$this is being removed")
        onRemoveEvent.invoke(Unit)
        removeEvents(preStartEventInstances)
        removeEvents(mainEventInstances)
        advancementHandler.unregister()
        listener?.let { HandlerList.unregisterAll(it) }
        listener = null
        unregisterTeamEventListeners()
        clearParticipationState()
        phase = TournamentPhase.ENDED
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

    fun prepare() {
        check(phase != TournamentPhase.PREPARED) { "Tournament is already prepared" }
        check(phase != TournamentPhase.STARTED) { "Tournament is already started" }
        check(phase != TournamentPhase.ENDED) { "Tournament is already ended" }

        registerListener()
        registerTeamEventListeners()
        setWorldBorderAndSpawn(tournamentPreSpawn)
        phase = TournamentPhase.PREPARED
        subscribeAll(preStartEvents, preStartEventInstances)
        Bukkit.getOnlinePlayers().forEach(::checkPlayer)
        onPrepareEvent.invoke(Unit)
        advancementHandler.register()
    }

    fun start() {
        check(phase != TournamentPhase.CREATED) { "Tournament is not prepared yet" }
        check(phase != TournamentPhase.STARTED) { "Tournament is already started" }
        check(phase != TournamentPhase.ENDED) { "Tournament is already ended" }

        subscribeAll(mainEvents, mainEventInstances)
        removeEvents(preStartEventInstances)
        phase = TournamentPhase.STARTED
        startTime = timeWorld()?.fullTime ?: 0L
        setWorldBorderAndSpawn(tournamentSpawn)
        checkAllTeams()
        onStartEvent.invoke(Unit)
        Bukkit.getOnlinePlayers().forEach(::checkPlayer)
    }

    fun checkPlayer(player: Player) {
        val pluginPlayer = PluginPlayer.get(player)
        if (isPlayerBoundToAnotherTournament(pluginPlayer)) return

        if (phase == TournamentPhase.PREPARED && !player.hasPermission("innotournament.admin")) {
            player.gameMode = preStartGameMode
            return
        }

        if (phase != TournamentPhase.STARTED) return
        val pluginTeam = pluginPlayer.team
        if (pluginTeam == null) {
            clearPlayer(pluginPlayer)
            player.gameMode = GameMode.SPECTATOR
            return
        }

        val tournamentPlayer = getOrCreateTournamentPlayer(pluginPlayer) ?: return
        if (!tournamentPlayer.initialized) {
            tournamentPlayer.initialized = setupPlayer(tournamentPlayer)
        }

        if (tournamentPlayer.lives == 0) {
            player.gameMode = GameMode.SPECTATOR
        }
    }

    fun checkAllTeams() {
        teamManager.getAllTeams().forEach(::checkTeam)
    }

    fun checkTeam(team: PluginTeam) = checkTeam(team, resetAcceptAll = true)

    private fun checkTeam(team: PluginTeam, resetAcceptAll: Boolean) {
        if (phase != TournamentPhase.STARTED) return
        val tournamentTeam = getOrCreateTournamentTeam(team) ?: return
        val state = team.getTournamentState()
        if (!state.initialized) {
            setupTeam(tournamentTeam, resetAcceptAll)
            state.initialized = true
        }
    }

    val elapsedTime: Long
        get() = if (phase >= TournamentPhase.STARTED) (timeWorld()?.fullTime ?: startTime) - startTime else 0L

    private fun setupPlayer(tournamentPlayer: TournamentPlayer): Boolean {
        val player = tournamentPlayer.player ?: return false
        tournamentPlayer.lives = 3
        player.health = player.maxHealth
        player.inventory.clear()
        player.foodLevel = 20
        player.saturation = 1f
        player.gameMode = GameMode.SURVIVAL
        player.teleport(centerBlock(tournamentSpawn))
        return true
    }

    fun setupTeam(team: TournamentTeam) {
        setupTeam(team, resetAcceptAll = true)
    }

    private fun setupTeam(team: TournamentTeam, resetAcceptAll: Boolean) {
        team.score = 0.0
        team.advancements.clear()
        if (resetAcceptAll) team.acceptAll = false
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

    // The result set includes deleted history; only exact live registry objects receive places.
    private fun currentStandings(): List<TournamentTeam> = teams
        .filter { teamManager.getTeamById(it.pluginTeam.id) === it.pluginTeam }
        .sortedByDescending { it.score }

    fun getTopTeams(n: Int): MutableList<TournamentTeam> {
        val sorted = currentStandings().toMutableList()
        return sorted.subList(0, min(n, sorted.size))
    }

    fun onPlayerJoinTeam(event: TeamChange) {
        if (phase != TournamentPhase.STARTED) return
        val pluginTeam = event.team
        // A new team starts with acceptAll=false; initialize its tournament state
        // without writing that public team property inside the join callback.
        checkTeam(pluginTeam, resetAcceptAll = false)
        val team = getTeam(pluginTeam) ?: return
        val oldModifier = scoreModifier(event.sizeBefore)
        if (oldModifier != 0.0) {
            team.score *= scoreModifier(event.sizeAfter) / oldModifier
        }
        event.player.player?.let(::checkPlayer)
        updateSidebars()
    }

    fun onPlayerLeaveTeam(event: TeamChange) {
        if (phase != TournamentPhase.STARTED) return
        // A completed leave expires that runtime participation. A later join gets
        // a fresh TournamentPlayer and its normal setup, even on an A -> B move.
        clearPlayer(event.player)
        // Leaving never changes the historical score of the departing team.
        updateSidebars()
    }

    fun onTeamRemoved(team: PluginTeam) {
        // Preserve a finished tournament's historical links until remove().
        if (phase == TournamentPhase.ENDED) return
        // Keep the accumulated TournamentTeam in the result set, but release
        // the permanently disposed team's live back-reference.
        clearTeamState(team)
    }

    fun getOrCreateTournamentTeam(team: PluginTeam): TournamentTeam? {
        if (teamManager.getTeamById(team.id) !== team) return null
        val state = team.getTournamentState()
        val tournamentTeam = state.tournamentTeam ?: run {
            val newTournamentTeam = TournamentTeam(team, this)
            state.tournamentTeam = newTournamentTeam
            teams.add(newTournamentTeam)
            return@run newTournamentTeam
        }
        if (tournamentTeam.tournament != this)
            return null
        return tournamentTeam
    }

    fun getOrCreateTournamentPlayer(player: PluginPlayer): TournamentPlayer? {
        val team = player.team ?: return null
        if (getOrCreateTournamentTeam(team) == null)
            return null
        val playerState = player.getTournamentState()
        val tournamentPlayer: TournamentPlayer = playerState.tournamentPlayer ?: run {
            val newTournamentPlayer = TournamentPlayer(player, this)
            playerState.tournamentPlayer = newTournamentPlayer
            players.add(newTournamentPlayer)
            return@run newTournamentPlayer
        }
        if (tournamentPlayer.tournament != this)
            return null
        return tournamentPlayer
    }

    fun getPlayer(player: PluginPlayer): TournamentPlayer? {
        val state = player.getTournamentState()
        if (state.tournament != this)
            return null
        return state.tournamentPlayer
    }

    fun getTeam(team: PluginTeam): TournamentTeam? {
        val state = team.getTournamentState()
        if (state.tournament != this)
            return null
        return state.tournamentTeam
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
                val data = getPlayer(PluginPlayer.get(event.entity)) ?: return
                if (phase == TournamentPhase.STARTED && data.lives > 0) {
                    data.lives--
                    updateSidebars()
                    if (data.lives == 0) playerRunOutOfLivesEvent.invoke(data)
                }
            }

            @EventHandler
            fun onRespawn(event: PlayerRespawnEvent) {
                val pluginPlayer = PluginPlayer.get(event.player)
                if (isPlayerBoundToAnotherTournament(pluginPlayer)) return

                if (phase == TournamentPhase.PREPARED) {
                    event.respawnLocation = tournamentPreSpawn
                } else if (phase == TournamentPhase.STARTED) {
                    if (getPlayer(pluginPlayer) == null) return
                    event.respawnLocation = tournamentSpawn
                }
            }
        }.also { Bukkit.getPluginManager().registerEvents(it, Innotournament.instance) }
    }

    private fun tick() {
        if (phase != TournamentPhase.CREATED && phase != TournamentPhase.ENDED) {
            Bukkit.getOnlinePlayers().forEach(::checkPlayer)
            checkAllTeams()
        }

        if (phase != TournamentPhase.STARTED) return
        val aliveTeams = teams.filter { team ->
            team.players.any { (getPlayer(it)?.lives ?: 0) > 0 }
        }
        if (aliveTeams.size > 1) return

        aliveTeams.singleOrNull()?.let { it.score += lastTeamScore }
        broadcastFinalStandings()
        onEndEvent.invoke(Unit)
        phase = TournamentPhase.ENDED
        removeEvents(mainEventInstances)
        advancementHandler.unregister()
        unregisterTeamEventListeners()
        clearParticipationState()
    }

    private fun broadcastFinalStandings() {
        Bukkit.broadcast(finalStandingsMessage())
    }

    private fun finalStandingsMessage(): Component {
        val standings = currentStandings()
        if (standings.isEmpty()) return Component.text("Tournament ended")

        val message = Component.text()
            .append(Component.text("Tournament ended. Results: "))
        standings.forEachIndexed { index, team ->
            if (index > 0) message.append(Component.text(", "))
            message.append(Component.text("${index + 1}. "))
                .append(team.displayName)
                .append(Component.text(" (${team.score})"))
        }
        return message.build()
    }

    private fun registerTeamEventListeners() {
        if (teamJoinListener != null || teamLeaveListener != null) return
        teamJoinListener = ::onPlayerJoinTeam
        teamLeaveListener = ::onPlayerLeaveTeam
        teamRemoveListener = ::onTeamRemoved
        teamManager.events.afterJoin.subscribe(teamJoinListener!!)
        teamManager.events.afterLeave.subscribe(teamLeaveListener!!)
        teamManager.events.afterRemove.subscribe(teamRemoveListener!!)
    }

    private fun unregisterTeamEventListeners() {
        teamJoinListener?.let { teamManager.events.afterJoin.unsubscribe(it) }
        teamLeaveListener?.let { teamManager.events.afterLeave.unsubscribe(it) }
        teamRemoveListener?.let { teamManager.events.afterRemove.unsubscribe(it) }
        teamJoinListener = null
        teamLeaveListener = null
        teamRemoveListener = null
    }

    private fun clearPlayer(player: PluginPlayer) {
        players.remove(player.getTournamentState().tournamentPlayer)
        clearPlayerState(player)
    }

    private fun clearParticipationState() {
        players.forEach { clearPlayerState(it.pluginPlayer) }
        teams.forEach { clearTeamState(it.pluginTeam) }
        players.clear()
        teams.clear()
    }

    private fun setWorldBorderAndSpawn(location: Location) {
        val world = location.world ?: return
        world.spawnLocation = centerBlock(location)
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

        fun get(): Tournament? {
            return tournament
        }

        fun createPlayerState(): PlayerTournamentState = PlayerTournamentStateImpl()

        fun createTeamState(): TeamTournamentState = TeamTournamentStateImpl()
    }
}
