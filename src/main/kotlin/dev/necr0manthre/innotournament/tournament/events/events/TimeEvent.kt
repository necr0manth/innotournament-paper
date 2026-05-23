package dev.necr0manthre.innotournament.tournament.events.events

import com.destroystokyo.paper.event.server.ServerTickEndEvent
import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.events.BukkitEventBasedTournamentEvent

class TimeEvent(private val time: Long, tournament: Tournament) :
    BukkitEventBasedTournamentEvent<ServerTickEndEvent, Unit>(
        ServerTickEndEvent::class,
        ServerTickEndEvent::getHandlerList,
        tournament
    ) {
    private var completed = false

    override fun handle(
        event: ServerTickEndEvent,
        callback: (Unit) -> Unit
    ) {
        if (tournament.elapsedTime >= time && !completed) {
            completed = true
            callback(Unit)
        }
    }
}
