package dev.necr0manthre.innotournament.init

import com.mojang.brigadier.exceptions.CommandSyntaxException
import dev.necr0manthre.innotournament.Innotournament
import dev.necr0manthre.innotournament.teams.core.TeamManager
import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.components.TournamentPlayer
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
import java.util.function.BiFunction
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

interface InnoTournamentEventHandlers {
    companion object {
        private fun <E : ITournamentEventListener<*>?, T : IParser<String?, E?>?> register(parser: T?): T? {
            return TournamentEventHandlerParserRegistry.INSTANCE.register<String?, E?, T?>(String::class.java, parser)
        }

        fun init() {
        }

        val WORLD_BORDER: IParser<String?, ITournamentEventListener<Any?>?>? =
            register<ITournamentEventListener<Any?>?, OneArgumentParser<ITournamentEventListener<Any?>?>?>(
                OneArgumentParser<ITournamentEventListener<Any?>?>(
                    "world_border",
                    Function { size: String? -> WorldBorderAction(size!!.toDouble(), 0) })
            )
        val WORLD_BORDER_TIME: IParser<String?, ITournamentEventListener<Any?>?>? =
            register<ITournamentEventListener<Any?>?, TwoArgumentParser<ITournamentEventListener<Any?>?>?>(
                TwoArgumentParser<ITournamentEventListener<Any?>?>(
                    "world_border",
                    BiFunction { size: String?, time: String? ->
                        WorldBorderAction(
                            size!!.toDouble(),
                            time!!.toLong()
                        )
                    })
            )
        val LOG_ARG: IParser<String?, ITournamentEventListener<Any?>?>? =
            register<ITournamentEventListener<Any?>?, OneArgumentParser<ITournamentEventListener<Any?>?>?>(
                OneArgumentParser<ITournamentEventListener<Any?>?>(
                    "log",
                    Function { str: String? ->
                        ITournamentEventListener { tournament: Tournament?, obj: Any? ->
                            Innotournament.Companion.LOGGER.info(str)
                        }
                    })
            )
        val LOG_EVENT: IParser<String?, ITournamentEventListener<Any?>?>? =
            register<ITournamentEventListener<Any?>?, SimpleDecoder<ITournamentEventListener<Any?>?>?>(
                SimpleDecoder<ITournamentEventListener<Any?>?>(
                    "log",
                    Supplier {
                        ITournamentEventListener { tournament: Tournament?, obj: Any? ->
                            Innotournament.Companion.LOGGER.info(obj.toString())
                        }
                    })
            )
        val PLACE_START_BOX: IParser<String?, ITournamentEventListener<Any?>?>? =
            register<ITournamentEventListener<Any?>?, SimpleDecoder<ITournamentEventListener<Any?>?>?>(
                SimpleDecoder<ITournamentEventListener<Any?>?>(
                    "place_start_box",
                    Supplier { ITournamentEventListener { tournament: Tournament?, obj: Any? -> tournament!!.placeStartBox() } })
            )
        val REMOVE_START_BOX: IParser<String?, ITournamentEventListener<Any?>?>? =
            register<ITournamentEventListener<Any?>?, SimpleDecoder<ITournamentEventListener<Any?>?>?>(
                SimpleDecoder<ITournamentEventListener<Any?>?>(
                    "remove_start_box",
                    Supplier { ITournamentEventListener { tournament: Tournament?, obj: Any? -> tournament!!.removeStartBox() } })
            )
        val SCORE: IParser<String?, ITournamentEventListener<ITeamProvider?>?>? =
            register<ITournamentEventListener<ITeamProvider?>?, OneArgumentParser<ITournamentEventListener<ITeamProvider?>?>?>(
                OneArgumentParser<ITournamentEventListener<ITeamProvider?>?>(
                    "add_score",
                    Function { score: String? ->
                        ITournamentEventListener { tournament: Tournament?, teamProvider: ITeamProvider? ->
                            tournament!!.addScores(
                                teamProvider!!.getTeam(),
                                score!!.toInt().toDouble()
                            )
                        }
                    })
            )
        val SCORE_FROM_LIVES: IParser<String?, ITournamentEventListener<ISourcePlayerProvider?>?>? =
            register<ITournamentEventListener<ISourcePlayerProvider?>?, MultiArgumentParser<ITournamentEventListener<ISourcePlayerProvider?>?>?>(
                MultiArgumentParser<ITournamentEventListener<ISourcePlayerProvider?>?>(
                    "add_score_by_lives",
                    Function { scores: Array<String?>? ->
                        ITournamentEventListener { tournament: Tournament?, playerProvider: ISourcePlayerProvider? ->
                            TeamManager.Companion.getPlayersTeam(playerProvider!!.getSourcePlayer()).ifPresent(
                                Consumer { team: Entity? ->
                                    tournament!!.addScores(
                                        team,
                                        scores!![TournamentPlayer.Companion.getTournamentData(playerProvider.getSourcePlayer()).lives]!!.toInt()
                                            .toDouble()
                                    )
                                })
                        }
                    })
            )
        val PVP: IParser<String?, ITournamentEventListener<Any?>?>? =
            register<ITournamentEventListener<Any?>?, OneArgumentParser<ITournamentEventListener<Any?>?>?>(
                OneArgumentParser<ITournamentEventListener<Any?>?>(
                    "pvp",
                    Function { enabled: String? ->
                        ITournamentEventListener { tournament: Tournament?, obj: Any? ->
                            tournament!!.getServer().setPvpAllowed(enabled.toBoolean())
                        }
                    })
            )
        val EXECUTE_COMMAND: IParser<String?, ITournamentEventListener<Any?>?>? =
            register<ITournamentEventListener<Any?>?, OneArgumentParser<ITournamentEventListener<Any?>?>?>(
                OneArgumentParser<ITournamentEventListener<Any?>?>("execute_command", Function { cmd: String? ->
                    ITournamentEventListener { tournament: Tournament?, obj: Any? ->
                        try {
                            tournament!!.getServer().getCommands().getDispatcher()
                                .execute(cmd, tournament.getServer().createCommandSourceStack().withPermission(4))
                        } catch (e: CommandSyntaxException) {
                            Innotournament.Companion.LOGGER.error("Failed to execute command: {}", cmd, e)
                        }
                    }
                }, true)
            )
    }
}
