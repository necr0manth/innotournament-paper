package dev.necr0manthre.innotournament.init

import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.events.AbstractTournamentEvent
import dev.necr0manthre.innotournament.tournament.events.LambdaTournamentEvent
import dev.necr0manthre.innotournament.tournament.events.events.TimeEvent
import dev.necr0manthre.innotournament.tournament.events.parsing.IParser
import dev.necr0manthre.innotournament.tournament.events.parsing.TournamentEventParserRegistry
import dev.necr0manthre.innotournament.tournament.events.parsing.parsers.OneArgumentParser
import dev.necr0manthre.innotournament.tournament.events.parsing.parsers.SimpleDecoder
import org.apache.logging.log4j.util.TriConsumer
import java.util.function.Consumer
import kotlin.concurrent.atomics.AtomicReference

interface InnoTournamentEvents {
    companion object {
        private fun <E : AbstractTournamentEvent<*>?, T : IParser<String, E>?> register(parser: T): T? {
            return TournamentEventParserRegistry.INSTANCE.register<String?, E?, T?>(
                String::class.java,
                parser
            )
        }

        fun init() {
        }

        val TIME: IParser<kotlin.String?, TimeEvent?>? =
            InnoTournamentEvents.Companion.register<TimeEvent?, OneArgumentParser<TimeEvent?>?>(
                OneArgumentParser<TimeEvent?>(
                    "time", java.util.function.Function { time: kotlin.String? -> TimeEvent(time!!.toLong()) })
            )
        val PREPARE: IParser<kotlin.String?, AbstractTournamentEvent<*>?>? =
            InnoTournamentEvents.Companion.register<AbstractTournamentEvent<*>?, SimpleDecoder<AbstractTournamentEvent<*>?>?>(
                SimpleDecoder<AbstractTournamentEvent<*>?>(
                    "onPrepare",
                    java.util.function.Supplier {
                        LambdaTournamentEvent<Any?, Consumer<Any?>?>(
                            TriConsumer { tournament: Tournament?, callback: Consumer<Any?>?, additionalData: AtomicReference<Consumer<Any?>?>? ->
                                tournament.onPrepare.register(
                                    callback
                                )
                            },
                            TriConsumer { tournament: Tournament?, callback: Consumer<Any?>?, data: Consumer<Any?>? ->
                                tournament.onStart.unregister(callback)
                            })
                    })
            )
        val START: IParser<kotlin.String?, AbstractTournamentEvent<*>?>? =
            InnoTournamentEvents.Companion.register<AbstractTournamentEvent<*>?, SimpleDecoder<AbstractTournamentEvent<*>?>?>(
                SimpleDecoder<AbstractTournamentEvent<*>?>(
                    "onStart",
                    java.util.function.Supplier {
                        LambdaTournamentEvent<kotlin.Any?, java.util.function.Consumer<kotlin.Any?>?>(
                            org.apache.logging.log4j.util.TriConsumer { tournament: Tournament?, callback: java.util.function.Consumer<kotlin.Any?>?, additionalData: AtomicReference<java.util.function.Consumer<kotlin.Any?>?>? ->
                                tournament.onStart.register(
                                    callback
                                )
                            },
                            org.apache.logging.log4j.util.TriConsumer { tournament: Tournament?, callback: java.util.function.Consumer<kotlin.Any?>?, data: java.util.function.Consumer<kotlin.Any?>? ->
                                tournament.onStart.unregister(callback)
                            })
                    })
            )
        val END: IParser<kotlin.String?, AbstractTournamentEvent<*>?>? =
            InnoTournamentEvents.Companion.register<AbstractTournamentEvent<*>?, SimpleDecoder<AbstractTournamentEvent<*>?>?>(
                SimpleDecoder<AbstractTournamentEvent<*>?>(
                    "onEnd",
                    java.util.function.Supplier {
                        LambdaTournamentEvent<kotlin.Any?, java.util.function.Consumer<kotlin.Any?>?>(
                            org.apache.logging.log4j.util.TriConsumer { tournament: Tournament?, callback: java.util.function.Consumer<kotlin.Any?>?, additionalData: AtomicReference<java.util.function.Consumer<kotlin.Any?>?>? ->
                                tournament.onEnd.register(
                                    callback
                                )
                            },
                            org.apache.logging.log4j.util.TriConsumer { tournament: Tournament?, callback: java.util.function.Consumer<kotlin.Any?>?, data: java.util.function.Consumer<kotlin.Any?>? ->
                                tournament.onStart.unregister(callback)
                            })
                    })
            )
        val ACHIEVEMENT: IParser<kotlin.String?, AbstractTournamentEvent<*>?>? =
            InnoTournamentEvents.Companion.register<AbstractTournamentEvent<*>?, OneArgumentParser<AbstractTournamentEvent<*>?>?>(
                OneArgumentParser<AbstractTournamentEvent<*>?>(
                    "advancement",
                    java.util.function.Function { achievement: kotlin.String? -> AchievementEvent(achievement) })
            )
        val TEAM_ACHIEVEMENT: IParser<kotlin.String?, AbstractTournamentEvent<*>?>? =
            InnoTournamentEvents.Companion.register<AbstractTournamentEvent<*>?, OneArgumentParser<AbstractTournamentEvent<*>?>?>(
                OneArgumentParser<AbstractTournamentEvent<*>?>(
                    "teamAdvancement",
                    java.util.function.Function { achievement: kotlin.String? -> TeamAchievementEvent(achievement) })
            )
        val PLAYER_KILLED: IParser<kotlin.String?, AbstractTournamentEvent<*>?>? =
            InnoTournamentEvents.Companion.register<AbstractTournamentEvent<*>?, SimpleDecoder<AbstractTournamentEvent<*>?>?>(
                SimpleDecoder<AbstractTournamentEvent<*>?>("playerKilled", java.util.function.Supplier {
                    LambdaTournamentEvent<PlayerKilled?, EntityEvent.LivingDeath?>(
                        org.apache.logging.log4j.util.TriConsumer { tournament: Tournament?, callback: java.util.function.Consumer<PlayerKilled?>?, additionalData: AtomicReference<EntityEvent.LivingDeath?>? ->
                            additionalData.set(EntityEvent.LivingDeath { entity, damageSource ->
                                if (entity is ServerPlayer && damageSource.getEntity() is ServerPlayer) {
                                    if (TournamentPlayer.Companion.getTournamentData(
                                            PlayerManager.Companion.getEntity(
                                                entity
                                            )
                                        ).lives > 0
                                    ) callback!!.accept(
                                        PlayerKilled(
                                            PlayerManager.Companion.getEntity(source),
                                            PlayerManager.Companion.getEntity(entity)
                                        )
                                    )
                                }
                                EventResult.pass()
                            })
                            EntityEvent.LIVING_DEATH.register(additionalData.get())
                        },
                        org.apache.logging.log4j.util.TriConsumer { tournament: Tournament?, callback: java.util.function.Consumer<PlayerKilled?>?, data: EntityEvent.LivingDeath? ->
                            EntityEvent.LIVING_DEATH.unregister(data)
                        })
                })
            )
        val PLAYER_TICK: IParser<kotlin.String?, AbstractTournamentEvent<PlayerHolder?>?>? =
            InnoTournamentEvents.Companion.register<AbstractTournamentEvent<PlayerHolder?>?, SimpleDecoder<AbstractTournamentEvent<PlayerHolder?>?>?>(
                SimpleDecoder<AbstractTournamentEvent<PlayerHolder?>?>("playerTick", java.util.function.Supplier {
                    LambdaTournamentEvent<PlayerHolder?, TickEvent.Player?>(
                        org.apache.logging.log4j.util.TriConsumer { tournament: Tournament?, consumer: java.util.function.Consumer<PlayerHolder?>?, atomicReference: AtomicReference<TickEvent.Player?>? ->
                            atomicReference.set(TickEvent.Player { player ->
                                if (player is ServerPlayer) consumer!!.accept(
                                    PlayerHolder(
                                        PlayerManager.Companion.getEntity(
                                            player
                                        )
                                    )
                                )
                            })
                            TickEvent.PLAYER_POST.register(atomicReference.get())
                        },
                        (org.apache.logging.log4j.util.TriConsumer { tournament: Tournament?, consumer: java.util.function.Consumer<PlayerHolder?>?, `object`: TickEvent.Player? ->
                            TickEvent.PLAYER_POST.unregister(`object`)
                        })
                    )
                })
            )
        val PLAYER_TICK_WITH_PERIOD: IParser<kotlin.String?, AbstractTournamentEvent<PlayerHolder?>?>? =
            InnoTournamentEvents.Companion.register<AbstractTournamentEvent<PlayerHolder?>?, OneArgumentParser<AbstractTournamentEvent<PlayerHolder?>?>?>(
                OneArgumentParser<AbstractTournamentEvent<PlayerHolder?>?>(
                    "playerTick",
                    java.util.function.Function { preiod: kotlin.String? -> PlayerTickEvent(preiod!!.toInt()) })
            )
    }
}
