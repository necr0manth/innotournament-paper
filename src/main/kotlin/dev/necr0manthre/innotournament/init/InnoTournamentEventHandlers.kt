package dev.necr0manthre.innotournament.init

import dev.necr0manthre.innotournament.Innotournament
import dev.necr0manthre.innotournament.tournament.events.EventDeliveryPhase
import dev.necr0manthre.innotournament.tournament.events.ITournamentEventListener
import dev.necr0manthre.innotournament.tournament.events.ParsedListener
import dev.necr0manthre.innotournament.tournament.events.ValueType
import dev.necr0manthre.innotournament.tournament.events.event_data.ISourcePlayerProvider
import dev.necr0manthre.innotournament.tournament.events.event_data.ITeamProvider
import dev.necr0manthre.innotournament.tournament.events.handlers.WorldBorderAction
import dev.necr0manthre.innotournament.tournament.events.parsing.IParser
import dev.necr0manthre.innotournament.tournament.events.parsing.TournamentEventHandlerParserRegistry
import dev.necr0manthre.innotournament.tournament.events.parsing.parsers.MultiArgumentParser
import dev.necr0manthre.innotournament.tournament.events.parsing.parsers.OneArgumentParser
import dev.necr0manthre.innotournament.tournament.events.parsing.parsers.SimpleDecoder
import dev.necr0manthre.innotournament.tournament.events.parsing.parsers.TwoArgumentParser
import org.bukkit.Bukkit

object InnoTournamentEventHandlers {
    private val inGameOnly = setOf(EventDeliveryPhase.STARTED)
    private val beforeOrInGame = setOf(EventDeliveryPhase.PREPARED, EventDeliveryPhase.STARTED)
    private val anyPhase = EventDeliveryPhase.entries.toSet()

    // A concrete typed registration, not a runtime adapter to an arbitrary event payload.
    private fun <V, E : ITournamentEventListener<V>, P : IParser<String, E>> register(
        accepted: ValueType<V>,
        parser: P,
        allowedPhases: Set<EventDeliveryPhase> = anyPhase
    ): IParser<String, ParsedListener<V>> {
        val typed = object : IParser<String, ParsedListener<V>> {
            override fun tryParse(t: String): ParsedListener<V>? =
                parser.tryParse(t)?.let { ParsedListener(accepted, it, allowedPhases) }
        }
        return TournamentEventHandlerParserRegistry.register(typed)
    }

    fun init() {
    }

    private fun parseBorderSize(raw: String): Double = raw.toDouble().also {
        require(it.isFinite() && it > 0.0) { "Border size must be finite and positive" }
    }

    private fun parseBorderTicks(raw: String): Long = raw.toLong().also {
        // Paper WorldBorder.changeSize(double, long) accepts 0..Int.MAX_VALUE ticks.
        require(it in 0L..Int.MAX_VALUE.toLong()) { "Border duration must be 0..${Int.MAX_VALUE} ticks" }
    }

    val WORLD_BORDER = register(
        ValueType.of(Any::class).nullable(),
        OneArgumentParser("world_border", { size ->
            WorldBorderAction(parseBorderSize(size), 0L)
        })
    )

    val WORLD_BORDER_TIME = register(
        ValueType.of(Any::class).nullable(),
        TwoArgumentParser("world_border") { size, time ->
            WorldBorderAction(parseBorderSize(size), parseBorderTicks(time))
        }
    )

    val LOG_ARG = register(
        ValueType.of(Any::class).nullable(),
        OneArgumentParser("log", { message ->
            ITournamentEventListener<Any?> { _, _ -> Innotournament.instance.logger.info(message) }
        }, greedy = true)
    )

    val LOG_EVENT = register(
        ValueType.of(Any::class).nullable(),
        SimpleDecoder("log") {
            ITournamentEventListener<Any?> { _, value -> Innotournament.instance.logger.info(value.toString()) }
        }
    )

    val PLACE_START_BOX = register(
        ValueType.of(Any::class).nullable(),
        SimpleDecoder("place_start_box") {
            ITournamentEventListener<Any?> { tournament, _ -> tournament.placeStartBox() }
        }, beforeOrInGame
    )

    val REMOVE_START_BOX = register(
        ValueType.of(Any::class).nullable(),
        SimpleDecoder("remove_start_box") {
            ITournamentEventListener<Any?> { tournament, _ -> tournament.removeStartBox() }
        }
    )

    val SCORE = register(
        ValueType.of(ITeamProvider::class),
        OneArgumentParser("add_score", { score ->
            val amount = score.toDouble().also { require(it.isFinite()) }
            ITournamentEventListener<ITeamProvider> { tournament, teamProvider ->
                tournament.addScores(teamProvider.team, amount)
            }
        }), inGameOnly
    )

    val SCORE_FROM_LIVES = register(
        ValueType.of(ISourcePlayerProvider::class),
        MultiArgumentParser("add_score_by_lives") { rawScores ->
            val scores = rawScores.map { it.toDouble().also { number -> require(number.isFinite()) } }
            ITournamentEventListener<ISourcePlayerProvider> { tournament, playerProvider ->
                val player = playerProvider.sourcePlayer ?: return@ITournamentEventListener
                val team = player.team ?: return@ITournamentEventListener
                // Original Java's scores[lives] throws on an out-of-range index.
                tournament.addScores(team, scores[player.lives])
            }
        }, inGameOnly
    )

    val PVP = register(
        ValueType.of(Any::class).nullable(),
        OneArgumentParser("pvp", { enabled ->
            val pvp = when (enabled.lowercase()) {
                "true" -> true
                "false" -> false
                else -> throw IllegalArgumentException("Expected true or false")
            }
            ITournamentEventListener<Any?> { _, _ ->
                Bukkit.getWorlds().forEach { it.pvp = pvp }
            }
        })
    )

    val EXECUTE_COMMAND = register(
        ValueType.of(Any::class).nullable(),
        OneArgumentParser("execute_command", { command ->
            ITournamentEventListener<Any?> { _, _ ->
                if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
                    Innotournament.instance.logger.warning("Command was not executed: '$command'")
                }
            }
        }, greedy = true)
    )
}
