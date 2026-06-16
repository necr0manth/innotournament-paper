package dev.necr0manthre.innotournament.tournament.events

/**
 * Public view for events exposed by domain objects.
 *
 * Objects that expose public events should expose this type instead of
 * `SimpleEvent` so callers can subscribe and unsubscribe, but cannot invoke
 * the event from outside the owning object.
 */
class EventSubscriptionView<T>(private val event: SimpleEvent<T>) {
    fun subscribe(listener: (T) -> Unit) {
        event.subscribe(listener)
    }

    fun unsubscribe(listener: (T) -> Unit) {
        event.unsubscribe(listener)
    }
}

fun <T> SimpleEvent<T>.asSubscriptionView(): EventSubscriptionView<T> {
    return EventSubscriptionView(this)
}
