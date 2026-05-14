package dev.necr0manthre.innotournament.tournament.events

import dev.necr0manthre.innotournament.tournament.events.event_data.TeamAdvancement

interface ITeamAdvancementEventHandler {
    fun handle(event: TeamAdvancement?)
}
