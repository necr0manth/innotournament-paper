package dev.necr0manthre.innotournament.tournament.events.events

import dev.architectury.event.events.common.PlayerEvent
import java.util.function.Consumer

class AchievementEvent(private val achievement: String?) : AbstractTournamentEvent<PlayerHolder?>() {
    private var callback: Consumer<PlayerHolder?>? = null

    override fun subscribe(tournament: Tournament?, callback: Consumer<PlayerHolder?>) {
        this.callback = callback
        PlayerEvent.PLAYER_ADVANCEMENT.register(handler)
    }

    override fun remove() {
        PlayerEvent.PLAYER_ADVANCEMENT.unregister(handler)
    }

    var handler: PlayerEvent.PlayerAdvancement =
        PlayerEvent.PlayerAdvancement { player: ServerPlayer, advancement: AdvancementHolder ->
            this.handler(
                player,
                advancement
            )
        }

    fun handler(player: ServerPlayer, advancement: AdvancementHolder) {
        if (advancement.id().toString() == achievement) {
            callback!!.accept(PlayerHolder(PlayerManager.Companion.getEntity(player)))
        }
    }
}
