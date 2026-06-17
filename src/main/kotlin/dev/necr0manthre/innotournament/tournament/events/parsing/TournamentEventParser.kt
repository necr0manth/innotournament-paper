package dev.necr0manthre.innotournament.tournament.events.parsing

import dev.necr0manthre.innotournament.tournament.events.TournamentEvent

object TournamentEventParser {
    fun parse(data: Any): TournamentEvent<*>? {
        return IParser.parse(TournamentEventParserRegistry, data)
    }
}
