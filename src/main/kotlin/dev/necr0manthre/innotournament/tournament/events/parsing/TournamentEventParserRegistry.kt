package dev.necr0manthre.innotournament.tournament.events.parsing

import dev.necr0manthre.innotournament.tournament.events.AbstractTournamentEvent
import dev.necr0manthre.innotournament.tournament.events.ParserRegistry
import dev.necr0manthre.innotournament.tournament.events.parsing.TournamentEventParserRegistry

object TournamentEventParserRegistry : ParserRegistry<AbstractTournamentEvent<*>?>() {
    val INSTANCE: TournamentEventParserRegistry = TournamentEventParserRegistry()
}
