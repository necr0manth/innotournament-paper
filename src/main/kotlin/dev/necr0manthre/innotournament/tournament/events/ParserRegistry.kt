package dev.necr0manthre.innotournament.tournament.events

import dev.necr0manthre.innotournament.tournament.events.parsing.IParser

open class ParserRegistry<O> {
    private val registry: MutableMap<Class<*>, MutableSet<IParser<*, out O>>> = mutableMapOf()

    fun <T, E : O?, P : IParser<T?, E?>?> register(clazz: Class<T?>?, parser: P?): P? {
        registry.computeIfAbsent(clazz) { k: java.lang.Class<*>? -> HashSet<IParser<*, out O?>?>() }!!.add(parser)
        return parser
    }

    fun <T> getParsers(clazz: Class<T?>?): MutableSet<IParser<T, out O>> {
        return registry.computeIfAbsent(clazz) { k: Class<*>? -> HashSet<IParser<*, out O?>?>() } as Any as MutableSet<IParser<T?, out O?>?>
    }
}
