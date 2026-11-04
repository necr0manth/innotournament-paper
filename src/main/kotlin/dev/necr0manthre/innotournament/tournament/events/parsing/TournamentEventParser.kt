package dev.necr0manthre.innotournament.tournament.events.parsing

import dev.necr0manthre.innotournament.tournament.events.TournamentEvent

object TournamentEventParser {
    fun parse(expression: String): TournamentEvent<*>? = TournamentEventParserRegistry.parse(expression)
}
