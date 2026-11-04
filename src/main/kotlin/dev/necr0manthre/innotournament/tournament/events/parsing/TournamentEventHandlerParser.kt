package dev.necr0manthre.innotournament.tournament.events.parsing

import dev.necr0manthre.innotournament.tournament.events.ParsedListener

object TournamentEventHandlerParser {
    fun parse(expression: String): ParsedListener<*>? =
        TournamentEventHandlerParserRegistry.parse(expression)
}
