package dev.necr0manthre.innotournament.tournament

import com.google.gson.JsonParser
import dev.necr0manthre.innotournament.init.InnoTournamentEventHandlers
import dev.necr0manthre.innotournament.init.InnoTournamentEvents
import dev.necr0manthre.innotournament.tournament.events.EventPlanLoadException
import org.bukkit.GameMode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions

class TournamentConfigRepositoryTest {
    @TempDir lateinit var temp: Path
    private val repository = TournamentConfigRepository()

    companion object {
        @JvmStatic @BeforeAll fun registerEventCatalog() {
            InnoTournamentEvents.init()
            InnoTournamentEventHandlers.init()
        }
    }

    private fun originalSettings(): String =
        requireNotNull(javaClass.classLoader.getResourceAsStream("tournament/original-tournament.json"))
            .bufferedReader().use { it.readText() }

    private fun configIn(directory: Path = temp): Path {
        Files.createDirectories(directory)
        return directory.resolve("tournament.json").also { Files.writeString(it, originalSettings()) }
    }

    @Test fun nestedSelectedDirectoryLoadsBothFullyValidatedPlans() {
        val directory = temp.resolve("configs/sub")
        val config = configIn(directory)
        Files.writeString(directory.resolve("preEvents.json"), """{"onPrepare": ["log", "log"]}""")
        Files.writeString(directory.resolve("mainEvents.json"), """{"onStart": ["log", "log"], "onEnd": "log"}""")

        val settings = repository.readSettings(config)
        assertEquals(BlockPosition(-805, 83, 236), settings.tournamentPreSpawn)
        assertEquals(GameMode.SURVIVAL, settings.preStartGameMode)
        assertEquals(Path.of("preEvents.json"), settings.preEventsPath)
        val loaded = repository.loadForRun(config)
        assertEquals(settings, loaded.settings)
        assertEquals(1, loaded.prePlan.entries.size)
        assertEquals(2, loaded.mainPlan.entries.size)

        // Plans are snapshots, not live views over the input JSON or the backing entry list.
        assertThrows(UnsupportedOperationException::class.java) {
            (loaded.prePlan.entries as MutableList<*>).clear()
        }
        Files.writeString(directory.resolve("mainEvents.json"), "not json")
        assertEquals(2, loaded.mainPlan.entries.size)
        assertThrows(EventPlanLoadException::class.java) { repository.loadForRun(config) }
    }

    private fun symbolicLink(link: Path, target: Path) {
        try {
            Files.createSymbolicLink(link, target)
        } catch (e: UnsupportedOperationException) {
            assumeTrue(false, "Symbolic links are unsupported")
        } catch (e: FileSystemException) {
            assumeTrue(false, "Cannot create symbolic link: ${e.message}")
        } catch (e: SecurityException) {
            assumeTrue(false, "Cannot create symbolic link: ${e.message}")
        }
    }

    @Test fun linkedSettingsReadPlansRelativeToSelectedAliasNotItsTarget() {
        val realDirectory = temp.resolve("real")
        val realConfig = configIn(realDirectory)
        val selectedDirectory = temp.resolve("selected")
        Files.createDirectories(selectedDirectory)
        val selected = selectedDirectory.resolve("chosen.json")
        symbolicLink(selected, selectedDirectory.relativize(realConfig))
        Files.writeString(selectedDirectory.resolve("preEvents.json"), """{"onPrepare":"log"}""")
        Files.writeString(selectedDirectory.resolve("mainEvents.json"), """{"onStart":"log", "onEnd":"log"}""")

        assertEquals(repository.readSettings(realConfig), repository.readSettings(selected))
        val loaded = repository.loadForRun(selected)
        assertEquals(1, loaded.prePlan.entries.size)
        assertEquals(2, loaded.mainPlan.entries.size)
    }

    @Test fun linkedPreAndMainFilesLoadCompleteDefinition() {
        val config = configIn()
        val shared = temp.resolve("shared")
        Files.createDirectory(shared)
        val preTarget = shared.resolve("pre.json")
        val mainTarget = shared.resolve("main.json")
        Files.writeString(preTarget, """{"onPrepare":"log"}""")
        Files.writeString(mainTarget, """{"onStart":"log", "onEnd":"log"}""")
        symbolicLink(temp.resolve("preEvents.json"), preTarget)
        symbolicLink(temp.resolve("mainEvents.json"), mainTarget)

        val loaded = repository.loadForRun(config)
        assertEquals(1, loaded.prePlan.entries.size)
        assertEquals(2, loaded.mainPlan.entries.size)
    }

    @Test fun brokenDirectoryAndMalformedLinkTargetsRemainContextualErrors() {
        val brokenSettings = temp.resolve("missing-settings-link.json")
        symbolicLink(brokenSettings, Path.of("missing-settings.json"))
        val settingsError = assertThrows(TournamentConfigException::class.java) {
            repository.readSettings(brokenSettings)
        }
        assertTrue(settingsError.message!!.contains(brokenSettings.toString()))

        val directory = temp.resolve("directory")
        Files.createDirectory(directory)
        val directorySettings = temp.resolve("directory-settings-link.json")
        symbolicLink(directorySettings, directory)
        assertTrue(assertThrows(TournamentConfigException::class.java) {
            repository.readSettings(directorySettings)
        }.message!!.contains("not a regular file"))
        val malformedSettings = temp.resolve("malformed-settings.json")
        Files.writeString(malformedSettings, "{")
        val malformedAlias = temp.resolve("malformed-settings-link.json")
        symbolicLink(malformedAlias, malformedSettings)
        val malformedError = assertThrows(TournamentConfigException::class.java) {
            repository.readSettings(malformedAlias)
        }
        assertTrue(malformedError.message!!.contains(malformedAlias.toString()))

        val config = configIn()
        Files.writeString(temp.resolve("preEvents.json"), "{}")
        val main = temp.resolve("mainEvents.json")
        val badTarget = temp.resolve("missing-main.json")
        symbolicLink(main, badTarget)
        fun mainFailure(): EventPlanLoadException = assertThrows(EventPlanLoadException::class.java) {
            repository.loadForRun(config)
        }.also { failure ->
            assertTrue(failure.message!!.contains("main event file"))
            assertTrue(failure.message!!.contains(main.toString()))
            assertTrue(failure.message!!.contains(config.toString()))
        }
        assertTrue(mainFailure().message!!.contains("not a regular file"))
        Files.writeString(badTarget, "{")
        mainFailure()
        Files.delete(main)
        symbolicLink(main, directory)
        assertTrue(mainFailure().message!!.contains("not a regular file"))
    }

    @Test fun emptyPlansAndAbsoluteEventPathAreAcceptedWhenExplicitlyConfigured() {
        val config = configIn()
        val absolutePre = temp.resolve("explicit-pre.json")
        Files.writeString(absolutePre, "{}")
        Files.writeString(temp.resolve("mainEvents.json"), """{"onStart":[]}""")
        val source = JsonParser.parseString(originalSettings()).asJsonObject
        source.addProperty("preEventsPath", absolutePre.toString())
        Files.writeString(config, source.toString())

        val result = repository.loadForRun(config)
        assertEquals(0, result.prePlan.entries.size)
        assertEquals(1, result.mainPlan.entries.size)
    }

    @Test fun settingsOnlyReadWriteDoesNotTouchEventFilesAndWritesOriginalSchema() {
        val config = configIn()
        val document = JsonParser.parseString(Files.readString(config)).asJsonObject
        document.addProperty("unrecognizedTypedCodecField", "ignored")
        Files.writeString(config, document.toString())
        // Neither named event file exists; editor-style access must still work.
        val before = repository.readSettings(config)
        assertEquals(BlockPosition(-815, 82, 226), before.startBoxPos)
        assertThrows(EventPlanLoadException::class.java) { repository.loadForRun(config) }

        val changed = TournamentSettings(
            before.tournamentPreSpawn, before.tournamentPreSpawnDimension,
            before.tournamentSpawn, before.tournamentSpawnDimension,
            before.startBoxStructureResourceLocation, BlockPosition(1, 2, 3),
            before.preStartGameMode, before.preEventsPath, before.mainEventsPath,
            before.teamScoreModifiers, before.lastTeamScore
        )
        repository.writeSettings(config, changed)
        val saved = JsonParser.parseString(Files.readString(config)).asJsonObject
        assertEquals(11, saved.size())
        assertFalse(saved.has("unrecognizedTypedCodecField"))
        assertEquals(listOf(1, 2, 3), saved.getAsJsonArray("startBoxPos").map { it.asInt })
        assertEquals("survival", saved.get("preStartGameMode").asString)
        assertEquals(changed, repository.readSettings(config))
        assertThrows(EventPlanLoadException::class.java) { repository.loadForRun(config) }
    }

    @Test fun explicitEmptyEventPathIsAnErrorNotAnEmptyPlan() {
        val config = configIn()
        Files.writeString(temp.resolve("mainEvents.json"), "{}")
        val document = JsonParser.parseString(Files.readString(config)).asJsonObject
        document.addProperty("preEventsPath", "")
        Files.writeString(config, document.toString())
        assertEquals(Path.of(""), repository.readSettings(config).preEventsPath)
        val failure = assertThrows(EventPlanLoadException::class.java) { repository.loadForRun(config) }
        assertTrue(failure.message!!.contains("pre-start"))
        assertTrue(failure.message!!.contains(config.toString()))
    }

    @Test fun malformedFilesAreRejectedWithStageFileAndActionContext() {
        val config = configIn()
        val pre = temp.resolve("preEvents.json")
        val main = temp.resolve("mainEvents.json")
        Files.writeString(pre, "{}")
        Files.writeString(main, "{}")

        // No successful empty fallback for missing, directory, malformed, unknown or invalid actions.
        Files.delete(main)
        assertTrue(assertThrows(EventPlanLoadException::class.java) { repository.loadForRun(config) }
            .message!!.contains("mainEvents.json"))
        Files.createDirectory(main)
        assertTrue(assertThrows(EventPlanLoadException::class.java) { repository.loadForRun(config) }
            .message!!.contains("not a regular file"))
        Files.delete(main)
        for (bad in listOf("{", "[]", "null", """{"undefined":[]}""", """{"onStart":42}""",
            """{"onStart":["log", false]}""")) {
            Files.writeString(main, bad)
            val failure = assertThrows(EventPlanLoadException::class.java) { repository.loadForRun(config) }
            assertTrue(failure.message!!.contains("mainEvents.json"), bad)
        }
        Files.writeString(main, """{"onStart":["log", "mystery bad"]}""")
        val badAction = assertThrows(EventPlanLoadException::class.java) { repository.loadForRun(config) }
        assertTrue(badAction.message!!.contains("mainEvents.json"))
        assertTrue(badAction.message!!.contains("onStart"))
        assertTrue(badAction.message!!.contains("action[1]"))
        assertTrue(badAction.message!!.contains("mystery bad"))
    }

    @Test fun invalidSettingsHaveFileAndFieldContext() {
        val config = configIn()
        val json = JsonParser.parseString(Files.readString(config)).asJsonObject
        json.remove("mainEventsPath")
        Files.writeString(config, json.toString())
        val missing = assertThrows(TournamentConfigException::class.java) { repository.readSettings(config) }
        assertTrue(missing.message!!.contains(config.toString()))
        assertTrue(missing.message!!.contains("mainEventsPath"))
        Files.writeString(config, "[]")
        assertTrue(assertThrows(TournamentConfigException::class.java) { repository.readSettings(config) }
            .message!!.contains(config.toString()))
        Files.delete(config)
        assertTrue(assertThrows(TournamentConfigException::class.java) { repository.readSettings(config) }
            .message!!.contains("not a regular file"))
    }

    @Test fun atomicSettingsWritePreservesExistingPosixMode() {
        val config = configIn()
        assumeTrue(Files.getFileAttributeView(config, PosixFileAttributeView::class.java) != null,
            "POSIX file permissions are not available on this filesystem")
        val settings = repository.readSettings(config)
        for (mode in listOf("rw-r--r--", "rw-------")) {
            val permissions = PosixFilePermissions.fromString(mode)
            Files.setPosixFilePermissions(config, permissions)
            repository.writeSettings(config, settings)
            assertEquals(permissions, Files.getPosixFilePermissions(config))
            assertEquals(settings, repository.readSettings(config))
        }
    }

    @Test fun failedAtomicReplacementKeepsOriginalAndCleansTemp() {
        val config = configIn()
        val settings = repository.readSettings(config)
        val original = Files.readString(config)
        val directoryAsTarget = temp.resolve("nonempty-directory")
        Files.createDirectory(directoryAsTarget)
        Files.writeString(directoryAsTarget.resolve("keep"), "unchanged")
        assertThrows(IOException::class.java) { repository.writeSettings(directoryAsTarget, settings) }
        assertEquals(original, Files.readString(config))
        assertEquals("unchanged", Files.readString(directoryAsTarget.resolve("keep")))
        Files.list(temp).use { children ->
            assertFalse(children.anyMatch { it.fileName.toString().endsWith(".tmp") })
        }
    }
}
