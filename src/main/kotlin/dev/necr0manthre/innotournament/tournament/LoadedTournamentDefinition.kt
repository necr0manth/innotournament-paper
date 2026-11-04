package dev.necr0manthre.innotournament.tournament

import dev.necr0manthre.innotournament.tournament.events.EventPlan

/** A complete, immutable configuration snapshot; no subscriptions or world resources are owned here. */
class LoadedTournamentDefinition(
    val settings: TournamentSettings,
    val prePlan: EventPlan,
    val mainPlan: EventPlan
)
