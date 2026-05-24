package dev.necr0manthre.innotournament.tournament

import dev.necr0manthre.innotournament.Innotournament
import dev.necr0manthre.innotournament.tournament.events.AbstractTournamentEvent
import dev.necr0manthre.innotournament.tournament.events.ITournamentEventFactory
import dev.necr0manthre.innotournament.tournament.events.ITournamentEventListener
import dev.necr0manthre.innotournament.tournament.events.SimpleEvent
import net.kyori.adventure.text.Component
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.craftbukkit.CraftServer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerRespawnEvent
import java.lang.ref.WeakReference
import kotlin.math.min

class Tournament(
    val tournamentSpawn: BlockPos,
    val tournamentSpawnDimension: ResourceKey<Level>,
    var tournamentPreSpawn: BlockPos,
    var tournamentPreSpawnDimension: ResourceKey<Level>,
    var startBoxStructureResourceLocation: Identifier?,
    var startBoxPos: BlockPos,
    private val preStartGameMode: GameMode,
    preStartEventFactories: Map<ITournamentEventFactory<*>, List<ITournamentEventListener<*>>>,
    mainEventFactories: Map<ITournamentEventFactory<*>, List<ITournamentEventListener<*>>>,
    var teamScoreModifiers: DoubleArray,
    val lastTeamScore: Float
) {
    val onPrepare = SimpleEvent<Any?>()
    val onStart = SimpleEvent<Any?>()
    val onEnd = SimpleEvent<Any?>()
    val onRemove = SimpleEvent<Any?>()
    val playerRunOutOfLives = SimpleEvent<TournamentPlayer>()
    val advancementHandler = TournamentAdvancementHandler(this)

    var phase: Int = 0
        private set
    var startTime: Long = 0L

    private val preStartEvents = createEvents(preStartEventFactories)
    private val mainEvents = createEvents(mainEventFactories)
    private val removedEvents = mutableSetOf<AbstractTournamentEvent<*>>()
    private var serverRef = WeakReference<MinecraftServer?>(null)
    private var listener: Listener? = null

    private val currentServer: MinecraftServer?
        get() = serverRef.get()

    fun getServer(): MinecraftServer {
        return currentServer ?: (Bukkit.getServer() as CraftServer).server
    }

    fun updateSidebars() {
        // TODO: Rebuild sidebar presentation on top of Adventure/Bukkit scoreboards.
    }

    fun remove() {
        println("$this is being removed")
        onRemove.invoke(null)
        preStartEvents.keys.forEach(::removeEvent)
        mainEvents.keys.forEach(::removeEvent)
        advancementHandler.unregister()
        listener?.let { HandlerList.unregisterAll(it) }
        listener = null
        phase = 0
        println("$this is removed")
    }

    fun placeStartBox() {
        // TODO: Port structure placement to Paper/NMS once tournament setup commands compile.
    }

    fun removeStartBox() {
        // TODO: Port structure removal to Paper/NMS once tournament setup commands compile.
    }

    fun prepare(server: MinecraftServer) {
        check(phase != 1) { "Tournament is already prepared" }
        check(phase != 2) { "Tournament is already started" }
        check(phase != 3) { "Tournament is already ended" }

        serverRef = WeakReference(server)
        registerListener()
        setWorldBorderAndSpawn(tournamentPreSpawnDimension, tournamentPreSpawn)
        phase = 1
        subscribeAll(preStartEvents)
        Bukkit.getOnlinePlayers().forEach(::checkPlayer)
        onPrepare.invoke(null)
        advancementHandler.register()
    }

    fun start() {
        check(phase != 0) { "Tournament is not prepared yet" }
        check(phase != 2) { "Tournament is already started" }
        check(phase != 3) { "Tournament is already ended" }

        subscribeAll(mainEvents)
        preStartEvents.keys.forEach(::removeEvent)
        phase = 2
        startTime = getServer().overworld().gameTime
        setWorldBorderAndSpawn(tournamentSpawnDimension, tournamentSpawn)
        TournamentTeam.getAllTeams().forEach(::setupTeam)
        onStart.invoke(null)
        Bukkit.getOnlinePlayers().forEach(::checkPlayer)
        checkAllTeams()
    }

    fun checkPlayer(player: Player) {
        val tournamentPlayer = TournamentPlayer.get(player)
        if (phase == 1 && !player.hasPermission("innotournament.admin")) {
            player.gameMode = preStartGameMode
            return
        }

        if (phase != 2) return
        val team = tournamentPlayer.team
        if (team == null) {
            tournamentPlayer.tournament = null
            player.gameMode = GameMode.SPECTATOR
            return
        }

        if (tournamentPlayer.lives == 0) {
            player.gameMode = GameMode.SPECTATOR
        }

        if (tournamentPlayer.tournament?.get() !== this) {
            setupPlayer(player)
            setupTeam(team)
        }
    }

    fun checkAllTeams() {
        TournamentTeam.getAllTeams().forEach(::checkTeam)
    }

    fun checkTeam(team: TournamentTeam) {
        if (phase != 2) return
        if (team.tournament?.get() !== this) setupTeam(team)
        if (team.owner == null || !team.players.contains(team.owner)) {
            team.owner = team.players.firstOrNull()
        }
        if (team.players.isEmpty()) {
            team.score = 0.0
        }
    }

    val elapsedTime: Long
        get() = if (phase >= 2) getServer().overworld().gameTime - startTime else 0L

    fun setupPlayer(player: Player) {
        val playerData = TournamentPlayer.get(player)
        playerData.tournament = WeakReference(this)
        playerData.lives = 3
        player.health = player.maxHealth
        player.inventory.clear()
        player.foodLevel = 20
        player.saturation = 1f
        player.gameMode = GameMode.SURVIVAL
        world(tournamentSpawnDimension)?.let { player.teleport(Location(it, tournamentSpawn.x + 0.5, tournamentSpawn.y.toDouble(), tournamentSpawn.z + 0.5)) }
    }

    fun setupTeam(team: TournamentTeam) {
        team.score = 0.0
        team.advancements.clear()
        team.tournament = WeakReference(this)
        team.acceptAll = false
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
        val sorted = TournamentTeam.getAllTeams()
            .sortedByDescending { it.score }
            .toMutableList()
        return sorted.subList(0, min(n, sorted.size))
    }

    fun onPlayerJoinTeam(event: dev.necr0manthre.innotournament.teams.events.PlayerTeamEvent) {
        if (phase != 2) return
        val team = event.team ?: return
        val previousSize = (team.players.size - 1).coerceAtLeast(0)
        val oldModifier = scoreModifier(previousSize)
        if (oldModifier != 0.0) team.score *= scoreModifier(team.players.size) / oldModifier
        checkTeam(team)
        updateSidebars()
    }

    fun onPlayerLeaveTeam(event: dev.necr0manthre.innotournament.teams.events.PlayerTeamEvent) {
        if (phase != 2) return
        val team = event.team ?: return
        if (team.players.isEmpty()) {
            team.score = 0.0
        } else {
            val oldModifier = scoreModifier(team.players.size + 1)
            if (oldModifier != 0.0) team.score *= scoreModifier(team.players.size) / oldModifier
        }
        checkTeam(team)
        updateSidebars()
    }

    private fun createEvents(
        factories: Map<ITournamentEventFactory<*>, List<ITournamentEventListener<*>>>
    ): MutableMap<AbstractTournamentEvent<*>, List<ITournamentEventListener<*>>> {
        return factories.mapKeys { it.key.createEvent(this) }.toMutableMap()
    }

    private fun subscribeAll(events: Map<AbstractTournamentEvent<*>, List<ITournamentEventListener<*>>>) {
        events.forEach { (event, listeners) ->
            event.start()
            listeners.forEach { event.addWildcardListener(it) }
        }
    }

    private fun removeEvent(event: AbstractTournamentEvent<*>) {
        if (removedEvents.add(event)) event.remove()
    }

    private fun scoreModifier(teamSize: Int): Double {
        if (teamScoreModifiers.isEmpty()) return 1.0
        return teamScoreModifiers[teamSize.coerceIn(teamScoreModifiers.indices)]
    }

    private fun registerListener() {
        if (listener != null) return
        listener = object : Listener {
            @EventHandler
            fun onDeath(event: PlayerDeathEvent) {
                val data = TournamentPlayer.get(event.entity)
                if (phase == 2 && data.lives > 0) {
                    data.lives--
                    updateSidebars()
                    if (data.lives == 0) playerRunOutOfLives.invoke(data)
                }
            }

            @EventHandler
            fun onRespawn(event: PlayerRespawnEvent) {
                if (phase == 1) {
                    location(tournamentPreSpawnDimension, tournamentPreSpawn)?.let { event.respawnLocation = it }
                } else if (phase == 2) {
                    location(tournamentSpawnDimension, tournamentSpawn)?.let { event.respawnLocation = it }
                }
            }
        }.also { Bukkit.getPluginManager().registerEvents(it, Innotournament.instance) }
    }

    private fun setWorldBorderAndSpawn(dimension: ResourceKey<Level>, pos: BlockPos) {
        val world = world(dimension) ?: return
        world.spawnLocation = Location(world, pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
        world.worldBorder.center = Location(world, pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
    }

    private fun location(dimension: ResourceKey<Level>, pos: BlockPos): Location? {
        val world = world(dimension) ?: return null
        return Location(world, pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5)
    }

    private fun world(dimension: ResourceKey<Level>): World? {
        val key = NamespacedKey.fromString(dimension.identifier().toString()) ?: return null
        return Bukkit.getWorld(key)
    }

    companion object {
        private var tournament: Tournament? = null

        fun setTournament(server: MinecraftServer?, tournament: Tournament?) {
            this.tournament?.remove()
            this.tournament = tournament
            if (server != null && tournament != null) tournament.serverRef = WeakReference(server)
        }

        fun get(server: MinecraftServer?): Tournament? {
            if (server != null && tournament != null) tournament?.serverRef = WeakReference(server)
            return tournament
        }
    }
}
