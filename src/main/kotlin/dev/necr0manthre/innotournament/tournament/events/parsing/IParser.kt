package dev.necr0manthre.innotournament.tournament.events.parsing

import dev.necr0manthre.innotournament.tournament.events.ParserRegistry

interface IParser<in T, out E> {
    fun tryParse(t: T): E?

    companion object {
        fun <V, I : Any> parse(registry: ParserRegistry<V>, input: I): V? {
            for (parser in registry.getParsers(input::class)) {
                val parsed = parser.tryParse(input)
                if (parsed != null) return parsed
            }
            return null
        }
    }
}
