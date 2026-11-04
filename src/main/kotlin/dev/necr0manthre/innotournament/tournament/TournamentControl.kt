package dev.necr0manthre.innotournament.tournament

import java.nio.file.Path

/** Owns the selected definition path and one current run for its caller. */
class TournamentControl(
    private val repository: TournamentConfigRepository,
    private val createTournament: (LoadedTournamentDefinition) -> Tournament
) {
    private var selectedPathValue: Path? = null
    private var currentValue: Tournament? = null

    val selectedPath: Path? get() = selectedPathValue
    val current: Tournament? get() = currentValue

    /** Select a definition for the next prepare; this does not affect the current run. */
    fun select(path: Path) {
        selectedPathValue = path.toAbsolutePath().normalize()
    }

    fun prepare(): Tournament {
        val path = checkNotNull(selectedPathValue) { "No tournament definition selected" }
        removeCurrent(RemovalReason.REPLACEMENT)

        val definition = repository.loadForRun(path)
        val tournament = createTournament(definition)
        currentValue = tournament
        try {
            tournament.prepare()
        } catch (failure: Throwable) {
            removeFailed(tournament)
            throw failure
        }
        return tournament
    }

    fun start() {
        val tournament = checkNotNull(currentValue) { "No current tournament" }
        check(tournament.phase == TournamentPhase.PREPARED) { "Tournament is not prepared" }
        try {
            tournament.start()
        } catch (failure: Throwable) {
            removeFailed(tournament)
            throw failure
        }
    }

    fun stop(reason: RemovalReason = RemovalReason.CANCELLED) {
        removeCurrent(reason)
    }

    private fun removeCurrent(reason: RemovalReason) {
        val previous = currentValue ?: return
        previous.remove(reason)
        if (currentValue === previous) currentValue = null
    }

    private fun removeFailed(tournament: Tournament) {
        tournament.remove(RemovalReason.ERROR)
        if (currentValue === tournament) currentValue = null
    }
}
