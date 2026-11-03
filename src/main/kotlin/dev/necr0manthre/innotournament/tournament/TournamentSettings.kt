package dev.necr0manthre.innotournament.tournament

import org.bukkit.GameMode
import org.bukkit.NamespacedKey
import java.nio.file.Path
import java.util.Collections

/** A block coordinate, independent of any loaded Bukkit world or mutable Location. */
data class BlockPosition(val x: Int, val y: Int, val z: Int)

/**
 * One immutable snapshot of the eleven original tournament JSON settings.
 * World/structure identifiers are syntax-only values; resolving them belongs to CORE.
 */
class TournamentSettings(
    val tournamentPreSpawn: BlockPosition,
    val tournamentPreSpawnDimension: NamespacedKey,
    val tournamentSpawn: BlockPosition,
    val tournamentSpawnDimension: NamespacedKey,
    val startBoxStructureResourceLocation: NamespacedKey,
    val startBoxPos: BlockPosition,
    val preStartGameMode: GameMode,
    val preEventsPath: Path,
    val mainEventsPath: Path,
    teamScoreModifiers: Collection<Double>,
    val lastTeamScore: Float
) {
    val teamScoreModifiers: List<Double> = Collections.unmodifiableList(ArrayList(teamScoreModifiers))

    init {
        require(this.teamScoreModifiers.all(Double::isFinite)) {
            "teamScoreModifiers must contain only finite numbers"
        }
        require(lastTeamScore.isFinite()) { "lastTeamScore must be finite" }
    }

    override fun equals(other: Any?): Boolean = other is TournamentSettings &&
        tournamentPreSpawn == other.tournamentPreSpawn &&
        tournamentPreSpawnDimension == other.tournamentPreSpawnDimension &&
        tournamentSpawn == other.tournamentSpawn &&
        tournamentSpawnDimension == other.tournamentSpawnDimension &&
        startBoxStructureResourceLocation == other.startBoxStructureResourceLocation &&
        startBoxPos == other.startBoxPos &&
        preStartGameMode == other.preStartGameMode &&
        preEventsPath == other.preEventsPath &&
        mainEventsPath == other.mainEventsPath &&
        teamScoreModifiers == other.teamScoreModifiers &&
        lastTeamScore.toBits() == other.lastTeamScore.toBits()

    override fun hashCode(): Int {
        var result = tournamentPreSpawn.hashCode()
        result = 31 * result + tournamentPreSpawnDimension.hashCode()
        result = 31 * result + tournamentSpawn.hashCode()
        result = 31 * result + tournamentSpawnDimension.hashCode()
        result = 31 * result + startBoxStructureResourceLocation.hashCode()
        result = 31 * result + startBoxPos.hashCode()
        result = 31 * result + preStartGameMode.hashCode()
        result = 31 * result + preEventsPath.hashCode()
        result = 31 * result + mainEventsPath.hashCode()
        result = 31 * result + teamScoreModifiers.hashCode()
        result = 31 * result + lastTeamScore.hashCode()
        return result
    }
}
