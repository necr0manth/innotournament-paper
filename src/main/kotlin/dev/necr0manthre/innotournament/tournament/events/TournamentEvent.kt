package dev.necr0manthre.innotournament.tournament.events

import dev.necr0manthre.innotournament.tournament.Tournament

/** Reusable immutable definition; the returned instance owns all per-run state. */
interface TournamentEvent<T> {
    val valueType: ValueType<T>
    fun subscribe(tournament: Tournament): TournamentEventInstance<T>
}

class TournamentEventInstance<T>(
    val tournament: Tournament,
    private val backingEvent: SimpleEvent<T>,
    private val unsubscribeAction: () -> Unit
) {
    val subscriptions: EventSubscriptionView<T> = backingEvent.asSubscriptionView()
    private val lock = Any()
    private val listeners = mutableListOf<Pair<ITournamentEventListener<T>, (T) -> Unit>>()
    @Volatile private var unsubscribed = false

    fun addListener(listener: ITournamentEventListener<T>) {
        val callback: (T) -> Unit = { value ->
            if (!unsubscribed) listener.listen(tournament, value)
        }
        synchronized(lock) {
            check(!unsubscribed) { "Event instance is already unsubscribed" }
            listeners.add(listener to callback)
            subscriptions.subscribe(callback)
        }
    }

    fun removeListener(listener: ITournamentEventListener<T>) {
        synchronized(lock) {
            val index = listeners.indexOfFirst { it.first == listener }
            if (index == -1) return
            val callback = listeners.removeAt(index).second
            subscriptions.unsubscribe(callback)
        }
    }

    fun unsubscribe() {
        synchronized(lock) {
            if (unsubscribed) return
            // A failed source unsubscribe propagates; do not pretend the handle was released.
            unsubscribeAction()
            unsubscribed = true
            backingEvent.close()
            listeners.clear()
        }
    }
}
