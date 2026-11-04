package dev.necr0manthre.innotournament.tournament.events

import dev.necr0manthre.innotournament.tournament.Tournament
import java.nio.file.Path
import java.util.Collections

/** One already-checked event/action binding; no wildcard listener API. */
sealed interface EventPlanEntry {
    /** The caller retains each returned instance immediately, before subscribing the next entry. */
    fun subscribe(tournament: Tournament): TournamentEventInstance<*>
}

/** Immutable ordered snapshot of one pre-start or main event file. */
class EventPlan private constructor(entries: List<EventPlanEntry>) {
    val entries: List<EventPlanEntry> = Collections.unmodifiableList(entries.toList())

    companion object {
        fun of(entries: List<EventPlanEntry>): EventPlan = EventPlan(entries)
    }
}

/** Source context captured when the plan is bound, for fail-fast diagnostics. */
data class EventActionLocation(
    val file: Path,
    val eventExpression: String,
    val actionIndex: Int,
    val actionExpression: String
) {
    override fun toString(): String =
        "$file: event '$eventExpression', action[$actionIndex] '$actionExpression'"
}

class EventPlanLoadException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)
class EventSubscriptionException(message: String, cause: Throwable) : RuntimeException(message, cause)
class EventDispatchException(message: String, cause: Throwable) : RuntimeException(message, cause)

/** The input to the single checked binding boundary; filled by the JSON parser. */
data class EventRuleLocation(val file: Path, val eventExpression: String) {
    override fun toString(): String = "$file: event '$eventExpression'"
}

class EventRule(
    val event: TournamentEvent<*>,
    val location: EventRuleLocation,
    actions: List<RuleAction>
) {
    val actions: List<RuleAction> = Collections.unmodifiableList(actions.toList())
}

class RuleAction(val location: EventActionLocation, val parsed: ParsedListener<*>)
