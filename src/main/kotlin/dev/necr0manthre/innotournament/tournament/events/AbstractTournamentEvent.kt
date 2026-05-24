package dev.necr0manthre.innotournament.tournament.events

import dev.necr0manthre.innotournament.tournament.Tournament

abstract class AbstractTournamentEvent<T>(tournament: Tournament) {
    var _tournament: Tournament? = tournament
        private set
    val tournament
        get() = _tournament ?: error("Event is already removed")
    private val listeners: MutableList<ITournamentEventListener<T>> = mutableListOf()

    protected val commonCallback: (T) -> Unit = { event -> notifyListeners(event) }
    fun addListener(listener: ITournamentEventListener<T>) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: ITournamentEventListener<T>) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun addWildcardListener(listener: ITournamentEventListener<*>) {
        addListener(listener as ITournamentEventListener<T>)
    }

    fun start() {
        subscribe()
    }

    private fun notifyListeners(value: T) {
        synchronized(this) {
            listeners.forEach { it.listen(tournament, value) }
        }
    }

    open fun remove() {
        if (_tournament == null) return
        unsubscribe()
        _tournament = null
        listeners.clear()
    }

    protected abstract fun subscribe()

    protected abstract fun unsubscribe()

}
