package dev.necr0manthre.innotournament.tournament

import com.destroystokyo.paper.event.server.ServerTickEndEvent
import dev.necr0manthre.innotournament.Innotournament
import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.teams.PluginTeam
import dev.necr0manthre.innotournament.teams.PluginTeam.TeamManager
import dev.necr0manthre.innotournament.teams.events.TeamChange
import dev.necr0manthre.innotournament.tournament.events.*
import dev.necr0manthre.innotournament.tournament.events.event_data.TeamAdvancement
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
import java.util.Collections
import kotlin.math.min

class Tournament(
    private val teamManager: TeamManager,
    val tournamentSpawn: Location,
    var tournamentPreSpawn: Location,
    var startBoxStructureResourceLocation: NamespacedKey?,
    var startBoxPos: Location,
    private val preStartGameMode: GameMode,
    private val preStartEvents: EventPlan,
    private val mainEvents: EventPlan,
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
        var participation: TournamentPlayer? = null

        override val tournament: Tournament?
            get() = participation?.tournament
    }

    private class TeamTournamentStateImpl : TeamTournamentState {
        var participation: TournamentTeam? = null

        override val tournament: Tournament?
            get() = participation?.tournament
    }

    private inner class TournamentPlayerImpl(
        override val pluginPlayer: PluginPlayer,
        override val team: TournamentTeam
    ) : TournamentPlayer {
        private var livesValue = 3
        private var isPrepared = false

        override val tournament: Tournament get() = this@Tournament
        override val lives: Int get() = livesValue
        override val prepared: Boolean get() = isPrepared

        override fun setLives(value: Int) = setParticipationLives(this, value)

        fun setLivesInternally(value: Int) { livesValue = value }
        fun markPrepared() { isPrepared = true }
    }

    private inner class TournamentTeamImpl(
        override val pluginTeam: PluginTeam
    ) : TournamentTeam {
        override val tournament: Tournament get() = this@Tournament
        var scoreValue = 0.0
        override val score: Double get() = scoreValue
        override val deleted: Boolean get() = teamManager.getTeamById(pluginTeam.id) !== pluginTeam
        override val advancements: Set<NamespacedKey> get() = advancementHandler.advancements(this)
        override val players: List<TournamentPlayer>
            get() = Collections.unmodifiableList(pluginTeam.players.mapNotNull(::getPlayer))
    }

    private fun PluginPlayer.getTournamentState(): PlayerTournamentStateImpl =
        tournamentState as PlayerTournamentStateImpl

    private fun PluginTeam.getTournamentState(): TeamTournamentStateImpl =
        tournamentState as TeamTournamentStateImpl

    private fun clearPlayerState(player: PluginPlayer, expected: TournamentPlayer? = null) {
        val state = player.getTournamentState()
        val participation = state.participation
        if (participation?.tournament === this && (expected == null || participation === expected)) {
            state.participation = null
        }
    }

    private fun clearTeamState(team: PluginTeam) {
        val state = team.getTournamentState()
        if (state.participation?.tournament === this) state.participation = null
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
    private val advancementHandler = TournamentAdvancementHandler(this)
    val teamAdvancementEvent: EventSubscriptionView<TeamAdvancement> = advancementHandler.teamAdvancementEvent

    var phase: TournamentPhase = TournamentPhase.CREATED
        private set
    var startTime: Long = 0L

    private val preStartEventInstances = mutableSetOf<TournamentEventInstance<*>>()
    private val mainEventInstances = mutableSetOf<TournamentEventInstance<*>>()
    private val removedEvents = mutableSetOf<TournamentEventInstance<*>>()
    private val players = LinkedHashSet<TournamentPlayer>()
    private val teams = LinkedHashSet<TournamentTeam>()
    private val preparedTeams = LinkedHashSet<TournamentTeam>()
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
        if (!tournamentPlayer.prepared && setupPlayer(tournamentPlayer)) {
            (tournamentPlayer as TournamentPlayerImpl).markPrepared()
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
        if (preparedTeams.add(tournamentTeam)) {
            setupTeam(tournamentTeam, resetAcceptAll)
        }
        team.players.forEach(::getOrCreateTournamentPlayer)
    }

    val elapsedTime: Long
        get() = if (phase >= TournamentPhase.STARTED) (timeWorld()?.fullTime ?: startTime) - startTime else 0L

    private fun setupPlayer(tournamentPlayer: TournamentPlayer): Boolean {
        val player = tournamentPlayer.player ?: return false
        player.health = player.maxHealth
        player.inventory.clear()
        player.foodLevel = 20
        player.saturation = 1f
        player.gameMode = GameMode.SURVIVAL
        player.teleport(centerBlock(tournamentSpawn))
        return true
    }

    private fun setupTeam(team: TournamentTeam, resetAcceptAll: Boolean) {
        (team as TournamentTeamImpl).scoreValue = 0.0
        if (resetAcceptAll) team.pluginTeam.acceptAll = false
    }

    fun addScoresToPlayer(player: TournamentPlayer?, score: Double) {
        if (player == null) return
        requireCanonicalPlayer(player)
        check(phase == TournamentPhase.STARTED && player.prepared) {
            "Player score requires a prepared participation in a started tournament"
        }
        addScores(player.team, score)
    }

    fun addScores(team: TournamentTeam?, score: Double) {
        if (team == null) return
        requireCanonicalTeam(team)
        require(score.isFinite()) { "Score must be finite" }
        val delta = score * scoreModifier(team.pluginTeam.players.size)
        require(delta.isFinite()) { "Calculated score must be finite" }
        val updated = team.score + delta
        require(updated.isFinite()) { "Calculated score must be finite" }
        (team as TournamentTeamImpl).scoreValue = updated
        updateSidebars()
    }

    private fun setParticipationLives(player: TournamentPlayerImpl, value: Int) {
        require(value >= 0) { "Lives cannot be negative" }
        check(phase == TournamentPhase.STARTED) { "Lives can only change in a started tournament" }
        requireCanonicalPlayer(player)
        check(player.prepared && getTeam(player.team.pluginTeam) === player.team) {
            "Lives require a prepared player on an active team"
        }
        val previous = player.lives
        player.setLivesInternally(value)
        updateSidebars()
        if (previous > 0 && value == 0) playerRunOutOfLivesEvent.invoke(player)
    }

    private fun requireCanonicalPlayer(player: TournamentPlayer) {
        require(player.tournament === this && getPlayer(player.pluginPlayer) === player) {
            "Tournament player is stale or belongs to another tournament"
        }
    }

    private fun requireCanonicalTeam(team: TournamentTeam) {
        require(team.tournament === this && getTeam(team.pluginTeam) === team) {
            "Tournament team is stale or belongs to another tournament"
        }
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
            val ratio = scoreModifier(event.sizeAfter) / oldModifier
            val updated = team.score * ratio
            require(ratio.isFinite() && updated.isFinite()) { "Calculated score must be finite" }
            (team as TournamentTeamImpl).scoreValue = updated
        }
        event.player.player?.let(::checkPlayer)
        updateSidebars()
    }

    fun onPlayerLeaveTeam(event: TeamChange) {
        if (phase != TournamentPhase.STARTED) return
        // A completed leave expires that runtime participation. A later join gets
        // a fresh TournamentPlayer and its normal setup, even on an A -> B move.
        clearPlayer(event.player, event.team)
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

    private fun getOrCreateTournamentTeam(team: PluginTeam): TournamentTeam? {
        if (teamManager.getTeamById(team.id) !== team) return null
        val state = team.getTournamentState()
        val tournamentTeam = state.participation ?: run {
            val newTournamentTeam = TournamentTeamImpl(team)
            state.participation = newTournamentTeam
            teams.add(newTournamentTeam)
            return@run newTournamentTeam
        }
        if (tournamentTeam.tournament != this)
            return null
        return tournamentTeam
    }

    private fun getOrCreateTournamentPlayer(player: PluginPlayer): TournamentPlayer? {
        val team = player.team ?: return null
        val tournamentTeam = getOrCreateTournamentTeam(team) ?: return null
        val playerState = player.getTournamentState()
        val tournamentPlayer: TournamentPlayer = playerState.participation ?: run {
            val newTournamentPlayer = TournamentPlayerImpl(player, tournamentTeam)
            playerState.participation = newTournamentPlayer
            players.add(newTournamentPlayer)
            return@run newTournamentPlayer
        }
        if (tournamentPlayer.tournament !== this || tournamentPlayer.team !== tournamentTeam)
            return null
        return tournamentPlayer
    }

    fun getPlayer(player: PluginPlayer): TournamentPlayer? {
        val state = player.getTournamentState()
        val participation = state.participation
        if (participation?.tournament !== this || participation.pluginPlayer.team !== participation.team.pluginTeam)
            return null
        return participation
    }

    fun getTeam(team: PluginTeam): TournamentTeam? {
        val state = team.getTournamentState()
        val participation = state.participation
        if (participation?.tournament !== this || teamManager.getTeamById(team.id) !== team)
            return null
        return participation
    }

    fun players(): List<TournamentPlayer> = Collections.unmodifiableList(players.toList())

    fun teams(): List<TournamentTeam> = Collections.unmodifiableList(teams.toList())

    private fun subscribeAll(
        events: EventPlan,
        instances: MutableSet<TournamentEventInstance<*>>
    ) {
        for (entry in events.entries) {
            val instance = entry.subscribe(this)
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
                    (data as TournamentPlayerImpl).setLivesInternally(data.lives - 1)
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
            team.players.any { it.lives > 0 }
        }
        if (aliveTeams.size > 1) return

        aliveTeams.singleOrNull()?.let { team ->
            require(lastTeamScore.isFinite()) { "Final score must be finite" }
            val updated = team.score + lastTeamScore
            require(updated.isFinite()) { "Calculated score must be finite" }
            (team as TournamentTeamImpl).scoreValue = updated
        }
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
        teamManager.afterJoin.subscribe(teamJoinListener!!)
        teamManager.afterLeave.subscribe(teamLeaveListener!!)
        teamManager.afterRemove.subscribe(teamRemoveListener!!)
    }

    private fun unregisterTeamEventListeners() {
        teamJoinListener?.let { teamManager.afterJoin.unsubscribe(it) }
        teamLeaveListener?.let { teamManager.afterLeave.unsubscribe(it) }
        teamRemoveListener?.let { teamManager.afterRemove.unsubscribe(it) }
        teamJoinListener = null
        teamLeaveListener = null
        teamRemoveListener = null
    }

    private fun clearPlayer(player: PluginPlayer, expectedTeam: PluginTeam? = null) {
        val participation = player.getTournamentState().participation ?: return
        if (participation.tournament !== this ||
            (expectedTeam != null && participation.team.pluginTeam !== expectedTeam)
        ) return
        players.remove(participation)
        clearPlayerState(player, participation)
    }

    private fun clearParticipationState() {
        players.forEach { clearPlayerState(it.pluginPlayer, it) }
        teams.forEach { clearTeamState(it.pluginTeam) }
        players.clear()
        teams.clear()
        preparedTeams.clear()
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
