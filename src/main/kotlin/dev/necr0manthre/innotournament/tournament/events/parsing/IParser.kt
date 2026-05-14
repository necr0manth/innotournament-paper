package dev.necr0manthre.innotournament.tournament.events.parsing

import dev.necr0manthre.innotournament.tournament.events.ParserRegistry
import java.util.*

interface IParser<T, E> {
    fun tryParse(t: T?): Optional<E?>

    companion object {
        fun <V, I> parse(registry: ParserRegistry<V?>, input: I?): V? {
            for (parser in registry.getParsers<I?>(input!!.javaClass as Class<I?>)) {
                val parsed: Optional<out V?> = parser.tryParse(input)
                if (parsed.isPresent()) return parsed.get()
            }
            return null
        }
    }
}
