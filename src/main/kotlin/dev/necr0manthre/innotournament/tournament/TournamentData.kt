package dev.necr0manthre.innotournament.tournament

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.necr0manthre.innotournament.tournament.events.EventLoader
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.Identifier
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import java.nio.file.Files
import java.nio.file.Path

class TournamentData(
    var tournamentSpawn: BlockPos = BlockPos.ZERO,
    var tournamentSpawnDimension: ResourceKey<Level> = Level.OVERWORLD,
    var tournamentPreSpawn: BlockPos = BlockPos.ZERO,
    var tournamentPreSpawnDimension: ResourceKey<Level> = Level.OVERWORLD,
    var startBoxStructureResourceLocation: Identifier? = Identifier.fromNamespaceAndPath("", ""),
    var startBoxPos: BlockPos = BlockPos.ZERO,
    var preStartGameMode: GameType = GameType.SURVIVAL,
    var preEventsPath: Path = Path.of(""),
    var mainEventsPath: Path = Path.of(""),
    var teamScoreModifiers: DoubleArray = doubleArrayOf(2.43, 1.45, 1.0, 0.7241170, 0.4530344, 0.3645),
    var lastTeamScore: Float = 0f
) : Cloneable {

    fun createTournament(tournamentDataPath: Path): Tournament {
        val parent = tournamentDataPath.parent ?: Path.of("")
        return Tournament(
            tournamentSpawn,
            tournamentSpawnDimension,
            tournamentPreSpawn,
            tournamentPreSpawnDimension,
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
            tournamentSpawnDimension,
            tournamentPreSpawn,
            tournamentPreSpawnDimension,
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
            addProperty("tournamentSpawnDimension", tournamentSpawnDimension.identifier().toString())
            add("tournamentPreSpawn", tournamentPreSpawn.toJson())
            addProperty("tournamentPreSpawnDimension", tournamentPreSpawnDimension.identifier().toString())
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
                tournamentSpawn = json.getObject("tournamentSpawn")?.toBlockPos() ?: tournamentSpawn
                tournamentSpawnDimension = json.getString("tournamentSpawnDimension")?.toDimension() ?: tournamentSpawnDimension
                tournamentPreSpawn = json.getObject("tournamentPreSpawn")?.toBlockPos() ?: tournamentPreSpawn
                tournamentPreSpawnDimension =
                    json.getString("tournamentPreSpawnDimension")?.toDimension() ?: tournamentPreSpawnDimension
                startBoxStructureResourceLocation = json.getString("startBoxStructureResourceLocation")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(Identifier::parse)
                startBoxPos = json.getObject("startBoxPos")?.toBlockPos() ?: startBoxPos
                preStartGameMode = json.getString("preStartGameMode")?.let(GameType::byName) ?: preStartGameMode
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

        private fun JsonObject.toBlockPos(): BlockPos {
            return BlockPos(
                get("x")?.asInt ?: 0,
                get("y")?.asInt ?: 0,
                get("z")?.asInt ?: 0
            )
        }

        private fun BlockPos.toJson(): JsonObject {
            return JsonObject().apply {
                addProperty("x", x)
                addProperty("y", y)
                addProperty("z", z)
            }
        }

        private fun String.toDimension(): ResourceKey<Level> {
            return ResourceKey.create(Registries.DIMENSION, Identifier.parse(this))
        }
    }
}
