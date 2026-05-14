package dev.necr0manthre.innotournament.tournament.events.parsing.parsers

import dev.necr0manthre.innotournament.tournament.events.parsing.IParser
import java.util.*
import java.util.function.Function

class OneArgumentParser<T> : IParser<String?, T?> {
    private val name: String?
    private val eventFactory: Function<String?, T?>
    private var greedy = false

    constructor(name: String?, eventFactory: Function<String?, T?>) {
        this.name = name
        this.eventFactory = eventFactory
    }

    constructor(name: String?, eventFactory: Function<String?, T?>, greedy: Boolean) {
        this.name = name
        this.eventFactory = eventFactory
        this.greedy = greedy
    }

    override fun tryParse(s: String): Optional<T?> {
        var s = s
        s = s.trim()
        if (s.startsWith(name + " ")) {
            if (greedy) {
                val spacePos = s.indexOf(" ")
                return Optional.of<T?>(eventFactory.apply(s.substring(spacePos + 1))!!)
            } else {
                val l: Array<String?> = s.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (l.size == 2) {
                    return Optional.of<T?>(eventFactory.apply(l[1])!!)
                }
            }
        }
        return Optional.empty<T?>()
    }
}
