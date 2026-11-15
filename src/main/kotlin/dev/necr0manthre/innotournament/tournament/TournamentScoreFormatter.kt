package dev.necr0manthre.innotournament.tournament

import java.util.Locale

/** Formats scores for the tournament's sidebar and result announcement. */
object TournamentScoreFormatter {
    fun format(score: Double): String = String.format(Locale.ROOT, "%.2f", score)
}
