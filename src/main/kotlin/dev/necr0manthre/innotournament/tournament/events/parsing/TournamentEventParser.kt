package dev.necr0manthre.innotournament.tournament.events.parsing

import dev.necr0manthre.innotournament.tournament.events.AbstractTournamentEvent

object TournamentEventParser {
    fun parse(data: Any): AbstractTournamentEvent<*>? {
        return IParser.Companion.parse<AbstractTournamentEvent<*>?, Any?>(TournamentEventParserRegistry.INSTANCE, data)
    }
}
