package dev.necr0manthre.innotournament.tournament.events.events

import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.TournamentTick
import dev.necr0manthre.innotournament.tournament.events.TournamentTickBasedEvent
import dev.necr0manthre.innotournament.tournament.events.ValueType
import dev.necr0manthre.innotournament.tournament.events.TournamentEventInstance

class TimeEvent(private val time: Long) :
    TournamentTickBasedEvent<Unit>(ValueType.of(Unit::class)) {
    override fun subscribe(tournament: Tournament): TournamentEventInstance<Unit> {
        var completed = false
        return subscribeTicks(tournament) { tick: TournamentTick, callback ->
            if (tick.stageTick >= time && !completed) {
                completed = true
                callback(Unit)
            }
        }
    }
}
