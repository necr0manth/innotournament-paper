package dev.necr0manthre.innotournament.tournament.events

import dev.architectury.event.Event
import java.util.function.Consumer
import java.util.function.Function

class ArchitecturyEventBasedLambdaTournamentEvent<T, V>(
    event: Event<T?>?,
    private val listenerFactory: Function<Consumer<V?>?, T?>
) : ArchitecturyEventBasedTournamentEvent<T?, V?>(event) {
    override fun getListener(callback: Consumer<V?>?): T? {
        return listenerFactory.apply(callback)
    }
}
