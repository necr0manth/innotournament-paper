package dev.necr0manthre.innotournament.tournament.events.parsing.parsers

import dev.necr0manthre.innotournament.tournament.events.parsing.IParser

class OneArgumentParser<T>(
    private val name: String?,
    private val factory: (String) -> T?,
    private var greedy: Boolean = false
) : IParser<String, T> {
    override fun tryParse(str: String): T? {
        val str = str.trim()
        val args = str.removePrefix("$name ")
        if (args == str)
            return null
        if (greedy) {
            return factory(args)
        }
        val trimmedArgs = args.trim()
        if (trimmedArgs.contains(" "))
            return null
        return factory(trimmedArgs)
    }
}
