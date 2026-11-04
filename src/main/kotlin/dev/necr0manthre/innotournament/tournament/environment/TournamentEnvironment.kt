package dev.necr0manthre.innotournament.tournament.environment

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.GameRules
import org.bukkit.NamespacedKey
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.WorldBorder
import org.bukkit.block.Block
import org.bukkit.block.structure.Mirror
import org.bukkit.block.structure.StructureRotation
import org.bukkit.entity.Entity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.*
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerBucketEmptyEvent
import org.bukkit.event.player.PlayerBucketFillEvent
import org.bukkit.plugin.Plugin
import org.bukkit.structure.Structure
import org.bukkit.structure.StructureManager
import org.bukkit.util.Vector
import java.util.Random
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** World-stage effects and owned chunk leases for one tournament run. */
class TournamentEnvironment(
    private val worlds: ResolvedWorldBindings,
    private val preStartSpawn: WorldPoint,
    private val gameSpawn: WorldPoint,
    private val chunkTickets: PluginChunkTickets,
    private val plugin: Plugin,
    private val structureManager: StructureManager,
    private val startBoxStructureKey: NamespacedKey?,
    private val startBoxOrigin: WorldPoint
) {
    private val preloadLeases = mutableMapOf<Pair<Int, Int>, ChunkLease>()
    private var startBox: StartBoxResource? = null
    private var structureTemplate: Structure? = null
    private var structureResolved = false

    init {
        require(preStartSpawn.world === worlds.preStartWorld) { "Pre-start point belongs to another world" }
        require(gameSpawn.world === worlds.gameWorld) { "Game point belongs to another world" }
        require(startBoxOrigin.world === worlds.preStartWorld) { "Start box origin belongs to another world" }
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
        removeStartBox()
        val leases = preloadLeases.entries.iterator()
        while (leases.hasNext()) {
            val entry = leases.next()
            entry.value.release()
            leases.remove()
        }
    }

    /** Place the configured template and retain ownership before its first block change. */
    fun placeStartBox() {
        requireMainThread()
        if (startBoxStructureKey == null) return
        val template = resolveStructure()
            ?: error("Start box structure '$startBoxStructureKey' was not found")
        val region = boxRegion(template)
        val existing = startBox
        if (existing != null) {
            check(existing.region.sameArea(region)) { "Remove the existing start box before changing its area" }
            return
        }

        val resource = StartBoxResource(region)
        startBox = resource
        resource.acquireTickets()
        Bukkit.getPluginManager().registerEvents(resource.protection, plugin)
        resource.protectionRegistered = true
        resource.placementStarted = true
        template.place(
            startBoxOrigin.toLocation(), false, StructureRotation.NONE, Mirror.NONE,
            0, 1.0f, Random(0L)
        )
    }

    /** Clear only the owned box; a failed clear retains its protection and leases. */
    fun removeStartBox() {
        requireMainThread()
        val resource = startBox ?: return
        if (resource.placementStarted) {
            val region = resource.region
            for (x in region.minX until region.maxX) {
                for (y in region.minY until region.maxY) {
                    for (z in region.minZ until region.maxZ) {
                        region.world.getBlockAt(x, y, z).type = org.bukkit.Material.AIR
                    }
                }
            }
        }
        if (resource.protectionRegistered) {
            org.bukkit.event.HandlerList.unregisterAll(resource.protection)
            resource.protectionRegistered = false
        }
        resource.releaseTickets()
        startBox = null
    }

    /** Test the configured box geometry even before it has been placed. */
    fun isInsideStartBox(point: WorldPoint): Boolean {
        if (startBoxStructureKey == null) return false
        val region = startBox?.region ?: boxRegion(
            resolveStructure() ?: error("Start box structure '$startBoxStructureKey' was not found")
        )
        return region.contains(point)
    }

    /** Return a new Bukkit Location each time. */
    fun preStartRespawnLocation(): Location = preStartSpawn.toLocation()

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

    private fun resolveStructure(): Structure? {
        if (!structureResolved) {
            structureTemplate = startBoxStructureKey?.let { structureManager.loadStructure(it) }
            structureResolved = true
        }
        return structureTemplate
    }

    private fun boxRegion(structure: Structure): BoxRegion {
        val size: Vector = structure.size
        val sizeX = size.blockX
        val sizeY = size.blockY
        val sizeZ = size.blockZ
        require(sizeX > 0 && sizeY > 0 && sizeZ > 0) { "Start box structure size must be positive" }
        val originX = blockCoordinate(startBoxOrigin.x)
        val originY = blockCoordinate(startBoxOrigin.y)
        val originZ = blockCoordinate(startBoxOrigin.z)
        val maxX = Math.addExact(originX, sizeX)
        val maxY = Math.addExact(originY, sizeY)
        val maxZ = Math.addExact(originZ, sizeZ)
        require(originY >= worlds.preStartWorld.minHeight && maxY <= worlds.preStartWorld.maxHeight) {
            "Start box is outside the build height of ${worlds.preStartWorld.key}"
        }
        val region = BoxRegion(worlds.preStartWorld, originX, originY, originZ, maxX, maxY, maxZ)
        require(region.contains(preStartSpawn)) { "Pre-start spawn must be inside the configured start box" }
        return region
    }

    private fun blockCoordinate(value: Double): Int {
        val floor = floor(value)
        require(floor >= Int.MIN_VALUE.toDouble() && floor <= Int.MAX_VALUE.toDouble()) {
            "Start box coordinate is outside the integer range"
        }
        return floor.toInt()
    }

    private inner class StartBoxResource(val region: BoxRegion) {
        val protection = BoxProtectionListener(region)
        val leases = mutableListOf<ChunkLease>()
        var protectionRegistered = false
        var placementStarted = false

        fun acquireTickets() {
            val minChunkX = Math.floorDiv(region.minX, 16)
            val maxChunkX = Math.floorDiv(region.maxX - 1, 16)
            val minChunkZ = Math.floorDiv(region.minZ, 16)
            val maxChunkZ = Math.floorDiv(region.maxZ - 1, 16)
            for (x in minChunkX..maxChunkX) {
                for (z in minChunkZ..maxChunkZ) {
                    leases += chunkTickets.acquire(region.world, x, z)
                }
            }
        }

        fun releaseTickets() {
            while (leases.isNotEmpty()) {
                leases.last().release()
                leases.removeAt(leases.lastIndex)
            }
        }
    }

    private class BoxRegion(
        val world: World,
        val minX: Int,
        val minY: Int,
        val minZ: Int,
        val maxX: Int,
        val maxY: Int,
        val maxZ: Int
    ) {
        fun sameArea(other: BoxRegion): Boolean = world === other.world && minX == other.minX &&
            minY == other.minY && minZ == other.minZ && maxX == other.maxX &&
            maxY == other.maxY && maxZ == other.maxZ

        fun contains(point: WorldPoint): Boolean = point.world === world &&
            point.x >= minX && point.x < maxX && point.y >= minY && point.y < maxY &&
            point.z >= minZ && point.z < maxZ

        fun contains(block: Block): Boolean = block.world === world &&
            block.x >= minX && block.x < maxX && block.y >= minY && block.y < maxY &&
            block.z >= minZ && block.z < maxZ

        fun contains(entity: Entity): Boolean = entity.world === world &&
            entity.location.x >= minX && entity.location.x < maxX &&
            entity.location.y >= minY && entity.location.y < maxY &&
            entity.location.z >= minZ && entity.location.z < maxZ
    }

    private class BoxProtectionListener(private val region: BoxRegion) : Listener {
        private fun protected(block: Block) = region.contains(block)

        @EventHandler fun onBreak(event: BlockBreakEvent) { if (protected(event.block)) event.isCancelled = true }
        @EventHandler fun onPlace(event: BlockPlaceEvent) { if (protected(event.block)) event.isCancelled = true }
        @EventHandler fun onMultiPlace(event: BlockMultiPlaceEvent) {
            if (event.replacedBlockStates.any { protected(it.block) }) event.isCancelled = true
        }
        @EventHandler fun onBucketEmpty(event: PlayerBucketEmptyEvent) {
            if (protected(event.block.getRelative(event.blockFace))) event.isCancelled = true
        }
        @EventHandler fun onBucketFill(event: PlayerBucketFillEvent) { if (protected(event.block)) event.isCancelled = true }
        @EventHandler fun onEntityExplode(event: EntityExplodeEvent) { event.blockList().removeIf(::protected) }
        @EventHandler fun onBlockExplode(event: BlockExplodeEvent) { event.blockList().removeIf(::protected) }
        @EventHandler fun onPistonExtend(event: BlockPistonExtendEvent) {
            if (protectPiston(event.blocks, event.block, event.direction)) event.isCancelled = true
        }
        @EventHandler fun onPistonRetract(event: BlockPistonRetractEvent) {
            if (protectPiston(event.blocks, event.block, event.direction.oppositeFace)) event.isCancelled = true
        }
        @EventHandler fun onFlow(event: BlockFromToEvent) {
            if (protected(event.block) || protected(event.toBlock)) event.isCancelled = true
        }
        @EventHandler fun onSpread(event: BlockSpreadEvent) {
            if (protected(event.block) || protected(event.source)) event.isCancelled = true
        }
        @EventHandler fun onBurn(event: BlockBurnEvent) { if (protected(event.block)) event.isCancelled = true }
        @EventHandler fun onIgnite(event: BlockIgniteEvent) { if (protected(event.block)) event.isCancelled = true }
        @EventHandler fun onForm(event: BlockFormEvent) { if (protected(event.block)) event.isCancelled = true }
        @EventHandler fun onGrow(event: BlockGrowEvent) { if (protected(event.block)) event.isCancelled = true }
        @EventHandler fun onEntityChangeBlock(event: EntityChangeBlockEvent) { if (protected(event.block)) event.isCancelled = true }
        @EventHandler fun onPhysics(event: BlockPhysicsEvent) { if (protected(event.block)) event.isCancelled = true }
        @EventHandler fun onFertilize(event: BlockFertilizeEvent) {
            if (protected(event.block) || event.blocks.any { protected(it.block) }) event.isCancelled = true
        }
        @EventHandler fun onStructureGrow(event: StructureGrowEvent) {
            if (event.blocks.any { protected(it.block) }) event.isCancelled = true
        }

        private fun protectPiston(blocks: List<Block>, piston: Block, movement: org.bukkit.block.BlockFace): Boolean =
            protected(piston) || blocks.any { protected(it) || protected(it.getRelative(movement)) }
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
