package dev.necr0manthre.innotournament.tournament

import com.google.gson.JsonParseException
import com.google.gson.JsonParser
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
            COORDINATE.listOf(3, 3).xmap(
                { BlockPosition(it[0], it[1], it[2]) },
                { listOf(it.x, it.y, it.z) }
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
                POSITION.fieldOf("tournamentPreSpawn").forGetter(TournamentSettings::tournamentPreSpawn),
                KEY.fieldOf("tournamentPreSpawnDimension").forGetter(TournamentSettings::tournamentPreSpawnDimension),
                POSITION.fieldOf("tournamentSpawn").forGetter(TournamentSettings::tournamentSpawn),
                KEY.fieldOf("tournamentSpawnDimension").forGetter(TournamentSettings::tournamentSpawnDimension),
                KEY.fieldOf("startBoxStructureResourceLocation").forGetter(TournamentSettings::startBoxStructureResourceLocation),
                POSITION.fieldOf("startBoxPos").forGetter(TournamentSettings::startBoxPos),
                GAME_MODE.fieldOf("preStartGameMode").forGetter(TournamentSettings::preStartGameMode),
                PATH.fieldOf("preEventsPath").forGetter(TournamentSettings::preEventsPath),
                PATH.fieldOf("mainEventsPath").forGetter(TournamentSettings::mainEventsPath),
                SCORE_MODIFIER.listOf().fieldOf("teamScoreModifiers").forGetter(TournamentSettings::teamScoreModifiers),
                LAST_SCORE.fieldOf("lastTeamScore").forGetter(TournamentSettings::lastTeamScore)
            ).apply(instance, ::TournamentSettings)
        }
    }
}
