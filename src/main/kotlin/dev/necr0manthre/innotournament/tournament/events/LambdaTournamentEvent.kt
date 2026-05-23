package dev.necr0manthre.innotournament.tournament.events

import dev.necr0manthre.innotournament.tournament.Tournament

class LambdaTournamentEvent<T, V>(
    private val subscribeAction: (Tournament, (T) -> Unit) -> V,
    private val unsubscribeAction: (Tournament, (T) -> Unit, V) -> Unit,
    tournament: Tournament
) : AbstractTournamentEvent<T>(tournament) {
    private var subscription: V? = null

    override fun subscribe() {
        subscription = subscribeAction(tournament, commonCallback)
    }

    override fun unsubscribe() {
        subscription?.let { unsubscribeAction(tournament, commonCallback, it) }
    }
}
