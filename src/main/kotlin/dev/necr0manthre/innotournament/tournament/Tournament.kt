package dev.necr0manthre.innotournament.tournament

import dev.architectury.event.Event
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import java.lang.ref.WeakReference
import java.util.*
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier
import java.util.stream.Collectors
import kotlin.math.min

class Tournament(
    tournamentSpawn: BlockPos,
    tournamentSpawnDimension: ResourceKey<Level?>,
    tournamentPreSpawn: BlockPos,
    tournamentPreSpawnDimension: ResourceKey<Level?>,
    startBoxStructureResourceLocation: ResourceLocation,
    startBoxPos: BlockPos,
    preStartGameMode: GameType,
    preStartEvents: MutableMap<AbstractTournamentEvent<*>, MutableSet<ITournamentEventListener<*>?>?>,
    mainEvents: MutableMap<AbstractTournamentEvent<*>, MutableSet<ITournamentEventListener<*>?>?>,
    teamScoreModifiers: DoubleArray,
    lastTeamScore: Float
) : Removable {
    val onPrepare: Event<Consumer<Any?>?> = EventFactory.createLoop()
    val onStart: Event<Consumer<Any?>?> = EventFactory.createLoop()
    val onEnd: Event<Consumer<Any?>?> = EventFactory.createLoop()
    val onRemove: Event<Consumer<Any?>?> = EventFactory.createLoop()
    val playerRunOutOfLives: Event<Consumer<Entity?>?> = EventFactory.createLoop()
    private val preStartGameMode: GameType
    private val preStartEvents: MutableMap<AbstractTournamentEvent<*>, MutableSet<ITournamentEventListener<*>?>?>
    private val mainEvents: MutableMap<AbstractTournamentEvent<*>, MutableSet<ITournamentEventListener<*>?>?>
    val tournamentSpawn: BlockPos
    val tournamentSpawnDimension: ResourceKey<Level?>
    val advancementHandler: TournamentAdvancementHandler = TournamentAdvancementHandler(this)
    var tournamentPreSpawn: BlockPos
    var tournamentPreSpawnDimension: ResourceKey<Level?>
    var startBoxStructureResourceLocation: ResourceLocation
    var startBoxPos: BlockPos
    var startTime: Long = 0
    val lastTeamScore: Float
    var teamScoreModifiers: DoubleArray

    @Getter
    var phase: Int = 0
    private var startBoxStructure: StructureTemplate? = null
    private var serverRef: WeakReference<MinecraftServer?> = WeakReference<MinecraftServer?>(null)
    private var sidebarManager: SidebarManager? = null

    val server: MinecraftServer?
        get() = serverRef.get()

    fun updateSidebars() {
        for (player in this.server.getPlayerList().getPlayers()) sidebarManager.updateSidebar(player)
    }

    fun remove() {
        println(this.toString() + " is being removed")
        onRemove.invoker().accept(null)
        for (event in preStartEvents.keys) {
            event.remove()
        }
        for (event in mainEvents.keys) {
            event.remove()
        }
        sidebarManager.remove()
        PlayerEvent.PLAYER_RESPAWN.unregister(handlePreStartRespawn)
        PlayerEvent.PLAYER_RESPAWN.unregister(handleRespawn)
        TickEvent.SERVER_POST.unregister(tick)
        TickEvent.SERVER_POST.unregister(prepareTick)
        EntityEvent.LIVING_DEATH.unregister(livingDeath)
        advancementHandler.unregister()
        TeamManager.Companion.PLAYER_JOIN_TEAM_EVENT.unregister(onPlayerJoinTeam)
        println(this.toString() + " is removed")
    }


    fun placeStartBox() {
        startBoxStructure = this.server.getStructureManager().getOrCreate(startBoxStructureResourceLocation)
        startBoxStructure.placeInWorld(
            this.server.getLevel(tournamentPreSpawnDimension),
            startBoxPos,
            startBoxPos,
            StructurePlaceSettings(),
            LegacyRandomSource(0),
            2 or 816
        )
    }

    fun removeStartBox() {
        val level: ServerLevel? = this.server.getLevel(tournamentPreSpawnDimension)
        for (pos in BlockPos.betweenClosed(startBoxPos, startBoxPos.offset(startBoxStructure.getSize()))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 18)
        }
    }

    private fun handlePreStartRespawn(player: ServerPlayer, huy: Boolean, removalReason: RemovalReason?) {
        player.setRespawnPosition(
            RespawnConfig(tournamentPreSpawnDimension, tournamentPreSpawn, 0.toBoolean(), true),
            false
        )
        player.teleport(
            TeleportTransition(
                this.server.getLevel(tournamentPreSpawnDimension),
                tournamentPreSpawn.getCenter(),
                Vec3.ZERO,
                0f,
                0f,
                PostTeleportTransition { entity: Entity? -> })
        )
    }

    private fun handleRespawn(player: ServerPlayer, huy: Boolean, removalReason: RemovalReason?) {
//        player.setRespawnPosition(new ServerPlayer.RespawnConfig(tournamentSpawnDimension, tournamentSpawn, 0, true), false);
//        player.teleport(new TeleportTransition(getServer().getLevel(tournamentSpawnDimension), tournamentSpawn.getCenter(), Vec3.ZERO, 0, 0, entity -> {
//        }));
        player.addEffect(MobEffectInstance(MobEffects.ABSORPTION, 200, 100))
    }

    private val lastWarningTicks = HashMap<UUID?, Int>()

    fun checkPlayer(player: ServerPlayer) {
        if (phase == 1) {
            if (!player.hasPermissions(4)) {
                if (player.gameMode.getGameModeForPlayer() != preStartGameMode) player.setGameMode(preStartGameMode)
                if (startBoxStructure != null) {
                    if (!AABB.of(startBoxStructure.getBoundingBox(StructurePlaceSettings(), startBoxPos))
                            .contains(player.position())
                    ) {
                        player.kill(player.serverLevel())
                    }
                }
            }
        } else if (phase == 2) {
            val ecsPlayer: Entity? = PlayerManager.Companion.getEntity(player)
            val playerData: TournamentPlayer = TournamentPlayer.Companion.getTournamentData(ecsPlayer)
            if (TeamManager.Companion.getPlayersTeam(ecsPlayer).isEmpty()) {
                playerData.tournament = null
                player.setGameMode(GameType.SPECTATOR)
                val lastWarning = lastWarningTicks.computeIfAbsent(player.getUUID()) { u: UUID? -> 0 }
                if (this.server.getTickCount() - lastWarning > 1000) {
                    lastWarningTicks.put(player.getUUID(), this.server.getTickCount())
                    player.sendSystemMessage(
                        Component.literal("You don't have team. But you must have team to participate. You can create team with only you through ")
                            .append(
                                Component.literal("/teams create <YourAwesomeTeamName>")
                                    .withStyle(ChatFormatting.AQUA)
                                    .withStyle(UnaryOperator { style: Style? ->
                                        style!!
                                            .withHoverEvent(HoverEvent.ShowText(Component.literal("<Yea, it's clickable>")))
                                            .withClickEvent(SuggestCommand("/teams create "))
                                    })
                            )
                            .append(Component.literal(" . But playing alone sucks, for more info see"))
                            .append(
                                Component.literal("/tournament help")
                                    .withStyle(ChatFormatting.AQUA)
                                    .withStyle(UnaryOperator { style: Style? ->
                                        style!!
                                            .withHoverEvent(HoverEvent.ShowText(Component.literal("<Yea, it's clickable>")))
                                            .withClickEvent(RunCommand("/tournament help"))
                                    })
                            )
                            .append(" .(It's really important)")
                    )
                }
                return
            }
            if (playerData.lives == 0) {
                player.addEffect(MobEffectInstance(MobEffects.WEAKNESS, 20, 5))
                player.addEffect(MobEffectInstance(MobEffects.MINING_FATIGUE, 20, 0))
            }
            if (playerData.tournament == null || playerData.tournament.get() !== this) {
                setupPlayer(player)
            }
        }
    }

    fun checkAllPlayers() {
        for (player in ArrayList<ServerPlayer?>(this.server.getPlayerList().getPlayers())) {
            checkPlayer(player)
        }
    }

    fun teamManager(): TeamManager? {
        return TeamManager.Companion.get(this.server)
    }

    var prepareTick: TickEvent.Server = TickEvent.Server { server: MinecraftServer? -> this.prepareTick(server) }
    var handlePreStartRespawn: PlayerEvent.PlayerRespawn =
        PlayerEvent.PlayerRespawn { player: ServerPlayer, huy: Boolean, removalReason: RemovalReason? ->
            this.handlePreStartRespawn(
                player,
                huy,
                removalReason
            )
        }
    var livingDeath: EntityEvent.LivingDeath =
        EntityEvent.LivingDeath { entity: LivingEntity?, source: DamageSource? -> this.livingDeath(entity, source) }
    var handleRespawn: PlayerEvent.PlayerRespawn =
        PlayerEvent.PlayerRespawn { player: ServerPlayer, huy: Boolean, removalReason: RemovalReason? ->
            this.handleRespawn(
                player,
                huy,
                removalReason
            )
        }
    var tick: TickEvent.Server = TickEvent.Server { server: MinecraftServer? -> this.tick(server) }
    var onPlayerJoinTeam: Consumer<PlayerTeamEvent?> =
        Consumer { event: PlayerTeamEvent? -> this.onPlayerJoinTeam(event) }

    init {
        this.tournamentSpawn = tournamentSpawn
        this.tournamentSpawnDimension = tournamentSpawnDimension
        this.tournamentPreSpawn = tournamentPreSpawn
        this.tournamentPreSpawnDimension = tournamentPreSpawnDimension
        this.startBoxStructureResourceLocation = startBoxStructureResourceLocation
        this.startBoxPos = startBoxPos
        this.preStartGameMode = preStartGameMode
        this.preStartEvents = preStartEvents
        this.mainEvents = mainEvents
        this.teamScoreModifiers = teamScoreModifiers
        this.lastTeamScore = lastTeamScore
    }

    fun prepare(server: MinecraftServer) {
        check(phase != 1) { "Tournament is already prepared" }
        check(phase != 2) { "Tournament is already started" }
        check(phase != 3) { "Tournament is already ended" }
        check(phase <= 3) { "WTF with tournament phase" }
        serverRef = WeakReference<MinecraftServer?>(server)
        TickEvent.SERVER_POST.register(prepareTick)
        this.sidebarManager = SidebarManager(server, Function { player: ServerPlayer? ->
            SuppliedSidebarData(Sidebar.Priority.OVERRIDE, 400, Supplier {
                val title: MutableComponent = Component.literal("Innotournament v2!!!")
                val playerTeam: PlayerTeam? = player.getTeam()
                val teamDisplayName: Component? = if (playerTeam == null) null else playerTeam.getDisplayName()
                val lines: MutableList<ImmutableSidebarLine?> = ArrayList<ImmutableSidebarLine?>()
                var i = 0
                val team: Entity? = if (playerTeam == null) null else teamManager().getEntity(playerTeam)
                lines.add(
                    ImmutableSidebarLine(
                        i--,
                        if (team == null) Component.literal("You have no team") else Component.literal("Your team: ")
                            .append(teamDisplayName!!),
                        BlankFormat.INSTANCE
                    )
                )
                if (team != null) {
                    lines.add(
                        ImmutableSidebarLine(
                            i--,
                            Component.literal("Score: " + TournamentUtils.formatTeamScore(team)),
                            BlankFormat.INSTANCE
                        )
                    )
                    for (tournamentPlayer in TeamManager.Companion.getPlayers(team)) {
                        lines.add(
                            ImmutableSidebarLine(
                                i--, Component.empty().append(
                                    PlayerManager.Companion.getDisplayName(tournamentPlayer).orElse(
                                        Component.literal("Unknown")
                                    )
                                ).append(
                                    "  [%d]".formatted(
                                        TournamentPlayer.Companion.getTournamentData(tournamentPlayer).lives
                                    )
                                ), BlankFormat.INSTANCE
                            )
                        )
                    }
                }

                lines.add(ImmutableSidebarLine(i--, Component.literal(""), BlankFormat.INSTANCE))
                lines.add(ImmutableSidebarLine(i--, Component.literal("Top teams: "), BlankFormat.INSTANCE))
                val top5: MutableList<Entity> = getTopTeams(5)
                val top: MutableList<Entity> = getTopTeams(teamManager().getAllTeams().size)
                val index = top.indexOf(team)
                for (j in 0..<min(5, top5.size)) {
                    if (top5.get(j) === team) lines.add(
                        ImmutableSidebarLine(
                            i--,
                            Component.literal("[" + (index + 1) + "] ").append(playerTeam.getDisplayName())
                                .append(" (You) ").append("(" + TournamentUtils.formatTeamScore(team) + ")"),
                            BlankFormat.INSTANCE
                        )
                    )
                    else lines.add(
                        ImmutableSidebarLine(
                            i--,
                            Component.literal("[" + (j + 1) + "] ")
                                .append(TeamManager.Companion.getPlayerTeam(top5.get(j)).getDisplayName())
                                .append(" (" + TournamentUtils.formatTeamScore(top5.get(j)) + ")"),
                            BlankFormat.INSTANCE
                        )
                    )
                }
                if (top5.size >= 5) {
                    if (index >= 5) {
                        if (index != 5) lines.add(
                            ImmutableSidebarLine(
                                i--,
                                Component.literal("..."),
                                BlankFormat.INSTANCE
                            )
                        )
                        lines.add(
                            ImmutableSidebarLine(
                                i--,
                                Component.literal("[" + (index + 1) + "] ")
                                    .append(TeamManager.Companion.getPlayerTeam(team).getDisplayName())
                                    .append(" (You) ").append("(" + TournamentUtils.formatTeamScore(team) + ")"),
                                BlankFormat.INSTANCE
                            )
                        )
                    }
                    if (index >= 4 && top.size > index + 1) {
                        lines.add(
                            ImmutableSidebarLine(
                                i--,
                                Component.literal("[" + (index + 2) + "] ")
                                    .append(TeamManager.Companion.getPlayerTeam(top.get(index + 1)).getDisplayName())
                                    .append(" (" + TournamentUtils.formatTeamScore(team) + ")"),
                                BlankFormat.INSTANCE
                            )
                        )
                    }
                }
                SidebarData(title, lines)
            })
        })
        server.getLevel(tournamentPreSpawnDimension).setDefaultSpawnPos(tournamentPreSpawn, 0)
        server.getLevel(tournamentPreSpawnDimension).getWorldBorder()
            .setCenter(tournamentPreSpawn.getX().toDouble(), tournamentPreSpawn.getZ().toDouble())
        if (tournamentPreSpawnDimension === Level.OVERWORLD) this.server.getLevel(Level.NETHER).getWorldBorder()
            .setCenter((tournamentPreSpawn.getX() / 8).toDouble(), (tournamentPreSpawn.getZ() / 8).toDouble())

        server.getLevel(Level.END).getWorldBorder().setSize(1000000000.0)
        server.getLevel(Level.END).getWorldBorder().setCenter(0.0, 0.0)
        if (Platform.getEnvironment() === Env.SERVER) {
            val level: ServerLevel = this.server.overworld()
            val forceLoadRadius = 500
            val forceLoadChunksRadius: Int = Mth.ceil(forceLoadRadius.toFloat() / 16)
            val spawnChunk: ChunkPos = ChunkPos(tournamentSpawn)
            for (i in -forceLoadChunksRadius..forceLoadChunksRadius) for (j in -forceLoadChunksRadius..forceLoadChunksRadius) level.setChunkForced(
                spawnChunk.x + i,
                spawnChunk.z + j,
                true
            )
        }
        PlayerEvent.PLAYER_RESPAWN.register(handlePreStartRespawn)
        //		PlayerEvent.PLAYER_JOIN.register(checkPlayer);
        EntityEvent.LIVING_DEATH.register(livingDeath)
        phase = 1
        for (event in preStartEvents.keys) {
            event.subscribe(this)
            for (listener in preStartEvents.get(event)!!) event.addListener(listener as ITournamentEventListener<*>?)
        }
        checkAllPlayers()
        onPrepare.invoker().accept(null)
        advancementHandler.register()
    }

    fun start() {
        check(phase != 0) { "Tournament is not prepared yet" }
        check(phase != 2) { "Tournament is already started" }
        check(phase != 3) { "Tournament is already ended" }
        check(phase <= 3) { "WTF with tournament phase" }
        for (event in mainEvents.keys) {
            event.subscribe(this)
            for (listener in mainEvents.get(event)!!) event.addListener(listener as ITournamentEventListener<*>?)
        }
        for (event in preStartEvents.keys) {
            event.remove()
        }
        phase = 2
        PlayerEvent.PLAYER_RESPAWN.unregister(handlePreStartRespawn)
        PlayerEvent.PLAYER_RESPAWN.register(handleRespawn)
        TeamManager.Companion.PLAYER_JOIN_TEAM_EVENT.register(onPlayerJoinTeam)
        TickEvent.SERVER_POST.register(tick)
        startTime = this.server.overworld().getGameTime()
        if (tournamentSpawnDimension == Level.OVERWORLD) {
            this.server.overworld().getWorldBorder()
                .setCenter(tournamentSpawn.getX().toDouble(), tournamentSpawn.getZ().toDouble())
            this.server.getLevel(Level.NETHER).getWorldBorder()
                .setCenter((tournamentSpawn.getX() / 8).toDouble(), (tournamentSpawn.getZ() / 8).toDouble())
            this.server.getLevel(Level.END).getWorldBorder().setCenter(0.0, 0.0)
        } else if (tournamentSpawnDimension == Level.NETHER) {
            this.server.getLevel(Level.NETHER).getWorldBorder()
                .setCenter(tournamentSpawn.getX().toDouble(), tournamentSpawn.getZ().toDouble())
            this.server.overworld().getWorldBorder()
                .setCenter((tournamentSpawn.getX() / 8).toDouble(), (tournamentSpawn.getZ() / 8).toDouble())
            this.server.getLevel(Level.END).getWorldBorder().setCenter(0.0, 0.0)
        } else {
            this.server.getLevel(Level.END).getWorldBorder()
                .setCenter(tournamentSpawn.getX().toDouble(), tournamentSpawn.getZ().toDouble())
            this.server.overworld().getWorldBorder()
                .setCenter(tournamentSpawn.getX().toDouble(), tournamentSpawn.getZ().toDouble())
            this.server.getLevel(Level.NETHER).getWorldBorder()
                .setCenter((tournamentSpawn.getX() / 8).toDouble(), (tournamentSpawn.getZ() / 8).toDouble())
        }
        this.server.getLevel(tournamentSpawnDimension).setDefaultSpawnPos(tournamentSpawn, 0)

        for (team in teamManager().getAllTeams()) {
            setupTeam(team)
            TeamSettings.Companion.getSettings(team).acceptAll = false
        }
        onStart.invoker().accept(null)
        checkAllPlayers()
        checkAllTeams()
        ItemGeneratorBlockEntity.Companion.clearAll()
    }

    fun checkAllTeams() {
        for (team in teamManager().getAllTeams()) checkTeam(team)
    }

    fun checkTeam(team: Entity?) {
        if (phase != 2) return
        val tournamentTeam: TournamentTeam = TournamentTeam.Companion.get(team)
        if (tournamentTeam.tournament == null || tournamentTeam.tournament.get() !== this) {
            setupTeam(team)
        }
        if (TeamOwner.Companion.getTeamOwner(team) == null || !TeamManager.Companion.getPlayers(team)
                .contains(TeamOwner.Companion.getTeamOwner(team))
        ) {
            if (TeamManager.Companion.getPlayers(team).isEmpty()) this.server.getScoreboard()
                .removePlayerTeam(TeamManager.Companion.getPlayerTeam(team))
            else TeamOwner.Companion.setTeamOwner(team, TeamManager.Companion.getPlayers(team).getFirst())
        }
    }

    private fun prepareTick(server: MinecraftServer?) {
        checkAllPlayers()
    }

    private fun tick(server: MinecraftServer?) {
        if (phase != 2) return
        var t: Entity? = null
        var end = true
        for (team in teamManager().getAllTeams()) {
            var alive = false
            for (player in TeamManager.Companion.getPlayers(team)) {
                val serverPlayer: Optional<ServerPlayer?> = PlayerManager.Companion.getServerPlayer(player)
                if (serverPlayer.isPresent() && !serverPlayer.get()
                        .hasDisconnected() && TournamentPlayer.Companion.getTournamentData(player).lives != 0
                ) {
                    alive = true
                    break
                }
            }
            if (alive) {
                if (t == null) t = team
                else end = false
            }
        }
        if (teamManager().getAllTeams().isEmpty()) end = false
        if (end) {
            if (t != null) {
                TeamScore.Companion.getScoreComponent(t).score += lastTeamScore.toDouble()
                updateSidebars()
            }
            val top: MutableList<Entity> = getTopTeams(teamManager().getAllTeams().size)
            this.server.getPlayerList().broadcastSystemMessage(
                Component.literal("Турнир завершён! Победила команда ")
                    .append(TeamManager.Companion.getPlayerTeam(top.get(0)).getDisplayName()).append(
                        " (" + TeamManager.Companion.getPlayers(top.get(0)).stream()
                            .map<Optional<String?>?> { player: Entity -> PlayerManager.Companion.getName(player) }
                            .map<String?> { opt: Optional<String?>? -> opt!!.orElse("Unknown") }.collect(
                                Collectors.joining(" ")
                            ) + " )"
                    ), false
            )
            for (i in top.indices) {
                this.server.getPlayerList().broadcastSystemMessage(
                    Component.literal("[" + (i + 1) + "] ")
                        .append(TeamManager.Companion.getPlayerTeam(top.get(i)).getDisplayName())
                        .append(" " + TournamentUtils.formatTeamScore(top.get(i))).append(
                            " (" + TeamManager.Companion.getPlayers(top.get(i)).stream()
                                .map<Optional<String?>?> { player: Entity -> PlayerManager.Companion.getName(player) }
                                .map<String?> { opt: Optional<String?>? -> opt!!.orElse("Unknown") }.collect(
                                    Collectors.joining(" ")
                                ) + " )"
                        ), false
                )
            }
            onEnd.invoker().accept(null)
            phase = 3

            for (event in mainEvents.keys) {
                ServerScheduler.scheduleServerPre(0, Runnable {
                    try {
                        event.remove()
                    } catch (ignored: Exception) {
                    }
                })
            }

            ServerScheduler.scheduleServerPre(0, Runnable {
                PlayerEvent.PLAYER_RESPAWN.unregister(handleRespawn)
                TickEvent.SERVER_POST.unregister(tick)
                EntityEvent.LIVING_DEATH.unregister(livingDeath)
                advancementHandler.unregister()
            })
            return
        }
        checkAllPlayers()
        checkAllTeams()
    }

    val elapsedTime: Long
        get() {
            if (phase >= 2) return this.server.overworld().getGameTime() - startTime
            return 0
        }

    private fun livingDeath(entity: LivingEntity?, source: DamageSource?): EventResult {
        if (entity is ServerPlayer) {
            val tournamentPlayer: Entity? = PlayerManager.Companion.getEntity(entity)
            val data: TournamentPlayer = TournamentPlayer.Companion.getTournamentData(tournamentPlayer)
            if (data.lives > 0) {
                data.lives--
                updateSidebars()
                if (data.lives == 0) playerRunOutOfLives.invoker().accept(tournamentPlayer)
            }
        }
        return EventResult.pass()
    }

    fun setupPlayer(player: ServerPlayer) {
        val playerData: TournamentPlayer =
            TournamentPlayer.Companion.getTournamentData(PlayerManager.Companion.getEntity(player))
        playerData.tournament = WeakReference<Tournament?>(this)
        playerData.lives = 3
        player.heal(100f)
        for (adv in this.server.getAdvancements().getAllAdvancements()) for (huy in player.getAdvancements()
            .getOrStartProgress(adv).getCompletedCriteria()) player.getAdvancements().revoke(adv, huy)
        player.getInventory().clearContent()
        player.getFoodData().setFoodLevel(20)
        player.getFoodData().setSaturation(1f)
        player.teleport(
            TeleportTransition(
                this.server.getLevel(tournamentSpawnDimension),
                tournamentSpawn.getCenter(),
                Vec3.ZERO,
                0f,
                0f,
                PostTeleportTransition { entity: Entity? -> })
        )
        player.setRespawnPosition(null, false)
        player.setGameMode(GameType.SURVIVAL)
        playerData.tournament = WeakReference<Tournament?>(this)
    }

    fun setupTeam(team: Entity?) {
        TeamScore.Companion.setTeamScore(team, 0.0)
        TeamAdvancements.Companion.get(team).advancements.clear()
        TournamentTeam.Companion.get(team).tournament = WeakReference<Tournament?>(this)
    }

    fun addScoresToPlayer(player: Entity?, score: Double) {
        if (TournamentPlayer.Companion.getTournamentData(player).lives == 0) return
        val team: Entity = TeamManager.Companion.getPlayersTeam(player).orElseThrow()
        TeamScore.Companion.getScoreComponent(team).score += score * teamScoreModifiers[TeamManager.Companion.getPlayers(
            team
        ).size]
        updateSidebars()
    }

    fun addScores(team: Entity?, score: Double) {
        TeamScore.Companion.getScoreComponent(team).score += score * teamScoreModifiers[TeamManager.Companion.getPlayers(
            team
        ).size]
        updateSidebars()
    }

    fun getTopTeams(n: Int): MutableList<Entity> {
        val sorted: MutableList<Entity> = teamManager().getAllTeams().stream().sorted(
            Comparator.comparing<Entity?, Double?>(
                Function { t: Entity? -> -TeamScore.Companion.getTeamScore(t) })
        ).toList()
        if (sorted.size < n) return sorted
        return sorted.subList(0, n)
    }

    fun onPlayerJoinTeam(event: PlayerTeamEvent) {
        if (phase != 2) return
        val coeff = teamScoreModifiers[TeamManager.Companion.getPlayers(event.team).size]
        TeamScore.Companion.getScoreComponent(event.team).score *= teamScoreModifiers[TeamManager.Companion.getPlayers(
            event.team
        ).size + 1] / coeff
        checkTeam(event.team)
        updateSidebars()
    }

    fun onPlayerLeaveTeam(event: PlayerTeamEvent) {
        if (phase != 2) return
        if (TeamManager.Companion.getPlayers(event.team).isEmpty()) {
            TeamScore.Companion.getScoreComponent(event.team).score = 0.0
        } else {
            val coeff = teamScoreModifiers[TeamManager.Companion.getPlayers(event.team).size + 1]
            TeamScore.Companion.getScoreComponent(event.team).score *= teamScoreModifiers[TeamManager.Companion.getPlayers(
                event.team
            ).size] / coeff
        }
        checkTeam(event.team)
        updateSidebars()
    }

    override fun onRemove() {
        remove()
    }

    companion object {
        private val tournamentManager: ServerBoundObjManager<Tournament?> = ServerBoundObjManager<Tournament?>()
        fun setTournament(server: MinecraftServer?, tournament: Tournament?) {
            if (get(server) != null) try {
                get(server)!!.remove()
            } catch (e: Exception) {
                throw RuntimeException("Exception while tournament", e)
            }
            tournamentManager.put(server, tournament)
        }

        fun get(server: MinecraftServer?): Tournament? {
            return tournamentManager.getOrDefault(server, null)
        }
    }
}
