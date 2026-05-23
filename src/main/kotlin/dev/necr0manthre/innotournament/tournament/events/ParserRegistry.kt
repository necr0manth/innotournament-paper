package dev.necr0manthre.innotournament.tournament.events

import dev.necr0manthre.innotournament.tournament.events.parsing.IParser
import kotlin.reflect.KClass

open class ParserRegistry<O> {
    private val registry: MutableMap<KClass<*>, MutableSet<IParser<*, O>>> = mutableMapOf()

    fun <T : Any, E : O, P : IParser<T, E>> register(clazz: KClass<T>, parser: P): P {
        registry.computeIfAbsent(clazz) { _ -> mutableSetOf() }.add(parser)
        return parser
    }

    fun <T : Any> getParsers(clazz: KClass<out T>): MutableSet<IParser<T, O>> {
        return registry.computeIfAbsent(clazz) { k -> mutableSetOf() } as MutableSet<IParser<T, O>>
    }
}
