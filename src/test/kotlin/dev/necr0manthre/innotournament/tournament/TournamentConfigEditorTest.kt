package dev.necr0manthre.innotournament.tournament

import org.bukkit.GameMode
import org.bukkit.NamespacedKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class TournamentConfigEditorTest {
    @TempDir lateinit var temp: Path
    private val repository = TournamentConfigRepository()
    private val editor = TournamentConfigEditor(repository)

    private fun sampleSettings() = TournamentSettings(
        BlockPosition(1, 2, 3), NamespacedKey.minecraft("overworld"),
        BlockPosition(4, 5, 6), NamespacedKey.minecraft("the_nether"),
        NamespacedKey.minecraft("start"), BlockPosition(7, 8, 9),
        GameMode.ADVENTURE, Path.of("missing-pre-events.json"), Path.of("invalid-main-events.json"),
        listOf(-0.0, 0.475, -3.0), -0.0f
    )

    private fun settingsWith(
        source: TournamentSettings,
        preSpawn: BlockPosition = source.tournamentPreSpawn,
        preSpawnDimension: NamespacedKey = source.tournamentPreSpawnDimension,
        spawn: BlockPosition = source.tournamentSpawn,
        spawnDimension: NamespacedKey = source.tournamentSpawnDimension,
        startBoxStructure: NamespacedKey = source.startBoxStructureResourceLocation,
        startBoxPosition: BlockPosition = source.startBoxPos,
    ) = TournamentSettings(
        preSpawn, preSpawnDimension, spawn, spawnDimension, startBoxStructure, startBoxPosition,
        source.preStartGameMode, source.preEventsPath, source.mainEventsPath,
        source.teamScoreModifiers, source.lastTeamScore
    )

    @Test fun eachTypedEditChangesOnlyItsFieldWithoutLoadingEventFiles() {
        val cases = listOf(
            TournamentConfigEdit.PreSpawn(BlockPosition(-10, 20, 30)) to
                { s: TournamentSettings -> settingsWith(s, preSpawn = BlockPosition(-10, 20, 30)) },
            TournamentConfigEdit.PreSpawnDimension(NamespacedKey.minecraft("the_end")) to
                { s: TournamentSettings -> settingsWith(s, preSpawnDimension = NamespacedKey.minecraft("the_end")) },
            TournamentConfigEdit.Spawn(BlockPosition(40, -50, 60)) to
                { s: TournamentSettings -> settingsWith(s, spawn = BlockPosition(40, -50, 60)) },
            TournamentConfigEdit.SpawnDimension(NamespacedKey.minecraft("overworld")) to
                { s: TournamentSettings -> settingsWith(s, spawnDimension = NamespacedKey.minecraft("overworld")) },
            TournamentConfigEdit.StartBoxStructure(NamespacedKey.minecraft("other")) to
                { s: TournamentSettings -> settingsWith(s, startBoxStructure = NamespacedKey.minecraft("other")) },
            TournamentConfigEdit.StartBoxPosition(BlockPosition(70, 80, -90)) to
                { s: TournamentSettings -> settingsWith(s, startBoxPosition = BlockPosition(70, 80, -90)) }
        )

        for ((edit, expectedFrom) in cases) {
            val path = temp.resolve("${edit::class.simpleName}.json")
            val original = sampleSettings()
            repository.writeSettings(path, original)

            editor.apply(path, edit)

            assertEquals(expectedFrom(original), repository.readSettings(path), edit.toString())
        }
    }

    @Test fun everyApplyReadsTheCurrentFileAndIgnoresInvalidEventFiles() {
        val path = temp.resolve("selected.json")
        val original = sampleSettings()
        repository.writeSettings(path, original)

        editor.apply(path, TournamentConfigEdit.PreSpawn(BlockPosition(10, 11, 12)))

        val externallyUpdated = settingsWith(repository.readSettings(path), spawn = BlockPosition(13, 14, 15))
        repository.writeSettings(path, externallyUpdated)
        Files.writeString(temp.resolve("missing-pre-events.json"), "{")
        Files.writeString(temp.resolve("invalid-main-events.json"), "not-json")

        editor.apply(path, TournamentConfigEdit.StartBoxPosition(BlockPosition(16, 17, 18)))

        assertEquals(
            settingsWith(externallyUpdated, startBoxPosition = BlockPosition(16, 17, 18)),
            repository.readSettings(path)
        )
    }

    @Test fun invalidSettingsAreRejectedBeforeAnyWrite() {
        val path = temp.resolve("invalid.json")
        for (invalid in listOf("{", "{}")) {
            Files.writeString(path, invalid)
            val originalBytes = Files.readAllBytes(path)

            assertThrows(TournamentConfigException::class.java) {
                editor.apply(path, TournamentConfigEdit.Spawn(BlockPosition(1, 2, 3)))
            }

            assertEquals(originalBytes.toList(), Files.readAllBytes(path).toList())
        }
    }
}
