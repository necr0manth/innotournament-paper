package dev.necr0manthre.innotournament.tournament.events

import dev.necr0manthre.innotournament.tournament.Tournament
import java.util.function.Consumer

abstract class AbstractTournamentEvent<T> {
    private val listeners: MutableList<ITournamentEventListener<T?>?> = ArrayList<ITournamentEventListener<T?>?>()

    fun addListener(listener: ITournamentEventListener<T?>?) {
        listeners.add(listener)
    }

    fun removeListener(listener: ITournamentEventListener<T?>?) {
        listeners.remove(listener)
    }

    private fun notifyListeners(tournament: Tournament?, value: T?) {
        listeners.forEach(Consumer { listener: ITournamentEventListener<T?>? -> listener!!.listen(tournament, value) })
    }

    fun subscribe(tournament: Tournament?) {
        subscribe(tournament, Consumer { event: T? -> notifyListeners(tournament, event) })
    }

    protected abstract fun subscribe(tournament: Tournament?, callback: Consumer<T?>?)

    abstract fun remove()
}
