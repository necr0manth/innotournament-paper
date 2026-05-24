package dev.necr0manthre.innotournament.init

import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.TournamentPlayer
import dev.necr0manthre.innotournament.tournament.events.AbstractTournamentEvent
import dev.necr0manthre.innotournament.tournament.events.BukkitEventBasedTournamentEvent
import dev.necr0manthre.innotournament.tournament.events.ITournamentEventFactory
import dev.necr0manthre.innotournament.tournament.events.LambdaTournamentEvent
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
    private fun <E : AbstractTournamentEvent<*>, T : IParser<String, ITournamentEventFactory<E>>> register(parser: T): T {
        return TournamentEventParserRegistry.register(String::class, parser)
    }

    fun init() {
    }

    val TIME = register(
        OneArgumentParser("time", { time -> TimeEvent.factory(time.toLong()) })
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
            ITournamentEventFactory { tournament -> AdvancementEvent(resolveAdvancement(id), tournament) }
        })
    )

    val TEAM_ADVANCEMENT = register(
        OneArgumentParser("teamAdvancement", { id ->
            ITournamentEventFactory { tournament -> TeamAdvancementEvent(resolveAdvancement(id), tournament) }
        })
    )

    val PLAYER_KILLED = register(
        SimpleDecoder("playerKilled") {
            ITournamentEventFactory { tournament ->
                object : BukkitEventBasedTournamentEvent<PlayerDeathEvent, PlayerKilled>(
                    PlayerDeathEvent::class,
                    PlayerDeathEvent::getHandlerList,
                    tournament
                ) {
                    override fun handle(event: PlayerDeathEvent, callback: (PlayerKilled) -> Unit) {
                        val source = event.entity.killer ?: return
                        val target = TournamentPlayer.get(event.entity)
                        if (target.lives > 0) {
                            callback(PlayerKilled(TournamentPlayer.get(source), target))
                        }
                    }
                }
            }
        }
    )

    val PLAYER_TICK = register(
        SimpleDecoder("playerTick") {
            ITournamentEventFactory { tournament -> PlayerTickEvent(tournament, 1) }
        }
    )

    val PLAYER_TICK_WITH_PERIOD = register(
        OneArgumentParser("playerTick", { period ->
            ITournamentEventFactory { tournament -> PlayerTickEvent(tournament, period.toInt()) }
        })
    )

    private fun lifecycleEvent(
        subscribe: Tournament.((Any?) -> Unit) -> Unit,
        unsubscribe: Tournament.((Any?) -> Unit) -> Unit
    ): ITournamentEventFactory<LambdaTournamentEvent<Any?, (Any?) -> Unit>> {
        return ITournamentEventFactory { tournament ->
            LambdaTournamentEvent(
                subscribeAction = { currentTournament, callback ->
                    val listener: (Any?) -> Unit = { callback(null) }
                    currentTournament.subscribe(listener)
                    listener
                },
                unsubscribeAction = { currentTournament, _, listener ->
                    currentTournament.unsubscribe(listener)
                },
                tournament = tournament
            )
        }
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
