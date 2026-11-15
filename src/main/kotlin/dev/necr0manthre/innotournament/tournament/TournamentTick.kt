package dev.necr0manthre.innotournament.tournament

import java.util.Collections

class TournamentTick(
    val stage: TournamentPhase,
    val stageTick: Long,
    players: List<TournamentPlayer>
) {
    val players: List<TournamentPlayer> = Collections.unmodifiableList(ArrayList(players))

    init {
        require(stage == TournamentPhase.PREPARED || stage == TournamentPhase.STARTED) {
            "Tournament ticks are only available in PREPARED or STARTED"
        }
        require(stageTick > 0) { "Tournament stage tick must be positive" }
    }
}
