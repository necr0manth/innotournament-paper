package dev.necr0manthre.innotournament.tournament.events.events

import dev.architectury.event.events.common.TickEvent
import java.util.function.Consumer

class TimeEvent(private val time: Long) : AbstractTournamentEvent<Any?>() {
    private var completed = false
    private var callback: Consumer<Any?>? = null
    private var tournament: Tournament? = null

    var tick: TickEvent.Server = TickEvent.Server { server: MinecraftServer? -> this.tick(server) }

    override fun subscribe(tournament: Tournament, callback: Consumer<Any?>) {
        this.tournament = tournament
        this.callback = callback
        completed = false
        TickEvent.SERVER_PRE.register(tick)
    }

    override fun remove() {
        TickEvent.SERVER_PRE.unregister(tick)
    }

    private fun tick(server: MinecraftServer?) {
        if (tournament.getElapsedTime() >= time && !completed) {
            completed = true
            callback!!.accept(null)
        }
    }
}
