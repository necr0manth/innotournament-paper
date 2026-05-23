package dev.necr0manthre.innotournament.tournament.events.parsing

import dev.necr0manthre.innotournament.tournament.events.ITournamentEventListener

object TournamentEventHandlerParser {
    fun parse(data: Any): ITournamentEventListener<*>? {
        return IParser.parse(
            TournamentEventHandlerParserRegistry,
            data
        )
    }
}
