package dev.necr0manthre.innotournament.tournament.events.parsing.parsers

import dev.necr0manthre.innotournament.tournament.events.parsing.IParser

class SimpleDecoder<T>(private val name: String?, private val factory: () -> T) : IParser<String, T> {
    override fun tryParse(str: String): T? {
        if (str.trim() == name) return factory()
        return null
    }
}
