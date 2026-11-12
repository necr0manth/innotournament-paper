package dev.necr0manthre.innotournament.generators

import org.bukkit.Chunk
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType

/**
 * The chunk-PDC half of a generator placement. Only block position and [GeneratorType] are saved;
 * inventories, levels and clocks belong to [ItemGeneratorSystem] and are never persisted here.
 *
 * Calls must be made on the server thread with a loaded chunk. The adapter is responsible for
 * installing the barrier before [write] and calling [remove] before removing that barrier.
 * This store never changes blocks, loads other chunks or repairs damaged PDC entries.
 */
class GeneratorPlacementStore {
    private val versionKey = NamespacedKey(NAMESPACE, "generators_version")

    /** Discover only marked locations in this chunk, validating every owned entry before returning. */
    fun read(chunk: Chunk): List<GeneratorKey> {
        val data = chunk.persistentDataContainer
        val entryKeys = data.keys.filter(::isPlacementKey).sortedBy { it.key }
        if (entryKeys.isEmpty() && !data.has(versionKey)) return emptyList()

        if (!data.has(versionKey, PersistentDataType.INTEGER)) {
            integrity(chunk, null, "missing or non-integer $versionKey for owned keys $entryKeys")
        }
        val version = data.get(versionKey, PersistentDataType.INTEGER)
        if (version != VERSION) integrity(chunk, null, "unsupported $versionKey=$version")

        val found = LinkedHashMap<GeneratorPosition, GeneratorKey>()
        for (entryKey in entryKeys) {
            val (localX, y, localZ) = parseCoordinates(chunk, entryKey)
            val position = GeneratorPosition(chunk.world.uid, global(chunk, chunk.x, localX), y,
                global(chunk, chunk.z, localZ))
            validateHeight(chunk, position)

            if (!data.has(entryKey, PersistentDataType.STRING)) {
                integrity(chunk, position, "non-string value for $entryKey")
            }
            val typeId = data.get(entryKey, PersistentDataType.STRING)
            val type = typeId?.let(GeneratorType::fromId)
                ?: integrity(chunk, position, "unknown or missing generator type '$typeId' at $entryKey")
            requireBarrier(chunk, position)

            val key = GeneratorKey(position, type)
            if (found.putIfAbsent(position, key) != null) {
                integrity(chunk, position, "duplicate generator placement at $entryKey")
            }
        }
        return found.values.toList()
    }

    /** Returns null for an unregistered position, including a bare vanilla barrier. */
    fun find(chunk: Chunk, position: GeneratorPosition): GeneratorKey? {
        requireInChunk(chunk, position)
        return read(chunk).firstOrNull { it.position == position }
    }

    /**
     * Save a new placement after the adapter has placed its barrier. Returns false if the same
     * placement was already stored. A conflicting type or corrupt existing data is never replaced.
     */
    fun write(chunk: Chunk, key: GeneratorKey): Boolean {
        val position = key.position
        requireInChunk(chunk, position)
        val old = read(chunk).firstOrNull { it.position == position }
        if (old != null) {
            if (old != key) integrity(chunk, position, "type conflict: stored ${old.type.id}, new ${key.type.id}")
            return false
        }
        requireBarrier(chunk, position)

        val data = chunk.persistentDataContainer
        data.set(versionKey, PersistentDataType.INTEGER, VERSION)
        data.set(placementKey(position), PersistentDataType.STRING, key.type.id)
        return true
    }

    /**
     * Remove exactly the supplied placement, not another type at the same position. A shared
     * version header remains until the last generator in this chunk has been removed.
     */
    fun remove(chunk: Chunk, key: GeneratorKey): Boolean {
        val position = key.position
        requireInChunk(chunk, position)
        val old = read(chunk).firstOrNull { it.position == position } ?: return false
        if (old != key) integrity(chunk, position, "type conflict: stored ${old.type.id}, removing ${key.type.id}")

        val data = chunk.persistentDataContainer
        data.remove(placementKey(position))
        if (data.keys.none(::isPlacementKey)) data.remove(versionKey)
        return true
    }

    private fun isPlacementKey(key: NamespacedKey): Boolean =
        key.namespace == NAMESPACE && key.key.startsWith(ENTRY_PREFIX)

    private fun placementKey(position: GeneratorPosition): NamespacedKey = NamespacedKey(
        NAMESPACE,
        "${ENTRY_PREFIX}${Math.floorMod(position.x, CHUNK_SIZE)}_${position.y}_${Math.floorMod(position.z, CHUNK_SIZE)}"
    )

    private fun parseCoordinates(chunk: Chunk, key: NamespacedKey): Triple<Int, Int, Int> {
        val encoded = key.key.removePrefix(ENTRY_PREFIX)
        val parts = encoded.split('_')
        if (parts.size != 3) integrity(chunk, null, "invalid generator placement key $key")
        val localX = parts[0].toIntOrNull()
        val y = parts[1].toIntOrNull()
        val localZ = parts[2].toIntOrNull()
        if (localX == null || y == null || localZ == null || localX !in 0 until CHUNK_SIZE ||
            localZ !in 0 until CHUNK_SIZE || encoded != "${localX}_${y}_${localZ}") {
            integrity(chunk, null, "invalid generator coordinates in key $key")
        }
        return Triple(localX, y, localZ)
    }

    private fun global(chunk: Chunk, chunkCoordinate: Int, local: Int): Int {
        val absolute = chunkCoordinate.toLong() * CHUNK_SIZE + local
        if (absolute !in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            integrity(chunk, null, "block coordinate overflow (chunk=$chunkCoordinate local=$local)")
        }
        return absolute.toInt()
    }

    private fun requireInChunk(chunk: Chunk, position: GeneratorPosition) {
        if (position.worldId != chunk.world.uid || Math.floorDiv(position.x, CHUNK_SIZE) != chunk.x ||
            Math.floorDiv(position.z, CHUNK_SIZE) != chunk.z) {
            integrity(chunk, position, "position does not belong to this world/chunk")
        }
        validateHeight(chunk, position)
    }

    private fun validateHeight(chunk: Chunk, position: GeneratorPosition) {
        if (position.y < chunk.world.minHeight || position.y >= chunk.world.maxHeight) {
            integrity(chunk, position, "Y outside world height [${chunk.world.minHeight}, ${chunk.world.maxHeight})")
        }
    }

    private fun requireBarrier(chunk: Chunk, position: GeneratorPosition) {
        val material = chunk.getBlock(Math.floorMod(position.x, CHUNK_SIZE), position.y,
            Math.floorMod(position.z, CHUNK_SIZE)).type
        if (material != Material.BARRIER) {
            integrity(chunk, position, "expected BARRIER for registered placement; found $material")
        }
    }

    private fun integrity(chunk: Chunk, position: GeneratorPosition?, detail: String): Nothing =
        throw IllegalStateException(
            "Generator placement integrity error: world=${chunk.world.uid} " +
                "chunk=(${chunk.x},${chunk.z}) " +
                "block=${position?.let { "(${it.x},${it.y},${it.z})" } ?: "unknown (see chunk/key)"}: $detail"
        )

    private companion object {
        const val NAMESPACE = "innotournament"
        const val ENTRY_PREFIX = "generator_"
        const val CHUNK_SIZE = 16
        const val VERSION = 1
    }
}
