package dev.necr0manthre.innotournament.tournament.events.parsing

import dev.necr0manthre.innotournament.tournament.events.ParserRegistry
import java.util.*

interface IParser<T, E> {
    fun tryParse(t: T): E?

    companion object {
        fun <V, I : Any> parse(registry: ParserRegistry<V>, input: I): V? {
            for (parser in registry.getParsers<I>(input.javaClass as Class<I>)) {
                val parsed = parser.tryParse(input)
                if (parsed.isPresent()) return parsed.get()
            }
            return null
        }
    }
}
