package dev.necr0manthre.innotournament.tournament.events.events

import com.destroystokyo.paper.event.server.ServerTickEndEvent
import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.events.BukkitEventBasedTournamentEvent
import dev.necr0manthre.innotournament.tournament.events.ValueType
import dev.necr0manthre.innotournament.tournament.events.TournamentEventInstance

class TimeEvent(private val time: Long) :
    BukkitEventBasedTournamentEvent<ServerTickEndEvent, Unit>(
        ServerTickEndEvent::class,
        ServerTickEndEvent::getHandlerList,
        ValueType.of(Unit::class)
    ) {
    override fun subscribe(tournament: Tournament): TournamentEventInstance<Unit> {
        var completed = false
        return subscribeBukkit(tournament) { _, callback ->
            if (tournament.elapsedTime >= time && !completed) {
                completed = true
                callback(Unit)
            }
        }
    }
}
