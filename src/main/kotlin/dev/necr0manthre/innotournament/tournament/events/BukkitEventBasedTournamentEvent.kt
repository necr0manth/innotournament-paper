package dev.necr0manthre.innotournament.tournament.events

import dev.necr0manthre.innotournament.Innotournament
import dev.necr0manthre.innotournament.tournament.Tournament
import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import kotlin.reflect.KClass

abstract class BukkitEventBasedTournamentEvent<T : Event, V>(
    private val eventClass: KClass<T>,
    private val getHandlerListMethod: () -> HandlerList,
    final override val valueType: ValueType<V>
) : TournamentEvent<V> {
    protected fun subscribeBukkit(
        tournament: Tournament,
        handler: (T, (V) -> Unit) -> Unit
    ): TournamentEventInstance<V> {
        val backingEvent = SimpleEvent<V>()
        var subscription: Listener? = object : Listener {}
        Bukkit.getPluginManager()
            .registerEvent(
                eventClass.java,
                subscription!!,
                EventPriority.LOW,
                { _, event -> handler(eventClass.java.cast(event), backingEvent::invoke) },
                Innotournament.instance
            )
        return TournamentEventInstance(tournament, backingEvent) {
            subscription?.let { getHandlerListMethod().unregister(it) }
            subscription = null
        }
    }
}
