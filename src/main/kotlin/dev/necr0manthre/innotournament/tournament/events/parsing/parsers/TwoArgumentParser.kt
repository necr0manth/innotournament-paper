package dev.necr0manthre.innotournament.tournament.events.parsing.parsers

import dev.necr0manthre.innotournament.tournament.events.parsing.IParser
import java.util.*
import java.util.function.BiFunction

class TwoArgumentParser<T>(private val name: String?, private val eventFactory: BiFunction<String?, String?, T?>) :
    IParser<String?, T?> {
    override fun tryParse(s: String): Optional<T?> {
        var s = s
        s = s.trim()
        if (s.startsWith(name + " ")) {
            val l: Array<String?> = s.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (l.size == 3) return Optional.of<T?>(eventFactory.apply(l[1], l[2])!!)
        }
        return Optional.empty<T?>()
    }
}
