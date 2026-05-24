package dev.necr0manthre.innotournament.tournament.events.events

import com.destroystokyo.paper.event.server.ServerTickEndEvent
import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.events.BukkitEventBasedTournamentEvent
import dev.necr0manthre.innotournament.tournament.events.ITournamentEventFactory

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

    companion object {
        fun factory(time: Long): ITournamentEventFactory<TimeEvent> = { tournament -> TimeEvent(time, tournament) }
    }
}
