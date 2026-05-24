package dev.necr0manthre.innotournament.init

import dev.necr0manthre.innotournament.Innotournament
import dev.necr0manthre.innotournament.tournament.events.ITournamentEventListener
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
    private fun <E : ITournamentEventListener<*>, T : IParser<String, E>> register(parser: T): T {
        return TournamentEventHandlerParserRegistry.register(String::class, parser)
    }

    fun init() {
    }

    val WORLD_BORDER = register(
        OneArgumentParser("world_border", { size ->
            WorldBorderAction(size.toDouble(), 0)
        })
    )

    val WORLD_BORDER_TIME = register(
        TwoArgumentParser("world_border") { size, time ->
            WorldBorderAction(size.toDouble(), time.toLong())
        }
    )

    val LOG_ARG = register(
        OneArgumentParser("log", { message ->
            ITournamentEventListener<Any?> { _, _ -> Innotournament.instance.logger.info(message) }
        }, greedy = true)
    )

    val LOG_EVENT = register(
        SimpleDecoder("log") {
            ITournamentEventListener<Any?> { _, value -> Innotournament.instance.logger.info(value.toString()) }
        }
    )

    val PLACE_START_BOX = register(
        SimpleDecoder("place_start_box") {
            ITournamentEventListener<Any?> { tournament, _ -> tournament.placeStartBox() }
        }
    )

    val REMOVE_START_BOX = register(
        SimpleDecoder("remove_start_box") {
            ITournamentEventListener<Any?> { tournament, _ -> tournament.removeStartBox() }
        }
    )

    val SCORE = register(
        OneArgumentParser("add_score", { score ->
            ITournamentEventListener<ITeamProvider> { tournament, teamProvider ->
                tournament.addScores(teamProvider.team, score.toDouble())
            }
        })
    )

    val SCORE_FROM_LIVES = register(
        MultiArgumentParser("add_score_by_lives") { scores ->
            ITournamentEventListener<ISourcePlayerProvider> { tournament, playerProvider ->
                val player = playerProvider.sourcePlayer ?: return@ITournamentEventListener
                val score = scores.getOrNull(player.lives)?.toDoubleOrNull() ?: return@ITournamentEventListener
                tournament.addScores(player.team, score)
            }
        }
    )

    val PVP = register(
        OneArgumentParser("pvp", { enabled ->
            ITournamentEventListener<Any?> { tournament, _ ->
                Bukkit.getWorlds().forEach { it.pvp = enabled.toBoolean() }
            }
        })
    )

    val EXECUTE_COMMAND = register(
        OneArgumentParser("execute_command", { command ->
            ITournamentEventListener<Any?> { _, _ ->
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                } catch (e: Exception) {
                    Innotournament.instance.logger.warning("Failed to execute command '$command': ${e.message}")
                }
            }
        }, greedy = true)
    )
}
