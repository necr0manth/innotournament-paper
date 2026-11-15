package dev.necr0manthre.innotournament.tournament.events.events

import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.RealPlayerAdvancement
import dev.necr0manthre.innotournament.tournament.events.SimpleEvent
import dev.necr0manthre.innotournament.tournament.events.TournamentEvent
import dev.necr0manthre.innotournament.tournament.events.ValueType
import dev.necr0manthre.innotournament.tournament.events.TournamentEventInstance
import dev.necr0manthre.innotournament.tournament.events.event_data.PlayerHolder
import org.bukkit.advancement.Advancement

class AdvancementEvent(private val advancement: Advancement) :
    TournamentEvent<PlayerHolder> {
    override val valueType = ValueType.of(PlayerHolder::class)

    override fun subscribe(tournament: Tournament): TournamentEventInstance<PlayerHolder> {
        val backingEvent = SimpleEvent<PlayerHolder>()
        var handler: ((RealPlayerAdvancement) -> Unit)? = { event ->
            if (event.key == advancement.key) backingEvent.invoke(PlayerHolder(event.player))
        }
        tournament.realPlayerAdvancements.subscribe(handler!!)
        return TournamentEventInstance(tournament, backingEvent) {
            handler?.let(tournament.realPlayerAdvancements::unsubscribe)
            handler = null
        }
    }
}
