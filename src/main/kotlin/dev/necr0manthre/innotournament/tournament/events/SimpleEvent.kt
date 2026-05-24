package dev.necr0manthre.innotournament.tournament.events

class SimpleEvent<T> {
    private val lock = Any()
    private val listeners = LinkedHashSet<(T) -> Unit>()

    fun subscribe(listener: (T) -> Unit) {
        synchronized(lock) {
            listeners.add(listener)
        }
    }

    fun unsubscribe(listener: (T) -> Unit) {
        synchronized(lock) {
            listeners.remove(listener)
        }
    }

    fun invoke(value: T) {
        snapshot().forEach { it(value) }
    }

    private fun snapshot(): List<(T) -> Unit> {
        return synchronized(lock) { listeners.toList() }
    }
}
