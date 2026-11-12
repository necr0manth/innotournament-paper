package dev.necr0manthre.innotournament.generators

import dev.necr0manthre.innotournament.tournament.environment.ChunkLease
import dev.necr0manthre.innotournament.tournament.environment.PluginChunkTickets
import org.bukkit.Chunk
import org.bukkit.World
import org.bukkit.inventory.ItemStack
import java.util.Collections
import java.util.UUID

/**
 * Main-thread owner of active Paper generators. Supply an exclusively owned [ItemGeneratorSystem]
 * built from already resolved [GeneratorProfiles], and the plugin's SAME shared [PluginChunkTickets]
 * used by the tournament environment. The caller owns the synchronous tick task and event hooks.
 *
 * Placement events install BARRIER then call [GeneratorPlacementStore.write] before discovery;
 * removal events call [GeneratorPlacementStore.remove] while BARRIER is present, then [unregister]
 * before changing the block. None of these operations writes placement PDC or modifies blocks.
 */
class GeneratorRuntime(
    private val generators: ItemGeneratorSystem,
    private val placements: GeneratorPlacementStore,
    private val displays: GeneratorDisplays,
    private val tickets: PluginChunkTickets
) : AutoCloseable {
    private data class ChunkId(val worldId: UUID, val x: Int, val z: Int)
    private enum class Stage { DISCOVERING, ACTIVE, RELEASING }

    private class ActiveChunk(val chunk: Chunk) {
        val placements = LinkedHashMap<GeneratorPosition, GeneratorKey>()
        var lease: ChunkLease? = null
        var stage = Stage.DISCOVERING
    }

    private val chunks = LinkedHashMap<ChunkId, ActiveChunk>()
    private var closed = false

    init {
        check(generators.discovered().isEmpty()) { "GeneratorRuntime requires an empty, exclusively owned ItemGeneratorSystem" }
    }

    /** Scan only the world's ALREADY loaded chunks, e.g. on enable or WorldLoadEvent. */
    fun discoverLoadedWorld(world: World): List<GeneratorKey> {
        ensureOpen()
        val added = ArrayList<GeneratorKey>()
        world.loadedChunks.forEach { added.addAll(discoverLoadedChunk(it)) }
        return Collections.unmodifiableList(added)
    }

    /** Read only our chunk-PDC markers; repeated or reentrant discovery never adds another lease. */
    fun discoverLoadedChunk(chunk: Chunk): List<GeneratorKey> {
        ensureOpen()
        val id = id(chunk)
        val existing = chunks[id]
        if (existing != null && existing.stage != Stage.ACTIVE) return emptyList()
        val world = chunk.world
        check(world.isChunkLoaded(chunk.x, chunk.z)) {
            "Generator discovery needs an already loaded chunk: world=${world.uid} chunk=(${chunk.x},${chunk.z})"
        }
        if (existing != null) check(existing.chunk.world === world) { "Generator world instance changed without deactivation: $id" }

        val stored = placements.read(chunk)
        if (existing == null && stored.isEmpty()) return emptyList()
        val active = existing ?: ActiveChunk(chunk).also { chunks[id] = it }
        val storedByPosition = stored.associateBy { it.position }
        active.placements.forEach { (position, key) ->
            check(storedByPosition[position] == key) { "Active generator has a missing/changed placement marker: $key" }
        }
        val added = stored.filter { it.position !in active.placements }
        if (added.isEmpty()) return emptyList()

        active.stage = Stage.DISCOVERING // reserve before ticket acquisition can fire a synchronous load event
        if (active.lease == null) active.lease = tickets.acquire(world, chunk.x, chunk.z)
        added.forEach { key ->
            val snapshot = generators.register(key)
            active.placements[key.position] = key // reserve before display creation can reenter discovery
            displays.sync(world, snapshot)
        }
        active.stage = Stage.ACTIVE
        return Collections.unmodifiableList(added)
    }

    /** Run exactly once per server tick, independently of Tournament stage and elapsed wall time. */
    fun tick() {
        ensureOpen()
        // Validate each active chunk's OWN PDC entries and physical BARRIERs before mutating any timer.
        chunks.values.forEach { active ->
            check(active.stage == Stage.ACTIVE) { "Generator chunk transition is incomplete: ${id(active.chunk)}" }
            val chunk = active.chunk
            check(chunk.world.isChunkLoaded(chunk.x, chunk.z)) {
                "Active generator chunk is not loaded: ${id(chunk)}"
            }
            val stored = placements.read(chunk).associateBy { it.position }
            active.placements.forEach { (position, key) ->
                check(stored[position] == key) { "Active generator has a missing/changed placement marker: $key" }
            }
        }
        generators.tick().forEach { key ->
            val active = active(key) ?: error("Generator tick changed an unowned placement: $key")
            displays.sync(active.chunk.world, checkNotNull(generators.find(key.position)))
        }
        displays.animate()
    }

    /** A detached snapshot, or null when this placement is not active. */
    fun find(position: GeneratorPosition): GeneratorInstance? {
        val key = chunks[id(position)]?.placements?.get(position) ?: return null
        return generators.find(key.position) ?: error("Missing registered generator: $key")
    }

    /** Transfer the core's detached stock once; inventory delivery belongs to the event adapter. */
    fun take(key: GeneratorKey): List<ItemStack>? {
        ensureOpen()
        val active = active(key) ?: return null
        check(active.stage == Stage.ACTIVE) { "Generator chunk transition is incomplete: $key" }
        verifyPlacement(active, key)
        val taken = generators.take(key) ?: error("Missing registered generator: $key")
        if (taken.isNotEmpty()) displays.sync(active.chunk.world, checkNotNull(generators.find(key.position)))
        return taken
    }

    /** The event adapter decides Dice identity, hand, permission and consumption. */
    fun reroll(key: GeneratorKey): Boolean {
        ensureOpen()
        val active = active(key) ?: return false
        check(active.stage == Stage.ACTIVE) { "Generator chunk transition is incomplete: $key" }
        verifyPlacement(active, key)
        val before = generators.find(key.position) ?: error("Missing registered generator: $key")
        check(generators.reroll(key)) { "Missing registered generator: $key" }
        val after = checkNotNull(generators.find(key.position))
        if (before.slots != after.slots) displays.sync(active.chunk.world, after)
        return true // even an empty generator accepts one valid Dice, as defined by the core
    }

    /** Deactivate the exact key after the adapter has removed its PDC entry, but before block removal. */
    fun unregister(key: GeneratorKey): Boolean {
        ensureOpen()
        val id = id(key.position)
        val active = chunks[id] ?: return false
        if (active.placements[key.position] != key) return false
        check(active.stage == Stage.ACTIVE) { "Generator chunk transition is incomplete: $key" }
        active.stage = Stage.RELEASING
        displays.remove(key)
        check(generators.unregister(key)) { "Missing registered generator: $key" }
        active.placements.remove(key.position)
        if (active.placements.isEmpty()) {
            checkNotNull(active.lease).release()
            chunks.remove(id)
        } else {
            active.stage = Stage.ACTIVE // other generators retain the single subsystem lease
        }
        return true
    }

    /** Explicit unload hook: discard ephemeral state without changing the chunk's placement PDC. */
    fun deactivateChunk(chunk: Chunk) {
        ensureOpen()
        val active = chunks[id(chunk)] ?: return
        if (active.stage == Stage.RELEASING) return // release may itself fire a synchronous unload event
        releaseChunk(active)
    }

    /** Explicit WorldUnloadEvent hook; only this world's active chunks are touched. */
    fun deactivateWorld(world: World) {
        ensureOpen()
        chunks.values.filter { it.chunk.world.uid == world.uid }.toList().forEach { active ->
            if (active.stage != Stage.RELEASING) releaseChunk(active)
        }
    }

    /** The adapter cancels its synchronous tick task before calling close on plugin disable. */
    override fun close() {
        if (closed) return
        chunks.values.toList().forEach { active ->
            if (active.stage != Stage.RELEASING) releaseChunk(active)
        }
        check(chunks.isEmpty()) { "Generator runtime still has an incomplete chunk release" }
        displays.close()
        generators.discardRuntime()
        closed = true
    }

    private fun releaseChunk(active: ActiveChunk) {
        active.stage = Stage.RELEASING
        active.placements.values.toList().forEach { key ->
            displays.remove(key)
            check(generators.unregister(key)) { "Missing registered generator: $key" }
            active.placements.remove(key.position)
        }
        checkNotNull(active.lease).release() // after displays and core; never releases another owner's lease
        chunks.remove(id(active.chunk))
    }

    private fun verifyPlacement(active: ActiveChunk, key: GeneratorKey) {
        val chunk = active.chunk
        check(chunk.world.isChunkLoaded(chunk.x, chunk.z)) { "Generator chunk is not loaded: $key" }
        check(placements.find(chunk, key.position) == key) { "Generator placement marker is missing/changed: $key" }
    }

    private fun active(key: GeneratorKey): ActiveChunk? = chunks[id(key.position)]?.takeIf {
        it.placements[key.position] == key
    }

    private fun id(chunk: Chunk) = ChunkId(chunk.world.uid, chunk.x, chunk.z)
    private fun id(position: GeneratorPosition) = ChunkId(position.worldId, position.x shr 4, position.z shr 4)
    /** Used by the lifecycle before world discovery, including worlds with no loaded chunks. */
    fun ensureOpen() = check(!closed) { "GeneratorRuntime is closed" }
}
