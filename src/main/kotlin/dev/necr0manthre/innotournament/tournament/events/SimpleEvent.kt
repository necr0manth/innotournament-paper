package dev.necr0manthre.innotournament.tournament.events

/** Insertion-ordered synchronous callbacks; snapshot dispatch, fail-fast on exceptions. */
class SimpleEvent<T> {
    private val lock = Any()
    private val listeners = LinkedHashSet<(T) -> Unit>()
    @Volatile private var closed = false

    fun subscribe(listener: (T) -> Unit) {
        synchronized(lock) {
            check(!closed) { "Event is already closed" }
            listeners.add(listener)
        }
    }

    fun unsubscribe(listener: (T) -> Unit) {
        synchronized(lock) {
            listeners.remove(listener)
        }
    }

    fun invoke(value: T) {
        for (listener in snapshot()) {
            if (closed) return
            listener(value)
        }
    }

    /** Source ownership ends together with its returned instance; future events are inert. */
    fun close() {
        synchronized(lock) {
            closed = true
            listeners.clear()
        }
    }

    private fun snapshot(): List<(T) -> Unit> = synchronized(lock) {
        if (closed) emptyList() else listeners.toList()
    }
}
