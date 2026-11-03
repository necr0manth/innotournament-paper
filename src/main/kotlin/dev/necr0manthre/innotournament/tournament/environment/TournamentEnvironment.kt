package dev.necr0manthre.innotournament.tournament.environment

import org.bukkit.Bukkit
import org.bukkit.GameRules
import org.bukkit.NamespacedKey
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.WorldBorder
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** World-stage effects and owned chunk leases for one tournament run. */
class TournamentEnvironment(
    private val worlds: ResolvedWorldBindings,
    private val preStartSpawn: WorldPoint,
    private val gameSpawn: WorldPoint,
    private val chunkTickets: PluginChunkTickets
) {
    private val preloadLeases = mutableMapOf<Pair<Int, Int>, ChunkLease>()

    init {
        require(preStartSpawn.world === worlds.preStartWorld) { "Pre-start point belongs to another world" }
        require(gameSpawn.world === worlds.gameWorld) { "Game point belongs to another world" }
    }

    /** Set the pre-start spawn/centers, and expand only the built-in End border to its Paper max. */
    fun applyPrepare() {
        requireMainThread()
        val pos = blockPosition(preStartSpawn)
        val centers = mutableListOf<CenterChange>()

        // The original End update always replaces a pre-start End center with (0, 0).
        if (worlds.preStartWorld !== worlds.endWorld) {
            addCenter(centers, worlds.preStartWorld, pos.x, pos.z)
        }
        if (worlds.preStartWorld.key == OVERWORLD) {
            addCenter(centers, worlds.netherWorld, pos.x / 8, pos.z / 8)
        }
        addCenter(centers, worlds.endWorld, 0, 0)

        val end = centers.single { it.world === worlds.endWorld }
        val endMaxSize = end.border.maxSize
        require(endMaxSize.isFinite() && endMaxSize >= 1.0) { "Invalid End world border maximum" }
        validateCenters(centers)

        acquirePreloadChunks()

        check(worlds.preStartWorld.setSpawnLocation(pos.x, pos.y, pos.z)) {
            "Could not set pre-start world spawn"
        }
        centers.filter { it.world !== worlds.endWorld }.forEach { it.apply() }
        end.border.setSize(endMaxSize)
        end.apply()
    }

    /** Release only this environment's retained chunk leases. */
    fun releaseResources() {
        requireMainThread()
        val leases = preloadLeases.entries.iterator()
        while (leases.hasNext()) {
            val entry = leases.next()
            entry.value.release()
            leases.remove()
        }
    }

    /** Set the game spawn and all vanilla-related centers using the original dimension branches. */
    fun applyStart() {
        requireMainThread()
        val pos = blockPosition(gameSpawn)
        val centers = mutableListOf<CenterChange>()
        when (worlds.gameWorld.key) {
            OVERWORLD -> {
                addCenter(centers, worlds.normalWorld, pos.x, pos.z)
                addCenter(centers, worlds.netherWorld, pos.x / 8, pos.z / 8)
                addCenter(centers, worlds.endWorld, 0, 0)
            }
            NETHER -> {
                addCenter(centers, worlds.netherWorld, pos.x, pos.z)
                addCenter(centers, worlds.normalWorld, pos.x / 8, pos.z / 8)
                addCenter(centers, worlds.endWorld, 0, 0)
            }
            else -> { // End and custom dimensions follow the same original center branch.
                addCenter(centers, worlds.endWorld, pos.x, pos.z)
                addCenter(centers, worlds.normalWorld, pos.x, pos.z)
                addCenter(centers, worlds.netherWorld, pos.x / 8, pos.z / 8)
            }
        }
        validateCenters(centers)

        centers.forEach { it.apply() }
        check(worlds.gameWorld.setSpawnLocation(pos.x, pos.y, pos.z)) {
            "Could not set game world spawn"
        }
    }

    /** An explicit configured action: size is normal-world equivalent; duration is exact ticks. */
    fun changeBorder(size: Double, ticks: Long) {
        requireMainThread()
        require(size.isFinite() && size > 0.0) { "Border size must be finite and positive" }
        require(ticks in 0L..Int.MAX_VALUE.toLong()) { "Border duration is outside Paper's tick range" }

        val changes = mutableListOf<SizeChange>()
        addSize(changes, worlds.normalWorld, size)
        addSize(changes, worlds.netherWorld, size / 8.0)
        addSize(changes, worlds.endWorld, size)
        // Every effective size and any shared-physical-world conflict is checked above.
        changes.forEach { it.border.changeSize(it.size, ticks) }
    }

    /** An explicit configured action; unrelated Bukkit worlds are not affected. */
    fun setPvp(enabled: Boolean) {
        requireMainThread()
        val related = listOf(
            worlds.preStartWorld, worlds.gameWorld, worlds.normalWorld,
            worlds.netherWorld, worlds.endWorld
        )
        val unique = mutableListOf<World>()
        for (world in related) {
            if (unique.none { it === world }) unique.add(world)
        }
        for (world in unique) {
            check(world.setGameRule(GameRules.PVP, enabled)) { "Could not change PvP for ${world.key}" }
        }
    }

    private fun addSize(changes: MutableList<SizeChange>, world: World, requested: Double) {
        // Size rounding is permitted only where the positive effective size is below Paper's 1.0.
        require(requested.isFinite() && requested > 0.0) { "Unrepresentable border size" }
        val effective = if (requested < 1.0) ceil(requested) else requested
        val previous = changes.firstOrNull { it.world === world }
        val border = previous?.border ?: world.worldBorder
        require(effective >= 1.0 && effective <= border.maxSize) {
            "Border size $effective is outside the bounds of ${world.key}"
        }
        if (previous == null) {
            changes.add(SizeChange(world, border, effective))
        } else {
            require(previous.size == effective) {
                "Conflicting border sizes for the same bound world ${world.key}"
            }
        }
    }

    private fun addCenter(changes: MutableList<CenterChange>, world: World, x: Int, z: Int) {
        val previous = changes.firstOrNull { it.world === world }
        if (previous == null) {
            changes.add(CenterChange(world, world.worldBorder, x, z))
        } else {
            require(previous.x == x && previous.z == z) {
                "Conflicting border centers for the same bound world ${world.key}"
            }
        }
    }

    private fun validateCenters(changes: List<CenterChange>) {
        for (change in changes) {
            val max = change.border.maxCenterCoordinate
            require(max.isFinite() && max >= 0.0 &&
                abs(change.x.toDouble()) <= max && abs(change.z.toDouble()) <= max) {
                "Border center is outside the bounds of ${change.world.key}"
            }
        }
    }

    private fun blockPosition(point: WorldPoint): BlockPosition {
        fun coordinate(value: Double): Int {
            val block = floor(value) // Bukkit block coordinate; integer inputs match Java BlockPos.
            require(block >= Int.MIN_VALUE.toDouble() && block <= Int.MAX_VALUE.toDouble()) {
                "Spawn block coordinate is outside the integer range"
            }
            return block.toInt()
        }
        return BlockPosition(coordinate(point.x), coordinate(point.y), coordinate(point.z))
    }

    private fun acquirePreloadChunks() {
        val gamePos = blockPosition(gameSpawn)
        val chunkX = Math.floorDiv(gamePos.x, 16)
        val chunkZ = Math.floorDiv(gamePos.z, 16)
        for (x in chunkX - PRELOAD_RADIUS..chunkX + PRELOAD_RADIUS) {
            for (z in chunkZ - PRELOAD_RADIUS..chunkZ + PRELOAD_RADIUS) {
                val chunk = x to z
                if (chunk !in preloadLeases) {
                    preloadLeases[chunk] = chunkTickets.acquire(worlds.gameWorld, x, z)
                }
            }
        }
    }

    private fun requireMainThread() {
        // In a real server every effect must be on its primary thread. A null server permits
        // isolated tests with lightweight World proxies; it cannot occur during live gameplay.
        val server: Server? = Bukkit.getServer()
        if (server != null) {
            check(Bukkit.isPrimaryThread()) { "Tournament world effects must run on the server main thread" }
        }
    }

    private data class BlockPosition(val x: Int, val y: Int, val z: Int)
    private data class CenterChange(val world: World, val border: WorldBorder, val x: Int, val z: Int) {
        fun apply() = border.setCenter(x.toDouble(), z.toDouble())
    }
    private data class SizeChange(val world: World, val border: WorldBorder, val size: Double)

    private companion object {
        const val PRELOAD_RADIUS = 32
        val OVERWORLD: NamespacedKey = NamespacedKey.minecraft("overworld")
        val NETHER: NamespacedKey = NamespacedKey.minecraft("the_nether")
    }
}
