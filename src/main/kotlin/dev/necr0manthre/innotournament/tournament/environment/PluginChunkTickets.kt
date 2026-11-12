package dev.necr0manthre.innotournament.tournament.environment

import org.bukkit.World
import org.bukkit.plugin.Plugin
import java.util.UUID

interface ChunkLease {
    fun release()
}

/**
 * Shares one Paper plugin ticket per world/chunk between independent resource owners.
 * Acquire and release on the main server thread. All ticket users of this plugin
 * must acquire through the same [PluginChunkTickets] instance.
 */
class PluginChunkTickets(private val plugin: Plugin) {
    private data class ChunkKey(val worldId: UUID, val x: Int, val z: Int)
    private class Entry(val world: World, var leaseCount: Int)

    private val entries = HashMap<ChunkKey, Entry>()

    fun acquire(world: World, chunkX: Int, chunkZ: Int): ChunkLease {
        val key = ChunkKey(world.uid, chunkX, chunkZ)
        val existing = entries[key]
        if (existing != null) {
            existing.leaseCount++
            return Lease(key, existing)
        }

        // Reserve the first share before Paper can fire a synchronous chunk-load event.
        val entry = Entry(world, 1)
        entries[key] = entry
        world.addPluginChunkTicket(chunkX, chunkZ, plugin)
        return Lease(key, entry)
    }

    private inner class Lease(
        private val key: ChunkKey,
        private val entry: Entry,
    ) : ChunkLease {
        private var released = false

        override fun release() {
            if (released) return

            if (entry.leaseCount == 1) {
                entry.world.removePluginChunkTicket(key.x, key.z, plugin)
                entries.remove(key)
            } else {
                entry.leaseCount--
            }
            released = true
        }
    }
}
