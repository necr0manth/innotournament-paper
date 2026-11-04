package dev.necr0manthre.innotournament.tournament.events

/** Metadata is supplied by a trusted, typed action registration, not by JSON. */
class ParsedListener<T>(
    val acceptedType: ValueType<T>,
    val listener: ITournamentEventListener<T>,
    allowedPhases: Set<EventDeliveryPhase> = EventDeliveryPhase.entries.toSet()
) {
    val allowedPhases: Set<EventDeliveryPhase> = java.util.Collections.unmodifiableSet(allowedPhases.toSet())
}

enum class EventPlanStage { PRE_START, MAIN }

enum class EventDeliveryPhase { PREPARED, STARTED, ENDED }
