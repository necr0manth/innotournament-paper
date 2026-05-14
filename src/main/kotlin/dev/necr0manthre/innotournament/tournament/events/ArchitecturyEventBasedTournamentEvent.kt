package dev.necr0manthre.innotournament.tournament.events

import dev.architectury.event.Event

abstract class ArchitecturyEventBasedTournamentEvent<T, V>(event: Event<T?>) : TournamentEvent<V?, T?>() {
    private val event: Event<T?>

    init {
        this.event = event
    }

    abstract fun getListener(callback: java.util.function.Consumer<V?>?): T?
    override fun subscribeInternal(tournament: Tournament?, callback: java.util.function.Consumer<V?>?): T? {
        val listener = getListener(callback)
        event.register(listener)
        return listener
    }

    override fun removeInternal(tournament: Tournament?, callback: java.util.function.Consumer<V?>?, obj: T?) {
        event.unregister(obj)
    }
}
