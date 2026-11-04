package dev.necr0manthre.innotournament.tournament.events

/** Payload type is supplied by a trusted, typed action registration, not by JSON. */
class ParsedListener<T>(
    val acceptedType: ValueType<T>,
    val listener: ITournamentEventListener<T>
)
