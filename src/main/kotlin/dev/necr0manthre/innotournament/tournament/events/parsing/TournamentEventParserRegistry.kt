package dev.necr0manthre.innotournament.tournament.events.parsing

import dev.necr0manthre.innotournament.tournament.events.ParserRegistry
import dev.necr0manthre.innotournament.tournament.events.TournamentEvent

object TournamentEventParserRegistry : ParserRegistry<TournamentEvent<*>>()
