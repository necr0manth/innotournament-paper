package dev.necr0manthre.innotournament.tournament.events.parsing

import dev.necr0manthre.innotournament.tournament.events.ITournamentEventFactory
import dev.necr0manthre.innotournament.tournament.events.ParserRegistry

object TournamentEventParserRegistry : ParserRegistry<ITournamentEventFactory<*>>()
