package dev.necr0manthre.innotournament.tournament.events.parsing

import dev.necr0manthre.innotournament.tournament.events.ITournamentEventFactory

object TournamentEventParser {
    fun parse(data: Any): ITournamentEventFactory<*>? {
        return IParser.parse(TournamentEventParserRegistry, data)
    }
}
