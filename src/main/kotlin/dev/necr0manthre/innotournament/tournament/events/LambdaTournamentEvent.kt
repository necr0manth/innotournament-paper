package dev.necr0manthre.innotournament.tournament.events

import dev.necr0manthre.innotournament.tournament.Tournament
import org.apache.logging.log4j.util.TriConsumer
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

class LambdaTournamentEvent<T, V>(
    private val subscribeAction: TriConsumer<Tournament?, Consumer<T?>?, AtomicReference<V?>?>,
    private val removeAction: TriConsumer<Tournament?, Consumer<T?>?, V?>
) : AbstractTournamentEvent<T?>() {
    private val reference = AtomicReference<V?>()
    private var tournament: Tournament? = null
    private var callback: Consumer<T?>? = null
    private var subscribed = false

    override fun subscribe(tournament: Tournament?, callback: Consumer<T?>?) {
        this.tournament = tournament
        this.callback = callback
        subscribeAction.accept(tournament, callback, reference)
        subscribed = true
    }

    override fun remove() {
        if (subscribed) removeAction.accept(tournament, callback, reference.get())
        subscribed = false
    }
}
