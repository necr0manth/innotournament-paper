package dev.necr0manthre.innotournament.tournament.events

import kotlin.reflect.KClass
import kotlin.jvm.javaObjectType

/**
 * The declared value class and nullability of a source or listener.
 *
 * Only non-generic classes/interfaces can describe event values: generic type
 * arguments are erased and cannot safely distinguish two listeners at load.
 * Describe such a value with a named, non-generic data class instead.
 */
class ValueType<T> private constructor(
    val valueClass: Class<*>,
    val nullable: Boolean
) {
    fun nullable(): ValueType<T?> = ValueType(valueClass, true)

    /** A value produced by [source] can always be consumed by this type. */
    fun accepts(source: ValueType<*>): Boolean =
        valueClass.isAssignableFrom(source.valueClass) && (nullable || !source.nullable)

    override fun toString(): String = "${valueClass.typeName}${if (nullable) "?" else ""}"

    companion object {
        fun <T : Any> of(type: KClass<T>): ValueType<T> {
            val javaClass = type.javaObjectType
            require(javaClass.typeParameters.isEmpty()) {
                "Generic value type ${javaClass.typeName} is not supported; use a named payload class"
            }
            return ValueType(javaClass, false)
        }
    }
}
