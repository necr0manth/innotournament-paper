package dev.necr0manthre.innotournament.tournament.events.events

import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.events.SimpleEvent
import dev.necr0manthre.innotournament.tournament.events.TournamentEvent
import dev.necr0manthre.innotournament.tournament.events.TournamentEventInstance
import dev.necr0manthre.innotournament.tournament.events.event_data.TeamAdvancement
import org.bukkit.advancement.Advancement

class TeamAdvancementEvent(private val advancement: Advancement) : TournamentEvent<TeamAdvancement> {
    override fun subscribe(tournament: Tournament): TournamentEventInstance<TeamAdvancement> {
        val backingEvent = SimpleEvent<TeamAdvancement>()
        var handler: ((TeamAdvancement) -> Unit)? = { event ->
            if (event.advancement == advancement) backingEvent.invoke(event)
        }
        tournament.advancementHandler.teamAdvancementEvent.subscribe(handler!!)
        return TournamentEventInstance(tournament, backingEvent) {
            handler?.let { tournament.advancementHandler.teamAdvancementEvent.unsubscribe(it) }
            handler = null
        }
    }
}
