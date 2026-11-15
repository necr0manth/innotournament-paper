package dev.necr0manthre.innotournament.generators

import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.event.world.WorldLoadEvent
import org.bukkit.event.world.WorldUnloadEvent

/**
 * Physical owner for the three placed generators. The future bootstrap registers this listener
 * once and calls [discoverLoadedWorld] for each already loaded world. It owns neither the tick
 * task nor any player-interaction/protection listener. All calls are synchronous/main-thread.
 * Map consumers receive only [GeneratorPlacementSource], not this adapter's mutable state.
 *
 * [find] also activates a stored marker on demand, so a later interaction listener can safely
 * pass its returned key to [GeneratorRuntime.take] or [GeneratorRuntime.reroll].
 */
class GeneratorPlacementLifecycle(
    private val items: GeneratorItems,
    private val placements: GeneratorPlacementStore,
    private val runtime: GeneratorRuntime
) : Listener, GeneratorPlacementSource, AutoCloseable {
    private val observations = GeneratorPlacementObservations()
    private var closed = false

    /** One main-thread call atomically attaches a view and captures already discovered points. */
    override fun subscribe(onChange: (GeneratorPlacementChange) -> Unit): GeneratorPlacementSubscription {
        requireMainThread()
        return observations.subscribe(onChange)
    }

    /** Only this adapter's observers are closed; runtime/tickets have a separate owner. */
    override fun close() {
        if (closed) return
        requireMainThread()
        closed = true
        observations.close()
    }

    private fun observe(keys: List<GeneratorKey>) = keys.forEach(observations::discovered)

    /** Explicit bootstrap entry: only already loaded chunks, never a full world block scan. */
    fun discoverLoadedWorld(world: World) {
        requireMainThread()
        discoverLoadedWorldOnPrimaryThread(world)
    }

    private fun discoverLoadedWorldOnPrimaryThread(world: World) {
        runtime.ensureOpen() // Fail before reading the world, even when no chunks are loaded.
        observations.observeLoadedChunks(world.loadedChunks.asIterable(), runtime::discoverLoadedChunk)
    }

    /** Only a marker in this block's already loaded chunk identifies an owned barrier. */
    fun find(block: Block): GeneratorKey? {
        requireMainThread()
        val chunk = loadedChunk(block)
        val position = position(block)
        val key = placements.find(chunk, position) ?: return null
        observe(runtime.discoverLoadedChunk(chunk))
        check(runtime.find(position)?.key == key) {
            "Generator placement is not active after discovery: $key"
        }
        observations.discovered(key)
        return key
    }

    /**
     * Administrative removal, also used after accepted creative break. It does not drop stock,
     * create an item or remove anything except this owner's PDC, runtime and physical barrier.
     * Repeated calls return false; no force setter or automatic recovery is provided.
     */
    fun remove(block: Block): Boolean {
        val key = find(block) ?: return false
        val chunk = loadedChunk(block)
        check(placements.remove(chunk, key)) { "Generator marker vanished before removal: $key" }
        check(runtime.unregister(key)) { "Generator runtime vanished before removal: $key" }
        loadedChunk(block) // ticket release must not turn the final block write into an implicit chunk load.
        block.setType(Material.AIR, false)
        observations.removed(key)
        return true
    }

    /** Observe the final allowed vanilla placement without altering the event or consuming items. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        requireMainThread()
        if (event.isCancelled || !event.canBuild()) return
        val special = items.identify(event.itemInHand) ?: return
        val type = special.generatorType ?: return // InnoDice never creates a block marker.
        val block = event.blockPlaced
        val chunk = loadedChunk(block)
        val key = GeneratorKey(position(block), type)

        // Validate the entire owned PDC chunk before replacing the accepted vanilla carrier.
        val existing = placements.find(chunk, key.position)
        if (existing != null) {
            check(existing == key) { "Generator placement type conflict: stored $existing, placing $key" }
            observe(runtime.discoverLoadedChunk(chunk)) // identical repeated observation is idempotent.
            observations.discovered(key)
            return
        }
        check(block.type == special.material) {
            "Generator carrier mismatch: world=${block.world.uid} " +
                "chunk=(${chunk.x},${chunk.z}) block=(${block.x},${block.y},${block.z}) " +
                "expected=${special.material} actual=${block.type}"
        }
        block.setType(Material.BARRIER, false)
        placements.write(chunk, key)
        observe(runtime.discoverLoadedChunk(chunk))
        observations.discovered(key)
    }

    /** A cancelled break or any non-creative attempt is not administrative removal. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCreativeBreak(event: BlockBreakEvent) {
        requireMainThread()
        if (!event.isCancelled && event.player.gameMode == GameMode.CREATIVE) remove(event.block)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onChunkLoad(event: ChunkLoadEvent) {
        requireMainThread()
        observe(runtime.discoverLoadedChunk(event.chunk))
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onWorldLoad(event: WorldLoadEvent) {
        discoverLoadedWorld(event.world)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onChunkUnload(event: ChunkUnloadEvent) {
        requireMainThread()
        runtime.deactivateChunk(event.chunk) // ephemeral state only; PDC survives normal unload.
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onWorldUnload(event: WorldUnloadEvent) {
        requireMainThread()
        if (!event.isCancelled) runtime.deactivateWorld(event.world)
    }

    private fun loadedChunk(block: Block): Chunk {
        val cx = block.x shr 4
        val cz = block.z shr 4
        check(block.world.isChunkLoaded(cx, cz)) {
            "Generator operation requires loaded chunk: world=${block.world.uid} chunk=($cx,$cz) " +
                "block=(${block.x},${block.y},${block.z})"
        }
        return block.chunk
    }

    private fun position(block: Block): GeneratorPosition =
        GeneratorPosition(block.world.uid, block.x, block.y, block.z)

    private fun requireMainThread() {
        check(Bukkit.isPrimaryThread()) { "Generator placement lifecycle must run on the primary server thread" }
        check(!closed) { "Generator placement lifecycle is closed" }
    }
}
