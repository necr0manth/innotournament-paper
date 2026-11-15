package dev.necr0manthre.innotournament.tournament

import org.bukkit.NamespacedKey
import java.nio.file.Path

sealed interface TournamentConfigEdit {
    data class PreSpawn(val position: BlockPosition) : TournamentConfigEdit
    data class PreSpawnDimension(val key: NamespacedKey) : TournamentConfigEdit
    data class Spawn(val position: BlockPosition) : TournamentConfigEdit
    data class SpawnDimension(val key: NamespacedKey) : TournamentConfigEdit
    data class StartBoxStructure(val key: NamespacedKey) : TournamentConfigEdit
    data class StartBoxPosition(val position: BlockPosition) : TournamentConfigEdit
}

class TournamentConfigEditor(
    private val configurations: TournamentConfigRepository,
) {
    fun apply(path: Path, edit: TournamentConfigEdit) {
        val current = configurations.readSettings(path)
        val updated = when (edit) {
            is TournamentConfigEdit.PreSpawn -> TournamentSettings(
                edit.position, current.tournamentPreSpawnDimension,
                current.tournamentSpawn, current.tournamentSpawnDimension,
                current.startBoxStructureResourceLocation, current.startBoxPos,
                current.preStartGameMode, current.preEventsPath, current.mainEventsPath,
                current.teamScoreModifiers, current.lastTeamScore
            )
            is TournamentConfigEdit.PreSpawnDimension -> TournamentSettings(
                current.tournamentPreSpawn, edit.key,
                current.tournamentSpawn, current.tournamentSpawnDimension,
                current.startBoxStructureResourceLocation, current.startBoxPos,
                current.preStartGameMode, current.preEventsPath, current.mainEventsPath,
                current.teamScoreModifiers, current.lastTeamScore
            )
            is TournamentConfigEdit.Spawn -> TournamentSettings(
                current.tournamentPreSpawn, current.tournamentPreSpawnDimension,
                edit.position, current.tournamentSpawnDimension,
                current.startBoxStructureResourceLocation, current.startBoxPos,
                current.preStartGameMode, current.preEventsPath, current.mainEventsPath,
                current.teamScoreModifiers, current.lastTeamScore
            )
            is TournamentConfigEdit.SpawnDimension -> TournamentSettings(
                current.tournamentPreSpawn, current.tournamentPreSpawnDimension,
                current.tournamentSpawn, edit.key,
                current.startBoxStructureResourceLocation, current.startBoxPos,
                current.preStartGameMode, current.preEventsPath, current.mainEventsPath,
                current.teamScoreModifiers, current.lastTeamScore
            )
            is TournamentConfigEdit.StartBoxStructure -> TournamentSettings(
                current.tournamentPreSpawn, current.tournamentPreSpawnDimension,
                current.tournamentSpawn, current.tournamentSpawnDimension,
                edit.key, current.startBoxPos,
                current.preStartGameMode, current.preEventsPath, current.mainEventsPath,
                current.teamScoreModifiers, current.lastTeamScore
            )
            is TournamentConfigEdit.StartBoxPosition -> TournamentSettings(
                current.tournamentPreSpawn, current.tournamentPreSpawnDimension,
                current.tournamentSpawn, current.tournamentSpawnDimension,
                current.startBoxStructureResourceLocation, edit.position,
                current.preStartGameMode, current.preEventsPath, current.mainEventsPath,
                current.teamScoreModifiers, current.lastTeamScore
            )
        }
        configurations.writeSettings(path, updated)
    }
}
