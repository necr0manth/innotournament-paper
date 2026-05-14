package dev.necr0manthre.innotournament.tournament.events.parsing.parsers

import dev.necr0manthre.innotournament.tournament.events.parsing.IParser
import java.util.*
import java.util.function.Supplier

class SimpleDecoder<T>(private val name: String?, private val eventFactory: Supplier<T?>) : IParser<String?, T?> {
    override fun tryParse(s: String): Optional<T?> {
        if (s.trim() == name) return Optional.of<T?>(eventFactory.get()!!)
        return Optional.empty<T?>()
    }
}
