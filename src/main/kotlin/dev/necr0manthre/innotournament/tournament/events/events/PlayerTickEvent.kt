package dev.necr0manthre.innotournament.tournament.events.events

import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.TournamentPlayer
import dev.necr0manthre.innotournament.tournament.TournamentPhase
import dev.necr0manthre.innotournament.tournament.TournamentTick
import dev.necr0manthre.innotournament.tournament.events.TournamentTickBasedEvent
import dev.necr0manthre.innotournament.tournament.events.ValueType
import dev.necr0manthre.innotournament.tournament.events.TournamentEventInstance
import dev.necr0manthre.innotournament.tournament.events.event_data.PlayerHolder

class PlayerTickEvent(val period: Int) :
    TournamentTickBasedEvent<PlayerHolder>(ValueType.of(PlayerHolder::class)) {
    override fun subscribe(tournament: Tournament): TournamentEventInstance<PlayerHolder> {
        val eligibleFrames = mutableMapOf<TournamentPlayer, Int>()
        return subscribeTicks(tournament) { tick: TournamentTick, callback ->
            if (tick.stage != TournamentPhase.STARTED) return@subscribeTicks
            for (player in tick.players) {
                if (!isCurrent(tournament, tick, player)) continue
                val count = (eligibleFrames[player] ?: 0) + 1
                eligibleFrames[player] = count
                if ((count - 1) % period != 0) continue
                if (!isCurrent(tournament, tick, player)) continue
                callback(PlayerHolder(player))
            }
        }
    }

    private fun isCurrent(tournament: Tournament, tick: TournamentTick, player: TournamentPlayer): Boolean =
        tournament.phase == tick.stage && player.prepared && player.pluginPlayer.player != null &&
            tournament.getPlayer(player.pluginPlayer) === player
}
