package dev.necr0manthre.innotournament.tournament

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.necr0manthre.innotournament.tournament.events.EventLoader
import dev.necr0manthre.innotournament.teams.TeamManager
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.World
import java.nio.file.Files
import java.nio.file.Path

class TournamentData(
    var tournamentSpawn: Location = defaultLocation(),
    var tournamentPreSpawn: Location = defaultLocation(),
    var startBoxStructureResourceLocation: NamespacedKey? = null,
    var startBoxPos: Location = defaultLocation(),
    var preStartGameMode: GameMode = GameMode.SURVIVAL,
    var preEventsPath: Path = Path.of(""),
    var mainEventsPath: Path = Path.of(""),
    var teamScoreModifiers: DoubleArray = doubleArrayOf(2.43, 1.45, 1.0, 0.7241170, 0.4530344, 0.3645),
    var lastTeamScore: Float = 0f
) : Cloneable {

    fun createTournament(tournamentDataPath: Path, teamManager: TeamManager): Tournament {
        val parent = tournamentDataPath.parent ?: Path.of("")
        return Tournament(teamManager,
            tournamentSpawn,
            tournamentPreSpawn,
            startBoxStructureResourceLocation,
            startBoxPos,
            preStartGameMode,
            EventLoader.loadEvents(parent.resolve(preEventsPath)),
            EventLoader.loadEvents(parent.resolve(mainEventsPath)),
            teamScoreModifiers,
            lastTeamScore
        )
    }

    public override fun clone(): TournamentData {
        return TournamentData(
            tournamentSpawn,
            tournamentPreSpawn,
            startBoxStructureResourceLocation,
            startBoxPos,
            preStartGameMode,
            preEventsPath,
            mainEventsPath,
            teamScoreModifiers.clone(),
            lastTeamScore
        )
    }

    fun toJson(): JsonObject {
        return JsonObject().apply {
            add("tournamentSpawn", tournamentSpawn.toJson())
            addProperty("tournamentSpawnDimension", tournamentSpawn.dimensionString())
            add("tournamentPreSpawn", tournamentPreSpawn.toJson())
            addProperty("tournamentPreSpawnDimension", tournamentPreSpawn.dimensionString())
            addProperty("startBoxStructureResourceLocation", startBoxStructureResourceLocation?.toString() ?: "")
            add("startBoxPos", startBoxPos.toJson())
            addProperty("preStartGameMode", preStartGameMode.name)
            addProperty("preEventsPath", preEventsPath.toString())
            addProperty("mainEventsPath", mainEventsPath.toString())
            add("teamScoreModifiers", teamScoreModifiers.fold(com.google.gson.JsonArray()) { array, value ->
                array.add(value)
                array
            })
            addProperty("lastTeamScore", lastTeamScore)
        }
    }

    fun save(path: Path) {
        Files.createDirectories(path.parent)
        Files.writeString(path, GsonBuilder().setPrettyPrinting().create().toJson(toJson()))
    }

    companion object {
        fun load(path: Path): TournamentData {
            val json = JsonParser.parseReader(Files.newBufferedReader(path)).asJsonObject
            return TournamentData().apply {
                val spawnWorld = json.getString("tournamentSpawnDimension")?.toWorld() ?: tournamentSpawn.world
                val preSpawnWorld = json.getString("tournamentPreSpawnDimension")?.toWorld() ?: tournamentPreSpawn.world
                tournamentSpawn = json.getObject("tournamentSpawn")?.toLocation(spawnWorld) ?: tournamentSpawn
                tournamentPreSpawn = json.getObject("tournamentPreSpawn")?.toLocation(preSpawnWorld) ?: tournamentPreSpawn
                startBoxStructureResourceLocation = json.getString("startBoxStructureResourceLocation")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(NamespacedKey::fromString)
                startBoxPos = json.getObject("startBoxPos")?.toLocation(preSpawnWorld) ?: startBoxPos
                preStartGameMode = json.getString("preStartGameMode")
                    ?.let { runCatching { GameMode.valueOf(it.uppercase()) }.getOrNull() }
                    ?: preStartGameMode
                preEventsPath = json.getString("preEventsPath")?.let(Path::of) ?: preEventsPath
                mainEventsPath = json.getString("mainEventsPath")?.let(Path::of) ?: mainEventsPath
                json.getAsJsonArray("teamScoreModifiers")?.let { array ->
                    teamScoreModifiers = array.map { it.asDouble }.toDoubleArray()
                }
                lastTeamScore = json.get("lastTeamScore")?.asFloat ?: lastTeamScore
            }
        }

        private fun JsonObject.getObject(name: String): JsonObject? {
            return get(name)?.takeIf { it.isJsonObject }?.asJsonObject
        }

        private fun JsonObject.getString(name: String): String? {
            return get(name)?.takeIf { it.isJsonPrimitive }?.asString
        }

        private fun JsonObject.toLocation(world: World?): Location {
            return Location(
                world,
                (get("x")?.asDouble ?: 0.0),
                (get("y")?.asDouble ?: 0.0),
                (get("z")?.asDouble ?: 0.0)
            )
        }

        private fun Location.toJson(): JsonObject {
            return JsonObject().apply {
                addProperty("x", blockX)
                addProperty("y", blockY)
                addProperty("z", blockZ)
            }
        }

        private fun Location.dimensionString(): String {
            return world?.key?.toString() ?: NamespacedKey.minecraft("overworld").toString()
        }

        private fun String.toWorld(): World? {
            return NamespacedKey.fromString(this)?.let(Bukkit::getWorld)
        }

        private fun defaultLocation(): Location {
            return Location(defaultWorld(), 0.0, 0.0, 0.0)
        }

        private fun defaultWorld(): World? {
            return Bukkit.getWorld(NamespacedKey.minecraft("overworld"))
                ?: Bukkit.getWorlds().firstOrNull { it.environment == World.Environment.NORMAL }
                ?: Bukkit.getWorlds().firstOrNull()
        }
    }
}
