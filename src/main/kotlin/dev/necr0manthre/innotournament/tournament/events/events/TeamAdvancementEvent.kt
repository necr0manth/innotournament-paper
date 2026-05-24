package dev.necr0manthre.innotournament.tournament.events.events

import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.events.AbstractTournamentEvent
import dev.necr0manthre.innotournament.tournament.events.event_data.TeamAdvancement
import org.bukkit.advancement.Advancement

class TeamAdvancementEvent(private val advancement: Advancement, tournament: Tournament) :
    AbstractTournamentEvent<TeamAdvancement>(tournament) {
    private var handler: ((TeamAdvancement) -> Unit)? = null

    override fun subscribe() {
        handler = { event: TeamAdvancement ->
            if (event.advancement == advancement) commonCallback(event)
        }.also { tournament.advancementHandler.teamAdvancementEvent.subscribe(it) }
    }

    override fun unsubscribe() {
        handler?.let { tournament.advancementHandler.teamAdvancementEvent.unsubscribe(it) }
        handler = null
    }
}
