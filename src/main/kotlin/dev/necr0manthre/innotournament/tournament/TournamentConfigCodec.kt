package dev.necr0manthre.innotournament.tournament

import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.mojang.datafixers.util.Pair
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.JsonOps
import com.mojang.serialization.codecs.PrimitiveCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.Identifier
import net.minecraft.world.level.GameType
import org.bukkit.GameMode
import org.bukkit.NamespacedKey
import java.math.BigDecimal
import java.nio.file.InvalidPathException
import java.nio.file.Path

class TournamentConfigException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/** Typed JSON conversion without file access or game-world resource resolution. */
class TournamentConfigCodec {
    fun decode(json: String): TournamentSettings {
        val document = try {
            JsonParser.parseString(json)
        } catch (e: JsonParseException) {
            throw TournamentConfigException("settings: malformed JSON: ${e.message}", e)
        }
        return CODEC.parse(JsonOps.INSTANCE, document).getOrThrow(::configError)
    }

    fun encode(settings: TournamentSettings): String =
        CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow(::configError).toString()

    private fun configError(message: String) = TournamentConfigException("settings: $message")

    private companion object {
        private fun <T> field(name: String, codec: Codec<T>): Codec<T> =
            codec.mapResult(object : Codec.ResultFunction<T> {
                override fun <O> apply(ops: DynamicOps<O>, input: O, result: DataResult<Pair<T, O>>) =
                    result.mapError { "$name: $it" }

                override fun <O> coApply(ops: DynamicOps<O>, input: T, result: DataResult<O>) =
                    result.mapError { "$name: $it" }
            })

        // Codec.INT/BlockPos.CODEC truncate fractions and overflow; validate before converting.
        val COORDINATE: Codec<Int> = object : PrimitiveCodec<Int> {
            override fun <T> read(ops: DynamicOps<T>, input: T): DataResult<Int> =
                ops.getNumberValue(input).flatMap { value ->
                    try {
                        DataResult.success(BigDecimal(value.toString()).intValueExact())
                    } catch (_: ArithmeticException) {
                        DataResult.error { "Expected a 32-bit integer: $value" }
                    } catch (_: NumberFormatException) {
                        DataResult.error { "Expected a 32-bit integer: $value" }
                    }
                }

            override fun <T> write(ops: DynamicOps<T>, value: Int): T = ops.createInt(value)
        }

        val POSITION: Codec<BlockPosition> = Codec.withAlternative(
            COORDINATE.listOf().flatXmap(
                { coordinates ->
                    if (coordinates.size == 3) DataResult.success(BlockPosition(coordinates[0], coordinates[1], coordinates[2]))
                    else DataResult.error { "Expected exactly 3 coordinates, found ${coordinates.size}" }
                },
                { position -> DataResult.success(listOf(position.x, position.y, position.z)) }
            ),
            RecordCodecBuilder.create { instance ->
                instance.group(
                    COORDINATE.fieldOf("x").forGetter(BlockPosition::x),
                    COORDINATE.fieldOf("y").forGetter(BlockPosition::y),
                    COORDINATE.fieldOf("z").forGetter(BlockPosition::z)
                ).apply(instance, ::BlockPosition)
            }
        )
        val KEY: Codec<NamespacedKey> = Identifier.CODEC.xmap(
            { NamespacedKey(it.namespace, it.path) },
            { Identifier.fromNamespaceAndPath(it.namespace, it.key) }
        )
        val GAME_MODE: Codec<GameMode> = GameType.CODEC.xmap(
            { GameMode.valueOf(it.name) }, { GameType.valueOf(it.name) }
        )
        val PATH: Codec<Path> = Codec.STRING.comapFlatMap({ value ->
            try {
                DataResult.success(Path.of(value))
            } catch (e: InvalidPathException) {
                DataResult.error { "Invalid path: ${e.message}" }
            }
        }, Path::toString)
        val SCORE_MODIFIER: Codec<Double> = Codec.DOUBLE.validate {
            if (it.isFinite()) DataResult.success(it) else DataResult.error { "Expected a finite number" }
        }
        val LAST_SCORE: Codec<Float> = Codec.FLOAT.validate {
            if (it.isFinite()) DataResult.success(it) else DataResult.error { "Expected a finite float" }
        }
        val CODEC: Codec<TournamentSettings> = RecordCodecBuilder.create { instance ->
            instance.group(
                field("tournamentPreSpawn", POSITION).fieldOf("tournamentPreSpawn").forGetter(TournamentSettings::tournamentPreSpawn),
                field("tournamentPreSpawnDimension", KEY).fieldOf("tournamentPreSpawnDimension").forGetter(TournamentSettings::tournamentPreSpawnDimension),
                field("tournamentSpawn", POSITION).fieldOf("tournamentSpawn").forGetter(TournamentSettings::tournamentSpawn),
                field("tournamentSpawnDimension", KEY).fieldOf("tournamentSpawnDimension").forGetter(TournamentSettings::tournamentSpawnDimension),
                field("startBoxStructureResourceLocation", KEY).fieldOf("startBoxStructureResourceLocation").forGetter(TournamentSettings::startBoxStructureResourceLocation),
                field("startBoxPos", POSITION).fieldOf("startBoxPos").forGetter(TournamentSettings::startBoxPos),
                field("preStartGameMode", GAME_MODE).fieldOf("preStartGameMode").forGetter(TournamentSettings::preStartGameMode),
                field("preEventsPath", PATH).fieldOf("preEventsPath").forGetter(TournamentSettings::preEventsPath),
                field("mainEventsPath", PATH).fieldOf("mainEventsPath").forGetter(TournamentSettings::mainEventsPath),
                field("teamScoreModifiers", SCORE_MODIFIER.listOf()).fieldOf("teamScoreModifiers").forGetter(TournamentSettings::teamScoreModifiers),
                field("lastTeamScore", LAST_SCORE).fieldOf("lastTeamScore").forGetter(TournamentSettings::lastTeamScore)
            ).apply(instance, ::TournamentSettings)
        }
    }
}
