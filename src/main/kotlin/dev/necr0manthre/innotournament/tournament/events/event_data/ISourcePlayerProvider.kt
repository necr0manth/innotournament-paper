package dev.necr0manthre.innotournament.tournament.events.event_data

import dev.dominion.ecs.api.Entity

interface ISourcePlayerProvider {
    val sourcePlayer: Entity?
}
