package dev.necr0manthre.innotournament.tournament

import com.destroystokyo.paper.event.server.ServerTickEndEvent
import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.teams.PluginTeam
import dev.necr0manthre.innotournament.teams.PluginTeam.TeamManager
import dev.necr0manthre.innotournament.teams.events.TeamChange
import dev.necr0manthre.innotournament.tournament.events.*
import org.bukkit.*
import org.bukkit.advancement.Advancement
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerAdvancementDoneEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import dev.necr0manthre.innotournament.tournament.environment.TournamentEnvironment
import dev.necr0manthre.innotournament.tournament.environment.WorldPoint
import java.util.*
import java.util.Collections
import java.util.IdentityHashMap
import java.util.LinkedHashMap
import kotlin.math.min

private data class AdvancementFact(val advancement: Advancement, val criteria: Set<String>)
private enum class ParticipationPreparation { PENDING, APPLYING, READY }

class Tournament(
    private val teamManager: TeamManager,
    private val definition: LoadedTournamentDefinition,
    private val environment: TournamentEnvironment,
    private val playerEffects: TournamentPlayerEffects,
    private val presentation: TournamentPresentation,
    private val plugin: Plugin
) {
    private val settings = definition.settings
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
        private var preparation = ParticipationPreparation.PENDING

        override val tournament: Tournament get() = this@Tournament
        override val lives: Int get() = livesValue
        override val prepared: Boolean get() = preparation == ParticipationPreparation.READY

        override fun setLives(value: Int) = setParticipationLives(this, value)

        fun setLivesInternally(value: Int) { livesValue = value }
        fun applyNewParticipationReset(player: Player) {
            if (preparation != ParticipationPreparation.PENDING) return
            check(getPlayer(pluginPlayer) === this && pluginPlayer.player === player &&
                pluginPlayer.team === team.pluginTeam) { "Tournament participation changed before its reset" }
            preparation = ParticipationPreparation.APPLYING
            playerEffects.resetNewParticipation(player)
            check(getPlayer(pluginPlayer) === this && pluginPlayer.player === player &&
                pluginPlayer.team === team.pluginTeam) { "Tournament participation changed during its reset" }
            preparation = ParticipationPreparation.READY
        }
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

    private inner class TournamentAdvancementHandler {
        private val realPlayerAdvancementsEvent = SimpleEvent<RealPlayerAdvancement>()
        private val firstTeamAdvancementsEvent = SimpleEvent<FirstTeamAdvancement>()
        val realPlayerAdvancements: EventSubscriptionView<RealPlayerAdvancement> =
            realPlayerAdvancementsEvent.asSubscriptionView()
        val firstTeamAdvancements: EventSubscriptionView<FirstTeamAdvancement> =
            firstTeamAdvancementsEvent.asSubscriptionView()
        private val playerKeys = IdentityHashMap<TournamentPlayer, MutableSet<NamespacedKey>>()
        private val teamFacts = IdentityHashMap<TournamentTeam, LinkedHashMap<NamespacedKey, AdvancementFact>>()
        private var listener: Listener? = null
        private var synchronizing = false

        fun register() {
            if (listener != null) return
            val owned = object : Listener {
                @EventHandler
                fun onAdvancement(event: PlayerAdvancementDoneEvent) = handle(event)

                @EventHandler
                fun onJoin(event: org.bukkit.event.player.PlayerJoinEvent) = handleJoin(event.player)
            }
            listener = owned
            Bukkit.getPluginManager().registerEvents(owned, plugin)
        }

        fun unregister() {
            listener?.let { HandlerList.unregisterAll(it) }
            listener = null
        }

        fun advancements(team: TournamentTeam): Set<NamespacedKey> = Collections.unmodifiableSet(
            LinkedHashSet(teamFacts[team]?.keys.orEmpty())
        )

        private fun handle(event: PlayerAdvancementDoneEvent) {
            if (synchronizing || phase != TournamentPhase.STARTED) return
            val player = currentPreparedPlayer(PluginPlayer.get(event.player)) ?: return
            if (player.player !== event.player) return
            val team = currentTeam(player) ?: return
            val advancement = event.advancement
            val progress = event.player.getAdvancementProgress(advancement)
            if (!progress.isDone) return

            val fact = AdvancementFact(
                advancement,
                Collections.unmodifiableSet(LinkedHashSet(progress.awardedCriteria))
            )
            val personalKeyAdded = playerKeys.getOrPut(player, ::LinkedHashSet).add(advancement.key)
            val firstForTeam = teamFacts.getOrPut(team, ::LinkedHashMap)
                .putIfAbsent(advancement.key, fact) == null

            if (personalKeyAdded) realPlayerAdvancementsEvent.invoke(RealPlayerAdvancement(player, advancement.key))
            if (firstForTeam) firstTeamAdvancementsEvent.invoke(FirstTeamAdvancement(team, advancement.key))

            syncTeam(team)
        }

        private fun handleJoin(player: Player) {
            if (phase != TournamentPhase.STARTED) return
            val participation = currentPreparedPlayer(PluginPlayer.get(player)) ?: return
            if (participation.player !== player) return
            currentTeam(participation)?.let(::syncTeam)
        }

        private fun syncTeam(team: TournamentTeam) {
            val facts = teamFacts[team]?.values.orEmpty().toList()
            if (facts.isEmpty()) return
            for (participation in team.players) {
                if (currentPreparedPlayer(participation.pluginPlayer) !== participation ||
                    participation.team !== team || currentTeam(participation) !== team
                ) continue
                val player = participation.player ?: continue
                syncPlayer(participation, team, player, facts)
            }
        }

        private fun syncPlayer(
            participation: TournamentPlayer,
            team: TournamentTeam,
            player: Player,
            facts: List<AdvancementFact>
        ) {
            withSemanticNoticesSuppressed {
                for (fact in facts) {
                    if (currentPreparedPlayer(participation.pluginPlayer) !== participation ||
                        currentTeam(participation) !== team || participation.player !== player
                    ) return@withSemanticNoticesSuppressed
                    val progress = player.getAdvancementProgress(fact.advancement)
                    for (criterion in fact.criteria) {
                        if (currentPreparedPlayer(participation.pluginPlayer) !== participation ||
                            currentTeam(participation) !== team || participation.player !== player
                        ) return@withSemanticNoticesSuppressed
                        if (criterion in progress.remainingCriteria) progress.awardCriteria(criterion)
                    }
                }
            }
        }

        private inline fun withSemanticNoticesSuppressed(action: () -> Unit) {
            val previous = synchronizing
            synchronizing = true
            try {
                action()
            } finally {
                synchronizing = previous
            }
        }

        private fun currentPreparedPlayer(player: PluginPlayer): TournamentPlayer? {
            if (phase != TournamentPhase.STARTED) return null
            val participation = getPlayer(player) ?: return null
            if (!participation.prepared || participation.pluginPlayer.team !== participation.team.pluginTeam) return null
            return participation
        }

        private fun currentTeam(player: TournamentPlayer): TournamentTeam? {
            val team = player.team
            if (team.tournament !== this@Tournament || team.deleted ||
                team.pluginTeam.players.none { it === player.pluginPlayer } ||
                getTeam(team.pluginTeam) !== team
            ) return null
            return team
        }
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
    private val ticksEvent = SimpleEvent<TournamentTick>()
    val onPrepare: EventSubscriptionView<Unit> = onPrepareEvent.asSubscriptionView()
    val onStart: EventSubscriptionView<Unit> = onStartEvent.asSubscriptionView()
    val onEnd: EventSubscriptionView<Unit> = onEndEvent.asSubscriptionView()
    val onRemove: EventSubscriptionView<Unit> = onRemoveEvent.asSubscriptionView()
    val playerRunOutOfLives: EventSubscriptionView<TournamentPlayer> = playerRunOutOfLivesEvent.asSubscriptionView()
    val ticks: EventSubscriptionView<TournamentTick> = ticksEvent.asSubscriptionView()
    private val advancementHandler = TournamentAdvancementHandler()
    val realPlayerAdvancements: EventSubscriptionView<RealPlayerAdvancement> =
        advancementHandler.realPlayerAdvancements
    val firstTeamAdvancements: EventSubscriptionView<FirstTeamAdvancement> =
        advancementHandler.firstTeamAdvancements

    var phase: TournamentPhase = TournamentPhase.CREATED
        private set
    var result: TournamentResult? = null
        private set
    val gameWorld: World get() = environment.gameWorld
    private var stageTick = 0L
    private var hasHadTwoAliveTeams = false
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

    fun remove(reason: RemovalReason = RemovalReason.CANCELLED) {
        if (phase == TournamentPhase.REMOVED) return
        listener?.let {
            HandlerList.unregisterAll(it)
            listener = null
        }
        unregisterTeamEventListeners()
        removeEvents(preStartEventInstances)
        removeEvents(mainEventInstances)
        advancementHandler.unregister()
        environment.releaseResources()
        presentation.close()
        clearParticipationState()
        phase = TournamentPhase.REMOVED
        onRemoveEvent.invoke(Unit)
    }

    fun placeStartBox() {
        check(phase == TournamentPhase.PREPARED || phase == TournamentPhase.STARTED) {
            "Start box can only be placed in a prepared or started tournament"
        }
        environment.placeStartBox()
    }

    fun removeStartBox() {
        check(phase == TournamentPhase.PREPARED || phase == TournamentPhase.STARTED || phase == TournamentPhase.ENDED) {
            "Start box can only be removed from a prepared, started, or ended tournament"
        }
        environment.removeStartBox()
    }

    fun changeBorder(size: Double, ticks: Long) {
        check(phase == TournamentPhase.PREPARED || phase == TournamentPhase.STARTED || phase == TournamentPhase.ENDED) {
            "World borders can only change in a prepared, started, or ended tournament"
        }
        environment.changeBorder(size, ticks)
    }

    fun setPvp(enabled: Boolean) {
        check(phase == TournamentPhase.PREPARED || phase == TournamentPhase.STARTED || phase == TournamentPhase.ENDED) {
            "PvP can only change in a prepared, started, or ended tournament"
        }
        environment.setPvp(enabled)
    }

    fun prepare() {
        check(phase == TournamentPhase.CREATED) { "Tournament can only prepare once from CREATED" }
        registerListener()
        registerTeamEventListeners()
        subscribeAll(definition.prePlan, preStartEventInstances)
        environment.applyPrepare()
        phase = TournamentPhase.PREPARED
        stageTick = 0L
        Bukkit.getOnlinePlayers().forEach(::checkPlayer)
        onPrepareEvent.invoke(Unit)
        advancementHandler.register()
        presentation.activate()
    }

    fun start() {
        check(phase == TournamentPhase.PREPARED) { "Tournament can only start from PREPARED" }
        subscribeAll(definition.mainPlan, mainEventInstances)
        removeEvents(preStartEventInstances)
        environment.applyStart()
        initializeTeamsAndPlayers()
        Bukkit.getOnlinePlayers().forEach(::prepareStartedPlayer)
        phase = TournamentPhase.STARTED
        stageTick = 0L
        updateFinishEligibility()
        Bukkit.getOnlinePlayers().forEach(::checkPlayer)
        onStartEvent.invoke(Unit)
    }

    fun checkPlayer(player: Player) {
        val pluginPlayer = PluginPlayer.get(player)
        if (isPlayerBoundToAnotherTournament(pluginPlayer)) return

        if (phase == TournamentPhase.PREPARED) {
            if (player.gameMode != settings.preStartGameMode) player.gameMode = settings.preStartGameMode
            if (!environment.isInsideStartBox(WorldPoint(player.world, player.location.x, player.location.y, player.location.z))) {
                player.health = 0.0
            }
            return
        }

        if (phase != TournamentPhase.STARTED) return
        prepareStartedPlayer(player)
    }

    private fun prepareStartedPlayer(player: Player) {
        val pluginPlayer = PluginPlayer.get(player)
        if (isPlayerBoundToAnotherTournament(pluginPlayer)) return
        if (pluginPlayer.team == null) {
            clearPlayer(pluginPlayer)
            player.gameMode = GameMode.SPECTATOR
            return
        }
        val tournamentPlayer = getOrCreateTournamentPlayer(pluginPlayer) ?: return
        (tournamentPlayer as TournamentPlayerImpl).applyNewParticipationReset(player)
    }

    fun checkAllTeams() {
        if (phase != TournamentPhase.STARTED) return
        initializeTeamsAndPlayers()
        updateFinishEligibility()
    }

    fun checkTeam(team: PluginTeam) {
        checkTeam(team, resetAcceptAll = true)
        updateFinishEligibility()
    }

    private fun checkTeam(team: PluginTeam, resetAcceptAll: Boolean) {
        if (phase != TournamentPhase.STARTED) return
        val tournamentTeam = getOrCreateTournamentTeam(team) ?: return
        if (preparedTeams.add(tournamentTeam)) {
            setupTeam(tournamentTeam, resetAcceptAll)
        }
        team.players.forEach(::getOrCreateTournamentPlayer)
    }

    private fun initializeTeamsAndPlayers() {
        teamManager.getAllTeams().forEach { team ->
            val tournamentTeam = getOrCreateTournamentTeam(team) ?: return@forEach
            if (preparedTeams.add(tournamentTeam)) setupTeam(tournamentTeam, resetAcceptAll = true)
            team.players.forEach(::getOrCreateTournamentPlayer)
        }
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
        check(phase == TournamentPhase.STARTED) { "Team score requires a started tournament" }
        require(score.isFinite()) { "Score must be finite" }
        val delta = score * scoreModifier(team.pluginTeam.players.size)
        require(delta.isFinite()) { "Calculated score must be finite" }
        val updated = team.score + delta
        require(updated.isFinite()) { "Calculated score must be finite" }
        (team as TournamentTeamImpl).scoreValue = updated
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
        if (previous > 0 && value == 0) playerRunOutOfLivesEvent.invoke(player)
        updateFinishEligibility()
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

    /** Returns a frozen view of the currently participating, registered teams. */
    fun standings(): List<TournamentResult.Team> {
        result?.let { return it.teams }
        val rankedTeams = currentStandings()
            .map { it to it.score }
            .sortedWith { (first, firstScore), (second, secondScore) ->
                when {
                    firstScore > secondScore -> -1
                    firstScore < secondScore -> 1
                    else -> first.id.compareTo(second.id)
                }
            }
        var rank = 0
        var previousScore = 0.0
        var hasPreviousScore = false
        return Collections.unmodifiableList(rankedTeams.mapIndexed { index, (team, score) ->
            if (!hasPreviousScore || score != previousScore) rank = index + 1
            previousScore = score
            hasPreviousScore = true
            TournamentResult.Team(
                team.id,
                team.displayName,
                score,
                rank,
                team.pluginTeam.players.map { TournamentResult.Player(it.uuid, it.offlinePlayer.name) }
            )
        })
    }

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
        updateFinishEligibility()
        event.player.player?.let(::checkPlayer)
    }

    fun onPlayerLeaveTeam(event: TeamChange) {
        if (phase != TournamentPhase.STARTED) return
        // A completed leave expires that runtime participation. A later join gets
        // a fresh TournamentPlayer and its normal setup, even on an A -> B move.
        clearPlayer(event.player, event.team)
        updateFinishEligibility()
        // Leaving never changes the historical score of the departing team.
    }

    fun onTeamRemoved(team: PluginTeam) {
        // Preserve a finished tournament's historical links until remove().
        if (phase == TournamentPhase.ENDED) return
        // Keep the accumulated TournamentTeam in the result set, but release
        // the permanently disposed team's live back-reference.
        clearTeamState(team)
        updateFinishEligibility()
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
        if (settings.teamScoreModifiers.isEmpty()) return 1.0
        return settings.teamScoreModifiers[teamSize.coerceIn(settings.teamScoreModifiers.indices)]
    }

    private fun handlePlayerDeath(pluginPlayer: PluginPlayer) {
        val player = getPlayer(pluginPlayer) ?: return
        if (phase != TournamentPhase.STARTED || !player.prepared || player.lives <= 0) return
        player.setLives(player.lives - 1)
    }

    private fun registerListener() {
        if (listener != null) return
        val owned = object : Listener {
            @EventHandler
            fun onTick(event: ServerTickEndEvent) {
                serverTick()
            }

            @EventHandler
            fun onDeath(event: PlayerDeathEvent) {
                handlePlayerDeath(PluginPlayer.get(event.entity))
            }

            @EventHandler
            fun onRespawn(event: PlayerRespawnEvent) {
                val pluginPlayer = PluginPlayer.get(event.player)
                if (isPlayerBoundToAnotherTournament(pluginPlayer)) return

                if (phase == TournamentPhase.PREPARED) {
                    val respawn = environment.preStartRespawnLocation()
                    event.respawnLocation = respawn
                    event.player.setRespawnLocation(respawn, false)
                }
            }

            @EventHandler
            fun onPostRespawn(event: com.destroystokyo.paper.event.player.PlayerPostRespawnEvent) {
                if (phase == TournamentPhase.STARTED) {
                    event.player.addPotionEffect(PotionEffect(PotionEffectType.ABSORPTION, 200, 100))
                }
            }
        }
        listener = owned
        Bukkit.getPluginManager().registerEvents(owned, plugin)
    }

    private fun serverTick() {
        if (phase == TournamentPhase.PREPARED || phase == TournamentPhase.STARTED) {
            Bukkit.getOnlinePlayers().forEach(::checkPlayer)
            checkAllTeams()
        }

        if (phase != TournamentPhase.PREPARED && phase != TournamentPhase.STARTED) return
        dispatchNextTick()
        if (phase != TournamentPhase.STARTED) return

        val aliveTeams = aliveTeams()
        if (!hasHadTwoAliveTeams || aliveTeams.size > 1) return

        finishNormally(aliveTeams.singleOrNull())
    }

    private fun finishNormally(lastAliveTeam: TournamentTeam?) {
        check(phase == TournamentPhase.STARTED)
        val updatedLastScore = lastAliveTeam?.let {
            val updated = it.score + settings.lastTeamScore.toDouble()
            require(updated.isFinite()) { "Calculated final score must be finite" }
            updated
        }
        val frozenResult = createResult(lastAliveTeam, updatedLastScore)
        if (lastAliveTeam != null) (lastAliveTeam as TournamentTeamImpl).scoreValue = requireNotNull(updatedLastScore)
        result = frozenResult
        phase = TournamentPhase.ENDED
        presentation.finishNormally(frozenResult)
        onEndEvent.invoke(Unit)
    }

    private fun createResult(lastAliveTeam: TournamentTeam?, updatedLastScore: Double?): TournamentResult {
        val eligible = teams.filter { !it.deleted }.map { team ->
            team to if (team === lastAliveTeam) requireNotNull(updatedLastScore) else team.score
        }.sortedWith { first, second ->
            when {
                first.second > second.second -> -1
                first.second < second.second -> 1
                else -> first.first.pluginTeam.id.compareTo(second.first.pluginTeam.id)
            }
        }
        var rank = 0
        var previousScore = 0.0
        var hasPreviousScore = false
        val rows = eligible.mapIndexed { index, (team, score) ->
            if (!hasPreviousScore || score != previousScore) rank = index + 1
            previousScore = score
            hasPreviousScore = true
            TournamentResult.Team(
                team.pluginTeam.id,
                team.pluginTeam.displayName,
                score,
                rank,
                team.pluginTeam.players.map { TournamentResult.Player(it.uuid, it.offlinePlayer.name) }
            )
        }
        val winners = rows.firstOrNull()?.let { top ->
            rows.filter { it.score == top.score }.mapTo(LinkedHashSet()) { it.id }
        } ?: emptySet()
        return TournamentResult(rows, winners, lastAliveTeam?.pluginTeam?.id)
    }

    private fun dispatchNextTick() {
        stageTick++
        val frame = TournamentTick(
            phase,
            stageTick,
            if (phase == TournamentPhase.STARTED) players.filter { player ->
                player.prepared && player.player != null && getPlayer(player.pluginPlayer) === player
            } else emptyList()
        )
        ticksEvent.invoke(frame)
    }

    private fun aliveTeams(): List<TournamentTeam> = teams.filter { team ->
        teamManager.getTeamById(team.id) === team.pluginTeam && team.players.any { it.lives > 0 }
    }

    private fun updateFinishEligibility() {
        if (phase == TournamentPhase.STARTED && aliveTeams().size >= 2) hasHadTwoAliveTeams = true
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

    companion object {
        fun createPlayerState(): PlayerTournamentState = PlayerTournamentStateImpl()

        fun createTeamState(): TeamTournamentState = TeamTournamentStateImpl()
    }
}
