package dev.necr0manthre.innotournament.tournament

import dev.necr0manthre.innotournament.tournament.events.EventLoader
import dev.necr0manthre.innotournament.tournament.events.EventPlan
import dev.necr0manthre.innotournament.tournament.events.EventPlanLoadException
import dev.necr0manthre.innotournament.util.AtomicTextFile
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** File operations on a caller-selected definition; selection and lifecycle belong to CORE. */
class TournamentConfigRepository(
    private val codec: TournamentConfigCodec = TournamentConfigCodec(),
    private val files: AtomicTextFile = AtomicTextFile()
) {
    /** Decode only the eleven typed settings. This does not open either event file. */
    fun readSettings(path: Path): TournamentSettings {
        val selected = path.toAbsolutePath().normalize()
        val json = try {
            requireRegularFile(selected, "tournament settings")
            Files.newBufferedReader(selected).use { it.readText() }
        } catch (e: IOException) {
            throw TournamentConfigException("Cannot read tournament settings '$selected': ${e.message}", e)
        } catch (e: SecurityException) {
            throw TournamentConfigException("Cannot read tournament settings '$selected': access denied", e)
        }
        return try {
            codec.decode(json)
        } catch (e: TournamentConfigException) {
            throw TournamentConfigException("Invalid tournament settings '$selected': ${e.message}", e)
        }
    }

    /** Parse and bind BOTH event files before publishing one immutable definition. */
    fun loadForRun(path: Path): LoadedTournamentDefinition {
        val selected = path.toAbsolutePath().normalize()
        val settings = readSettings(selected)
        val directory = selected.parent
        val prePlan = loadPlan(directory.resolve(settings.preEventsPath), selected, "pre-start")
        val mainPlan = loadPlan(directory.resolve(settings.mainEventsPath), selected, "main")
        return LoadedTournamentDefinition(settings, prePlan, mainPlan)
    }

    /** Typed settings-only write; event file validity is deliberately not consulted. */
    fun writeSettings(path: Path, settings: TournamentSettings) {
        val json = codec.encode(settings)
        files.replace(path, json)
    }

    private fun loadPlan(target: Path, selected: Path, name: String): EventPlan {
        val file = target.normalize()
        try {
            requireRegularFile(file, "$name event file")
            return EventLoader.loadEvents(file)
        } catch (e: IOException) {
            throw EventPlanLoadException("Cannot load $name event file '$file' for tournament '$selected': ${e.message}", e)
        } catch (e: EventPlanLoadException) {
            throw EventPlanLoadException("Invalid $name event file '$file' for tournament '$selected': ${e.message}", e)
        } catch (e: SecurityException) {
            throw EventPlanLoadException("Cannot load $name event file '$file' for tournament '$selected': access denied", e)
        }
    }

    private fun requireRegularFile(path: Path, description: String) {
        // Follow valid file symlinks, but reject missing, broken, directory and special targets.
        if (!Files.isRegularFile(path)) {
            throw IOException("$description '$path' is missing or is not a regular file")
        }
    }
}
