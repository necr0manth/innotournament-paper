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
    tournament: Tournament
) : AbstractTournamentEvent<V>(tournament) {
    private var subscription: Listener? = null
    override fun unsubscribe() {
        synchronized(this) {
            subscription?.let { getHandlerListMethod().unregister(it) }
            subscription = null
        }
    }

    abstract fun handle(event: T, callback: (V) -> Unit)

    override fun subscribe() {
        synchronized(this) {
            subscription = subscription.let {
                if (it != null) error("already subscribed")
                val listener = object : Listener {}
                Bukkit.getPluginManager()
                    .registerEvent(
                        eventClass.java,
                        listener,
                        EventPriority.LOW,
                        { _, event -> handle(event as T, commonCallback) },
                        Innotournament.instance
                    )
                return@let listener
            }
        }
    }
}
