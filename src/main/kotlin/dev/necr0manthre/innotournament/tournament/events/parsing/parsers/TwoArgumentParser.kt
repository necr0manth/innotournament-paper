package dev.necr0manthre.innotournament.tournament.events.parsing.parsers

import dev.necr0manthre.innotournament.tournament.events.parsing.IParser

class TwoArgumentParser<T>(private val name: String?, private val factory: (String, String) -> T?) :
    IParser<String, T> {
    override fun tryParse(str: String): T? {
        val str = str.trim()
        val args = str.removePrefix("$name ")
        if (args == str)
            return null
        val argsArray = args.split(" ").dropLastWhile { it.isEmpty() }.toTypedArray()
        if (argsArray.size == 2)
            return factory(argsArray[0], argsArray[1])
        return null
    }
}
