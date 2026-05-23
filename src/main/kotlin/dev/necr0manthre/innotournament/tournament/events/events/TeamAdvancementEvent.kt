package dev.necr0manthre.innotournament.tournament.events.events

import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.events.AbstractTournamentEvent
import dev.necr0manthre.innotournament.tournament.events.event_data.TeamAdvancement
import org.bukkit.advancement.Advancement

class TeamAdvancementEvent(private val advancement: Advancement, tournament: Tournament) :
    AbstractTournamentEvent<TeamAdvancement>(tournament) {

    override fun subscribe() {
        TODO("Not yet implemented")
    }

    override fun unsubscribe() {
        TODO("Not yet implemented")
    }
}
