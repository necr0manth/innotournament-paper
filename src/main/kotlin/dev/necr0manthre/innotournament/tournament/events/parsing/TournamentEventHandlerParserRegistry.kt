package dev.necr0manthre.innotournament.tournament.events.parsing

import dev.necr0manthre.innotournament.tournament.events.ITournamentEventListener
import dev.necr0manthre.innotournament.tournament.events.ParserRegistry

object TournamentEventHandlerParserRegistry : ParserRegistry<ITournamentEventListener<*>>()
