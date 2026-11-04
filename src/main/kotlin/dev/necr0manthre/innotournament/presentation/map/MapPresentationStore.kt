package dev.necr0manthre.innotournament.presentation.map

import com.google.gson.JsonParser
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.JsonOps
import com.mojang.serialization.codecs.PrimitiveCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.papermc.paper.util.AtomicFiles
import java.io.IOException
import java.math.BigDecimal
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.UUID

/** Persisted identity and area of a managed vanilla map. */
data class MapPresentationRecord(val id: Int, val area: MapAreaKey)

/** Durable metadata for map presentations and names previously published to JourneyMap. */
class MapPresentationStore(private val path: Path) {
    private var recordsById = LinkedHashMap<Int, MapPresentationRecord>()
    private var waypointNames = LinkedHashSet<String>()

    init {
        load()
    }

    /** A detached, read-only snapshot in insertion order. */
    val records: List<MapPresentationRecord>
        get() = Collections.unmodifiableList(recordsById.values.toList())

    /** A detached, read-only snapshot of every managed name ever published. */
    val publishedWaypointNames: Set<String>
        get() = Collections.unmodifiableSet(LinkedHashSet(waypointNames))

    fun recordForArea(area: MapAreaKey): MapPresentationRecord? =
        recordsById.values.firstOrNull { it.area == area }

    /** Adds and persists a map before a caller publishes map items that refer to it. */
    fun addRecord(record: MapPresentationRecord): Boolean {
        require(record.id >= 0) { "Map ID must not be negative" }
        val existing = recordsById[record.id]
        require(existing == null || existing == record) {
            "Map ID ${record.id} is already stored with different metadata"
        }
        if (existing != null) return false

        val updated = LinkedHashMap(recordsById)
        updated[record.id] = record
        save(updated, waypointNames)
        recordsById = updated
        return true
    }

    /** Remembers a managed name durably before its first JourneyMap endpoint send. */
    fun rememberWaypointName(name: String): Boolean {
        require(name.isNotBlank()) { "Managed waypoint name must not be blank" }
        if (name in waypointNames) return false

        val updated = LinkedHashSet(waypointNames)
        updated.add(name)
        save(recordsById, updated)
        waypointNames = updated
        return true
    }

    private fun load() {
        if (!Files.exists(path)) return

        val document = try {
            val json = JsonParser.parseString(Files.readString(path, UTF_8))
            DOCUMENT_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow { message ->
                IllegalArgumentException(message)
            }
        } catch (e: Exception) {
            if (e is IOException) throw e
            throw IOException("Invalid map presentation metadata '$path': ${e.message}", e)
        }

        val loadedRecords = LinkedHashMap<Int, MapPresentationRecord>()
        document.maps.forEach { record ->
            val existing = loadedRecords.putIfAbsent(record.id, record)
            if (existing != null && existing != record) {
                throw IOException("Conflicting map ID ${record.id} in '$path'")
            }
        }
        val loadedNames = LinkedHashSet(document.publishedWaypointNames)

        recordsById = loadedRecords
        waypointNames = loadedNames
    }

    private fun save(
        records: Map<Int, MapPresentationRecord>,
        names: Set<String>
    ) {
        val json = try {
            DOCUMENT_CODEC.encodeStart(
                JsonOps.INSTANCE,
                MapPresentationDocument(records.values.toList(), names.toList())
            ).getOrThrow { message -> IllegalArgumentException(message) }.toString()
        } catch (e: Exception) {
            throw IOException("Could not encode map presentation metadata '$path': ${e.message}", e)
        }

        try {
            Files.createDirectories(path.toAbsolutePath().parent)
            AtomicFiles.atomicWrite(path) { temporary -> Files.writeString(temporary, json, UTF_8) }
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("Could not save map presentation metadata '$path': ${e.message}", e)
        }
    }

    companion object {
        const val FILE_NAME = "map-presentation.json"
        private const val FORMAT_VERSION = 1

        private data class MapPresentationDocument(
            val maps: List<MapPresentationRecord>,
            val publishedWaypointNames: List<String>
        )

        private val INTEGER: Codec<Int> = object : PrimitiveCodec<Int> {
            override fun <T> read(ops: DynamicOps<T>, input: T): DataResult<Int> =
                ops.getNumberValue(input).flatMap { number ->
                    try {
                        DataResult.success(BigDecimal(number.toString()).intValueExact())
                    } catch (_: ArithmeticException) {
                        DataResult.error { "Expected a 32-bit integer, found $number" }
                    } catch (_: NumberFormatException) {
                        DataResult.error { "Expected a 32-bit integer, found $number" }
                    }
                }

            override fun <T> write(ops: DynamicOps<T>, value: Int): T = ops.createInt(value)
        }

        private val UUID_CODEC: Codec<UUID> = Codec.STRING.comapFlatMap(
            { value ->
                try {
                    DataResult.success(UUID.fromString(value))
                } catch (_: IllegalArgumentException) {
                    DataResult.error { "Invalid UUID: $value" }
                }
            },
            UUID::toString
        )

        private val MAP_RECORD_CODEC: Codec<MapPresentationRecord> = RecordCodecBuilder.create { instance ->
            instance.group(
                INTEGER.fieldOf("id").forGetter(MapPresentationRecord::id),
                UUID_CODEC.fieldOf("worldUUID").forGetter { it.area.worldId },
                INTEGER.fieldOf("centerX").forGetter { it.area.centerX },
                INTEGER.fieldOf("centerZ").forGetter { it.area.centerZ },
                INTEGER.fieldOf("scale").forGetter { it.area.scale }
            ).apply(instance) { id, worldId, centerX, centerZ, scale ->
                MapPresentationRecord(id, MapAreaKey(worldId, centerX, centerZ, scale))
            }
        }

        private val DOCUMENT_CODEC: Codec<MapPresentationDocument> = RecordCodecBuilder.create { instance ->
            instance.group(
                INTEGER.fieldOf("version").forGetter { FORMAT_VERSION },
                MAP_RECORD_CODEC.listOf().fieldOf("maps").forGetter(MapPresentationDocument::maps),
                Codec.STRING.listOf().fieldOf("publishedWaypointNames")
                    .forGetter(MapPresentationDocument::publishedWaypointNames)
            ).apply(instance) { version, maps, names ->
                require(version == FORMAT_VERSION) { "Unsupported map presentation metadata version $version" }
                require(maps.all { it.id >= 0 }) { "Map IDs must not be negative" }
                require(names.all(String::isNotBlank)) { "Managed waypoint names must not be blank" }
                MapPresentationDocument(maps, names)
            }
        }
    }
}
