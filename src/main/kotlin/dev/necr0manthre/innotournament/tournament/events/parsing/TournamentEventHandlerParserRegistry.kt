package dev.necr0manthre.innotournament.tournament.events.parsing

import dev.necr0manthre.innotournament.tournament.events.ITournamentEventListener
import dev.necr0manthre.innotournament.tournament.events.ParserRegistry
import dev.necr0manthre.innotournament.tournament.events.parsing.TournamentEventHandlerParserRegistry

object TournamentEventHandlerParserRegistry : ParserRegistry<ITournamentEventListener<*>?>() {
    val INSTANCE: TournamentEventHandlerParserRegistry = TournamentEventHandlerParserRegistry()
}
