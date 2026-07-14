package dev.necr0manthre.innotournament.init

import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.events.BukkitEventBasedTournamentEvent
import dev.necr0manthre.innotournament.tournament.events.LambdaTournamentEvent
import dev.necr0manthre.innotournament.tournament.events.TournamentEvent
import dev.necr0manthre.innotournament.tournament.events.TournamentEventInstance
import dev.necr0manthre.innotournament.tournament.events.event_data.PlayerKilled
import dev.necr0manthre.innotournament.tournament.events.events.AdvancementEvent
import dev.necr0manthre.innotournament.tournament.events.events.PlayerTickEvent
import dev.necr0manthre.innotournament.tournament.events.events.TeamAdvancementEvent
import dev.necr0manthre.innotournament.tournament.events.events.TimeEvent
import dev.necr0manthre.innotournament.tournament.events.parsing.IParser
import dev.necr0manthre.innotournament.tournament.events.parsing.TournamentEventParserRegistry
import dev.necr0manthre.innotournament.tournament.events.parsing.parsers.OneArgumentParser
import dev.necr0manthre.innotournament.tournament.events.parsing.parsers.SimpleDecoder
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.advancement.Advancement
import org.bukkit.event.entity.PlayerDeathEvent

object InnoTournamentEvents {
    private fun <E : TournamentEvent<*>, T : IParser<String, E>> register(parser: T): T {
        return TournamentEventParserRegistry.register(String::class, parser)
    }

    fun init() {
    }

    val TIME = register(
        OneArgumentParser("time", { time -> TimeEvent(time.toLong()) })
    )

    val PREPARE = register(
        SimpleDecoder("onPrepare") {
            lifecycleEvent(
                subscribe = { listener -> onPrepare.subscribe(listener) },
                unsubscribe = { listener -> onPrepare.unsubscribe(listener) }
            )
        }
    )

    val START = register(
        SimpleDecoder("onStart") {
            lifecycleEvent(
                subscribe = { listener -> onStart.subscribe(listener) },
                unsubscribe = { listener -> onStart.unsubscribe(listener) }
            )
        }
    )

    val END = register(
        SimpleDecoder("onEnd") {
            lifecycleEvent(
                subscribe = { listener -> onEnd.subscribe(listener) },
                unsubscribe = { listener -> onEnd.unsubscribe(listener) }
            )
        }
    )

    val ADVANCEMENT = register(
        OneArgumentParser("advancement", { id ->
            AdvancementEvent(resolveAdvancement(id))
        })
    )

    val TEAM_ADVANCEMENT = register(
        OneArgumentParser("teamAdvancement", { id ->
            TeamAdvancementEvent(resolveAdvancement(id))
        })
    )

    val PLAYER_KILLED = register(
        SimpleDecoder("playerKilled") {
            object : BukkitEventBasedTournamentEvent<PlayerDeathEvent, PlayerKilled>(
                PlayerDeathEvent::class,
                PlayerDeathEvent::getHandlerList
            ) {
                override fun subscribe(tournament: Tournament): TournamentEventInstance<PlayerKilled> {
                    return subscribeBukkit(tournament) { event, callback ->
                        val sourcePlayer =
                            tournament.getPlayer(PluginPlayer.get(event.player.killer ?: return@subscribeBukkit))
                                ?: return@subscribeBukkit
                        val targetPlayer =
                            tournament.getPlayer(PluginPlayer.get(event.player)) ?: return@subscribeBukkit
                        callback(PlayerKilled(sourcePlayer, targetPlayer))
                    }
                }
            }
        }
    )

    val PLAYER_TICK = register(
        SimpleDecoder("playerTick") {
            PlayerTickEvent(1)
        }
    )

    val PLAYER_TICK_WITH_PERIOD = register(
        OneArgumentParser("playerTick", { period ->
            PlayerTickEvent(period.toInt())
        })
    )

    private fun lifecycleEvent(
        subscribe: Tournament.((Any?) -> Unit) -> Unit,
        unsubscribe: Tournament.((Any?) -> Unit) -> Unit
    ): TournamentEvent<Any?> {
        return LambdaTournamentEvent(
            subscribeAction = { currentTournament, callback ->
                val listener: (Any?) -> Unit = { callback(null) }
                currentTournament.subscribe(listener)
                listener
            },
            unsubscribeAction = { currentTournament, _, listener ->
                currentTournament.unsubscribe(listener)
            }
        )
    }

    private fun resolveAdvancement(id: String): Advancement {
        val key = requireNotNull(NamespacedKey.fromString(id)) {
            "Invalid advancement key: $id"
        }
        return requireNotNull(Bukkit.getAdvancement(key)) {
            "Unknown advancement: $id"
        }
    }
}
