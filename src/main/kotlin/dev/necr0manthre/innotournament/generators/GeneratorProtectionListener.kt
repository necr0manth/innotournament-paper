package dev.necr0manthre.innotournament.generators

import org.bukkit.GameMode
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityExplodeEvent

/**
 * Protects only generator positions registered in our chunk PDC, not arbitrary barriers or land.
 * The placement lifecycle owns creative removal and all PDC, block, runtime and display writes.
 * Register this listener on the main server thread alongside that lifecycle adapter.
 */
class GeneratorProtectionListener(private val placements: GeneratorPlacementStore) : Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) {
        if (isRegistered(event.block) && event.player.gameMode != GameMode.CREATIVE) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        event.blockList().removeIf(::isRegistered)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        event.blockList().removeIf(::isRegistered)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        // Include the head's first destination when getBlocks() is empty, and slime/honey side moves.
        if (isRegistered(event.block.getRelative(event.direction)) ||
            movesRegistered(event.blocks, event.direction)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        // Paper reports the piston facing; pulled blocks move in the opposite direction.
        if (movesRegistered(event.blocks, event.direction.oppositeFace)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityChangeBlock(event: EntityChangeBlockEvent) {
        if (isRegistered(event.block)) event.isCancelled = true
    }

    private fun movesRegistered(blocks: List<Block>, direction: BlockFace): Boolean =
        blocks.any { isRegistered(it) || isRegistered(it.getRelative(direction)) }

    private fun isRegistered(block: Block): Boolean {
        val world = block.world
        // A relative piston destination can fall outside world height or an already loaded chunk.
        // Do not load new chunks merely to investigate a physics event.
        if (block.y < world.minHeight || block.y >= world.maxHeight ||
            !world.isChunkLoaded(Math.floorDiv(block.x, 16), Math.floorDiv(block.z, 16))) return false

        // find() checks the entire owned chunk schema, type, coordinates and physical BARRIER.
        // Corrupt owned markers deliberately throw with world/chunk/position diagnostics.
        return placements.find(block.chunk, GeneratorPosition(world.uid, block.x, block.y, block.z)) != null
    }
}
