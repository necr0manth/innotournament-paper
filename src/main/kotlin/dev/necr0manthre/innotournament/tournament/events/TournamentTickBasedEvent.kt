package dev.necr0manthre.innotournament.tournament.events

import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.TournamentTick

abstract class TournamentTickBasedEvent<V>(final override val valueType: ValueType<V>) : TournamentEvent<V> {
    protected fun subscribeTicks(
        tournament: Tournament,
        handler: (TournamentTick, (V) -> Unit) -> Unit
    ): TournamentEventInstance<V> {
        val backingEvent = SimpleEvent<V>()
        val tickListener: (TournamentTick) -> Unit = { tick ->
            if (tournament.phase == tick.stage) handler(tick, backingEvent::invoke)
        }
        tournament.ticks.subscribe(tickListener)
        return TournamentEventInstance(tournament, backingEvent) {
            tournament.ticks.unsubscribe(tickListener)
        }
    }
}
