package dev.necr0manthre.innotournament.tournament.events

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.events.parsing.TournamentEventHandlerParser
import dev.necr0manthre.innotournament.tournament.events.parsing.TournamentEventParser
import java.nio.file.Files
import java.nio.file.Path

/** Strict parsing and the only erased-type conversion boundary of the event protocol. */
object EventLoader {
    fun loadEvents(path: Path, stage: EventPlanStage): EventPlan {
        val json = try {
            Files.newBufferedReader(path).use { reader -> JsonParser.parseReader(reader) }
        } catch (e: Exception) {
            throw EventPlanLoadException("Cannot read or parse event file '$path': ${e.message}", e)
        }
        return loadEvents(json, path, stage)
    }

    /** Parse everything before any subscribe/effect; an error never publishes a partial plan. */
    fun loadEvents(json: JsonElement, path: Path, stage: EventPlanStage): EventPlan {
        if (!json.isJsonObject) throw EventPlanLoadException("$path: expected JSON object of event expressions")
        val rules = ArrayList<EventRule>()
        for ((expression, value) in json.asJsonObject.entrySet()) {
            val event = try {
                TournamentEventParser.parse(expression)
                    ?: throw EventPlanLoadException("$path: unknown event expression '$expression'")
            } catch (e: EventPlanLoadException) {
                throw e
            } catch (e: Exception) {
                throw EventPlanLoadException("$path: invalid event expression '$expression': ${e.message}", e)
            }
            val phase = deliveryPhase(path, expression, stage)
            val lines = when {
                value.isJsonPrimitive && value.asJsonPrimitive.isString -> listOf(value.asString)
                value.isJsonArray -> value.asJsonArray.mapIndexed { index, item ->
                    if (!item.isJsonPrimitive || !item.asJsonPrimitive.isString)
                        throw EventPlanLoadException("$path: event '$expression', action[$index]: expected string, got $item")
                    item.asString
                }
                else -> throw EventPlanLoadException("$path: event '$expression': expected action string or array of strings")
            }
            val actions = lines.mapIndexed { index, line ->
                val location = EventActionLocation(path, expression, index, line)
                val parsed = try {
                    TournamentEventHandlerParser.parse(line)
                        ?: throw EventPlanLoadException("$location: unknown action")
                } catch (e: EventPlanLoadException) {
                    throw e
                } catch (e: Exception) {
                    throw EventPlanLoadException("$location: invalid action: ${e.message}", e)
                }
                if (phase !in parsed.allowedPhases)
                    throw EventPlanLoadException("$location: action is not permitted during $phase")
                RuleAction(location, parsed)
            }
            rules.add(EventRule(event, EventRuleLocation(path, expression), actions))
        }
        return bindRules(rules)
    }

    /** A pure boundary usable by the next repository/parser component, without JSON or Bukkit. */
    fun bindRules(rules: List<EventRule>): EventPlan = EventPlan.of(rules.map { rule ->
        bind(rule.event, rule.location, rule.actions)
    })

    private fun deliveryPhase(path: Path, expression: String, stage: EventPlanStage): EventDeliveryPhase {
        // SimpleDecoder accepts surrounding whitespace; lifecycle checks must use the same spelling.
        val parsedName = expression.trim()
        return when (parsedName) {
            "onPrepare" -> {
                if (stage != EventPlanStage.PRE_START)
                    throw EventPlanLoadException("$path: event '$expression' only belongs in pre-start plan")
                EventDeliveryPhase.PREPARED
            }
            "onStart", "onEnd" -> {
                if (stage != EventPlanStage.MAIN)
                    throw EventPlanLoadException("$path: event '$expression' only belongs in main plan")
                if (parsedName == "onEnd") EventDeliveryPhase.ENDED else EventDeliveryPhase.STARTED
            }
            else -> if (stage == EventPlanStage.PRE_START) EventDeliveryPhase.PREPARED else EventDeliveryPhase.STARTED
        }
    }

    private fun <T> bind(
        event: TournamentEvent<T>, location: EventRuleLocation, actions: List<RuleAction>
    ): EventPlanEntry {
        // Validate the whole rule, not merely the first listener, before adapting any listener.
        for (action in actions) {
            if (!action.parsed.acceptedType.accepts(event.valueType)) {
                throw EventPlanLoadException(
                    "${action.location}: event emits ${event.valueType}, " +
                        "listener requires ${action.parsed.acceptedType}"
                )
            }
        }
        val checked = actions.map { action ->
            BoundAction(action.location, checkedListener<T>(action.parsed))
        }
        return BoundEntry(event, location, checked)
    }

    // The single unchecked type conversion, reached only after ALL declared types were checked.
    @Suppress("UNCHECKED_CAST")
    private fun <T> checkedListener(parsed: ParsedListener<*>): ITournamentEventListener<T> =
        parsed.listener as ITournamentEventListener<T>

    private class BoundAction<T>(
        val location: EventActionLocation,
        val listener: ITournamentEventListener<T>
    )

    private class BoundEntry<T>(
        private val source: TournamentEvent<T>,
        private val location: EventRuleLocation,
        private val actions: List<BoundAction<T>>
    ) : EventPlanEntry {
        override fun subscribe(tournament: Tournament): TournamentEventInstance<*> {
            val instance = try {
                source.subscribe(tournament)
            } catch (failure: Exception) {
                // A source failing before returning its instance is not an owned handle.
                throw EventSubscriptionException("$location: source subscription failed", failure)
            }
            try {
                instance.addListener(ITournamentEventListener { owner, value ->
                    for (action in actions) {
                        try {
                            action.listener.listen(owner, value)
                        } catch (failure: Exception) {
                            throw EventDispatchException("${action.location}: action failed", failure)
                        }
                    }
                })
            } catch (failure: Exception) {
                // The instance has not been returned to its owner: no invented rollback.
                throw EventSubscriptionException("$location: listener attachment failed", failure)
            }
            return instance
        }
    }
}
