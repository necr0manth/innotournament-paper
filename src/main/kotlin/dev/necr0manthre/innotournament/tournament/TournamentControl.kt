package dev.necr0manthre.innotournament.tournament

import java.nio.file.Path

/** Owns the selected definition path and one current run for its caller. */
class TournamentControl(
    private val repository: TournamentConfigRepository,
    private val createTournament: (LoadedTournamentDefinition) -> Tournament
) {
    private var selectedPathValue: Path? = null
    var current: Tournament? = null
        private set

    val selectedPath: Path? get() = selectedPathValue

    /** Select a definition for the next prepare; this does not affect the current run. */
    fun select(path: Path) {
        selectedPathValue = path.toAbsolutePath().normalize()
    }

    fun prepare(): Tournament {
        val path = checkNotNull(selectedPathValue) { "No tournament definition selected" }
        removeCurrent(RemovalReason.REPLACEMENT)

        val definition = repository.loadForRun(path)
        val tournament = createTournament(definition)
        current = tournament
        try {
            tournament.prepare()
        } catch (failure: Throwable) {
            removeFailed(tournament)
            throw failure
        }
        return tournament
    }

    fun start() {
        val tournament = checkNotNull(current) { "No current tournament" }
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
        val previous = current ?: return
        previous.remove(reason)
        if (current === previous) current = null
    }

    private fun removeFailed(tournament: Tournament) {
        tournament.remove(RemovalReason.ERROR)
        if (current === tournament) current = null
    }
}
