package dev.necr0manthre.innotournament.presentation.map

import com.mojang.serialization.Codec
import com.mojang.serialization.JsonOps
import com.mojang.serialization.codecs.RecordCodecBuilder
import dev.necr0manthre.innotournament.generators.GeneratorKey
import dev.necr0manthre.innotournament.generators.GeneratorPosition
import dev.necr0manthre.innotournament.generators.GeneratorType
import org.bukkit.NamespacedKey
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8

/** Pure encoder for the current JourneyMap waypoint plugin-message endpoint. */
object JourneyMapWaypointMessages {
    const val CHANNEL = "journeymap:waypoint"

    fun managedName(position: GeneratorPosition): String =
        "innotournament_${position.worldId}_${position.x}_${position.y}_${position.z}"

    fun create(key: GeneratorKey, dimension: NamespacedKey): ByteArray {
        val position = key.position
        val waypoint = Waypoint(
            name = managedName(position),
            groupId = "journeymap_default",
            origin = "external",
            pos = WaypointPosition(position.x, position.y, position.z, dimension.toString()),
            dimensions = listOf(dimension.toString()),
            color = key.type.color,
            settings = WaypointSettings(enable = true, showDeviation = false, persistent = false),
            icon = WaypointIcon("journeymap:textures/waypoint/icon/waypoint-icon.png")
        )
        return packet(encode(WAYPOINT_CODEC, waypoint), "create")
    }

    fun delete(name: String): ByteArray {
        require(name.isNotBlank()) { "Managed waypoint name must not be blank" }
        return packet(encode(DELETE_CODEC, DeleteWaypoint(name, "external")), "delete")
    }

    private fun packet(json: String, action: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeUtf(json)
        out.writeUtf(action)
        out.write(0) // announce=false
        return out.toByteArray().also {
            require(it.size <= MAX_MESSAGE_SIZE) { "JourneyMap message exceeds Paper's message limit" }
        }
    }

    private fun ByteArrayOutputStream.writeUtf(value: String) {
        require(value.length <= MAX_STRING_LENGTH) { "JourneyMap string exceeds its UTF-8 character limit" }
        val bytes = value.toByteArray(UTF_8)
        writeVarInt(bytes.size)
        write(bytes)
    }

    private fun ByteArrayOutputStream.writeVarInt(value: Int) {
        var remaining = value
        while (remaining and -128 != 0) {
            write(remaining and 127 or 128)
            remaining = remaining ushr 7
        }
        write(remaining)
    }

    private fun <T : Any> encode(codec: Codec<T>, value: T): String =
        codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow { message ->
            IllegalArgumentException("Could not encode JourneyMap waypoint: $message")
        }.toString()

    private val GeneratorType.color: Int
        get() = when (this) {
            GeneratorType.ITEM_GENERATOR -> 0xFFFF00
            GeneratorType.BOOK_GENERATOR -> 0x00FFFF
            GeneratorType.NETHER_GENERATOR -> 0xFF0000
        }

    private data class Waypoint(
        val name: String,
        val groupId: String,
        val origin: String,
        val pos: WaypointPosition,
        val dimensions: List<String>,
        val color: Int,
        val settings: WaypointSettings,
        val icon: WaypointIcon
    )

    private data class WaypointPosition(val x: Int, val y: Int, val z: Int, val primaryDimension: String)
    private data class WaypointSettings(val enable: Boolean, val showDeviation: Boolean, val persistent: Boolean)
    private data class WaypointIcon(val id: String)
    private data class DeleteWaypoint(val name: String, val origin: String)

    private val POSITION_CODEC = RecordCodecBuilder.create { instance ->
        instance.group(
            Codec.INT.fieldOf("x").forGetter(WaypointPosition::x),
            Codec.INT.fieldOf("y").forGetter(WaypointPosition::y),
            Codec.INT.fieldOf("z").forGetter(WaypointPosition::z),
            Codec.STRING.fieldOf("primaryDimension").forGetter(WaypointPosition::primaryDimension)
        ).apply(instance, ::WaypointPosition)
    }

    private val SETTINGS_CODEC = RecordCodecBuilder.create { instance ->
        instance.group(
            Codec.BOOL.fieldOf("enable").forGetter(WaypointSettings::enable),
            Codec.BOOL.fieldOf("showDeviation").forGetter(WaypointSettings::showDeviation),
            Codec.BOOL.fieldOf("persistent").forGetter(WaypointSettings::persistent)
        ).apply(instance, ::WaypointSettings)
    }

    private val ICON_CODEC = RecordCodecBuilder.create { instance ->
        instance.group(Codec.STRING.fieldOf("id").forGetter(WaypointIcon::id))
            .apply(instance, ::WaypointIcon)
    }

    private val WAYPOINT_CODEC = RecordCodecBuilder.create { instance ->
        instance.group(
            Codec.STRING.fieldOf("name").forGetter(Waypoint::name),
            Codec.STRING.fieldOf("groupId").forGetter(Waypoint::groupId),
            Codec.STRING.fieldOf("origin").forGetter(Waypoint::origin),
            POSITION_CODEC.fieldOf("pos").forGetter(Waypoint::pos),
            Codec.STRING.listOf().fieldOf("dimensions").forGetter(Waypoint::dimensions),
            Codec.INT.fieldOf("color").forGetter(Waypoint::color),
            SETTINGS_CODEC.fieldOf("settings").forGetter(Waypoint::settings),
            ICON_CODEC.fieldOf("icon").forGetter(Waypoint::icon)
        ).apply(instance, ::Waypoint)
    }

    private val DELETE_CODEC = RecordCodecBuilder.create { instance ->
        instance.group(
            Codec.STRING.fieldOf("name").forGetter(DeleteWaypoint::name),
            Codec.STRING.fieldOf("origin").forGetter(DeleteWaypoint::origin)
        ).apply(instance, ::DeleteWaypoint)
    }

    private const val MAX_STRING_LENGTH = 32_767
    private const val MAX_MESSAGE_SIZE = 1_048_576
}
