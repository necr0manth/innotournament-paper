package dev.necr0manthre.innotournament.tournament.events.events

import dev.architectury.event.events.common.TickEvent

class PlayerTickEvent(period: kotlin.Int) :
    ArchitecturyEventBasedTournamentEvent<TickEvent.Player?, PlayerHolder?>(TickEvent.PLAYER_POST) {
    private val counter: kotlin.collections.MutableMap<java.util.UUID?, kotlin.Int?> =
        HashMap<java.util.UUID?, kotlin.Int?>()
    private var period = 0

    init {
        this.period = period
    }

    override fun getListener(callback: java.util.function.Consumer<PlayerHolder?>): TickEvent.Player? {
        return TickEvent.Player { player ->
            if (player is ServerPlayer && TeamManager.Companion.getPlayersTeam(PlayerManager.Companion.getEntity(player))
                    .isPresent() && counter.computeIfAbsent(player.getUUID()) { u: java.util.UUID? -> 0 } == 0
            ) {
                callback.accept(PlayerHolder(PlayerManager.Companion.getEntity(player)))
            }
            counter.put(player.getUUID(), (counter.getOrDefault(player.getUUID(), 0)!! + 1) % period)
        }
    }
}
