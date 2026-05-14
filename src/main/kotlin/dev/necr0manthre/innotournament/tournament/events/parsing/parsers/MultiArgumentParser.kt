package dev.necr0manthre.innotournament.tournament.events.parsing.parsers

import dev.necr0manthre.innotournament.tournament.events.parsing.IParser
import java.util.*
import java.util.function.Function

class MultiArgumentParser<T>(private val name: String?, private val eventFactory: Function<Array<String?>?, T?>) :
    IParser<String?, T?> {
    override fun tryParse(s: String): Optional<T?> {
        var s = s
        s = s.trim()
        if (s.startsWith(name + " ")) {
            val l: Array<String?> = s.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            return Optional.of<T?>(
                eventFactory.apply(
                    java.util.Arrays.stream<kotlin.String?>(l).skip(1)
                        .toArray<kotlin.String?> { _Dummy_.__Array__() })!!
            )
        }
        return Optional.empty<T?>()
    }
}
