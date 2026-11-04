package dev.necr0manthre.innotournament.tournament

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TournamentTickTest {
    @Test
    fun snapshotsPlayersAsAnImmutableList() {
        val source = mutableListOf<TournamentPlayer>()
        val tick = TournamentTick(TournamentPhase.PREPARED, 1, source)

        source.clear()

        assertEquals(emptyList<TournamentPlayer>(), tick.players)
        assertThrows(UnsupportedOperationException::class.java) {
            (tick.players as MutableList<TournamentPlayer>).clear()
        }
    }
}
