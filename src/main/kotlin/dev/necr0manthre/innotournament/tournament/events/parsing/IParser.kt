package dev.necr0manthre.innotournament.tournament.events.parsing

interface IParser<in T, out E> {
    fun tryParse(t: T): E?
}
