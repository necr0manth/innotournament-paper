package dev.necr0manthre.innotournament.tournament.events

import dev.necr0manthre.innotournament.tournament.Tournament
import java.util.function.Consumer

abstract class TournamentEvent<T, V> : AbstractTournamentEvent<T?>() {
    private var tournament: Tournament? = null
    private var callback: Consumer<T?>? = null
    private var huy: V? = null

    override fun subscribe(tournament: Tournament?, callback: Consumer<T?>?) {
        this.tournament = tournament
        this.callback = callback
        huy = subscribeInternal(tournament, callback)
    }

    override fun remove() {
        if (tournament != null) removeInternal(tournament, callback, huy)
    }

    protected abstract fun subscribeInternal(tournament: Tournament?, callback: Consumer<T?>?): V?

    protected abstract fun removeInternal(tournament: Tournament?, callback: Consumer<T?>?, obj: V?)
}
