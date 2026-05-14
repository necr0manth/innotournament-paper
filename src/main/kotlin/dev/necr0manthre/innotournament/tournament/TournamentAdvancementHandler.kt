package dev.necr0manthre.innotournament.tournament

import dev.architectury.event.Event

class TournamentAdvancementHandler(tournament: Tournament?) {
    private val tournament: java.lang.ref.WeakReference<Tournament?>
    val teamAdvancementEvent: Event<ITeamAdvancementEventHandler?> = EventFactory.createLoop()

    fun register() {
        PlayerEvent.PLAYER_ADVANCEMENT.register(handle)
        PlayerEvent.PLAYER_JOIN.register(handleJoin)
    }

    var handle: PlayerEvent.PlayerAdvancement =
        PlayerEvent.PlayerAdvancement { serverPlayer: ServerPlayer, advancement: AdvancementHolder ->
            this.handle(
                serverPlayer,
                advancement
            )
        }
    var handleJoin: PlayerEvent.PlayerJoin = PlayerEvent.PlayerJoin { player: ServerPlayer -> this.handleJoin(player) }

    init {
        this.tournament = java.lang.ref.WeakReference<Tournament?>(tournament)
    }

    private fun handle(serverPlayer: ServerPlayer, advancement: AdvancementHolder) {
        val player: Entity? = PlayerManager.Companion.getEntity(serverPlayer)
        val team: java.util.Optional<Entity?> = TeamManager.Companion.getPlayersTeam(player)
        if (team.isEmpty()) return
        if (!TeamAdvancements.Companion.get(team.get()).advancements.contains(advancement)) {
            TeamAdvancements.Companion.get(team.get()).advancements.add(advancement)
            teamAdvancementEvent.invoker().handle(TeamAdvancement(team.get(), advancement))
        }
        for (player1 in TeamManager.Companion.getPlayers(team.get())) {
            PlayerManager.Companion.getServerPlayer(player1).ifPresent(java.util.function.Consumer { p: ServerPlayer? ->
                val advancements: PlayerAdvancements = p.getAdvancements()
                val progress: net.minecraft.advancements.AdvancementProgress =
                    advancements.getOrStartProgress(advancement)
                for (c in progress.getRemainingCriteria()) advancements.award(advancement, c)
            })
        }
    }

    private fun handleJoin(player: ServerPlayer) {
        val tournament = this.tournament.get()
        if (tournament == null) return
        val playerEntity: Entity? = PlayerManager.Companion.getEntity(player)
        val team: java.util.Optional<Entity?> = TeamManager.Companion.getPlayersTeam(playerEntity)
        if (team.isEmpty()) return
        for (advancement in TeamAdvancements.Companion.get(team.get()).advancements) {
            val advancements: PlayerAdvancements = player.getAdvancements()
            val progress: net.minecraft.advancements.AdvancementProgress = advancements.getOrStartProgress(advancement)
            for (c in progress.getRemainingCriteria()) advancements.award(advancement, c)
        }
    }

    fun unregister() {
        PlayerEvent.PLAYER_ADVANCEMENT.unregister(handle)
        PlayerEvent.PLAYER_JOIN.unregister(handleJoin)
    }
}
