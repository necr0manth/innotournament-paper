package dev.necr0manthre.innotournament.tournament.events

import dev.necr0manthre.innotournament.tournament.Tournament

class LambdaTournamentEvent<T, V>(
    override val valueType: ValueType<T>,
    private val subscribeAction: (Tournament, (T) -> Unit) -> V,
    private val unsubscribeAction: (Tournament, (T) -> Unit, V) -> Unit
) : TournamentEvent<T> {

    override fun subscribe(tournament: Tournament): TournamentEventInstance<T> {
        val backingEvent = SimpleEvent<T>()
        val callback: (T) -> Unit = backingEvent::invoke
        var subscription: V? = subscribeAction(tournament, callback)
        return TournamentEventInstance(tournament, backingEvent) {
            subscription?.let { unsubscribeAction(tournament, callback, it) }
            subscription = null
        }
    }
}
