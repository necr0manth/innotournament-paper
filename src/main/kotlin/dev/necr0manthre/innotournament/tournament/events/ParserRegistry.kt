package dev.necr0manthre.innotournament.tournament.events

import dev.necr0manthre.innotournament.tournament.events.parsing.IParser

/** Ordered, write-only registrations with no publicly mutable backing set. */
open class ParserRegistry<O> {
    private val parsers = LinkedHashSet<IParser<String, O>>()

    fun <E : O, P : IParser<String, E>> register(parser: P): P {
        parsers.add(parser)
        return parser
    }

    fun parse(expression: String): O? {
        for (parser in parsers.toList()) {
            parser.tryParse(expression)?.let { return it }
        }
        return null
    }
}
