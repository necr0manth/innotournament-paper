package dev.necr0manthre.innotournament.generators

import org.bukkit.inventory.ItemStack
import java.util.Collections
import java.util.UUID
import kotlin.random.Random

/** A block coordinate in one world; no Bukkit World or mutable Location is retained. */
data class GeneratorPosition(val worldId: UUID, val x: Int, val y: Int, val z: Int)

/** The identity of a placement includes its type, while its position is unique across all types. */
data class GeneratorKey(val position: GeneratorPosition, val type: GeneratorType)

/** A detached read-only view. Every access to slots returns copies of the ten ItemStacks. */
sealed interface GeneratorInstance {
    val key: GeneratorKey
    val level: Int
    val timer: Int
    val waitingForNextLevel: Boolean
    val slots: List<ItemStack?>
}

/**
 * Domain owner for discovered placements and their ephemeral inventories. All methods must run
 * sequentially on the server's primary thread. The Paper adapter owns chunk tickets, displays,
 * discovery, interaction permissions and delivery of the stacks returned by [take].
 *
 * Registrations start empty; only placement/type survives restarts in the adapter's chunk PDC.
 * This system runs independently of tournament ticks, with no wall-clock catch-up.
 */
class ItemGeneratorSystem(private val profiles: GeneratorProfiles, private val random: Random = Random.Default) {
    private val instances = LinkedHashMap<GeneratorPosition, Runtime>()

    /** Duplicate discovery is idempotent; a competing type at one position is an integrity error. */
    fun register(key: GeneratorKey): GeneratorInstance {
        val existing = instances[key.position]
        if (existing != null) {
            check(existing.key == key) { "Conflicting generator types at ${key.position}: ${existing.key.type.id} and ${key.type.id}" }
            return existing.snapshot()
        }
        val runtime = Runtime(key, profiles[key.type])
        instances[key.position] = runtime
        return runtime.snapshot()
    }

    fun find(position: GeneratorPosition): GeneratorInstance? = instances[position]?.snapshot()

    /** A stale adapter operation must never remove a different type at the same position. */
    fun unregister(key: GeneratorKey): Boolean {
        if (instances[key.position]?.key != key) return false
        instances.remove(key.position)
        return true
    }

    /** Fresh, detached snapshots; useful to displays and optional map presentation. */
    fun discovered(): List<GeneratorInstance> = Collections.unmodifiableList(instances.values.map { it.snapshot() })

    /**
     * Run exactly once for each server tick. Returns only keys whose visible slot contents
     * changed, allowing the Paper adapter to refresh displays without polling ItemStacks.
     */
    fun tick(): List<GeneratorKey> {
        val changed = ArrayList<GeneratorKey>()
        instances.values.forEach { runtime ->
            if (runtime.tick()) changed.add(runtime.key)
        }
        return Collections.unmodifiableList(changed)
    }

    /**
     * Null means unknown placement; an empty list means a known, empty generator. Stacks are
     * transferred to the adapter exactly once, and the nonempty generator resets in this call.
     * The adapter gives these stacks to the player and drops inventory overflow near the player.
     */
    fun take(key: GeneratorKey): List<ItemStack>? {
        val runtime = instances[key.position]?.takeIf { it.key == key } ?: return null
        return runtime.take()
    }

    /** True even when the generator is empty: the Paper adapter consumes one valid Dice. */
    fun reroll(key: GeneratorKey): Boolean {
        val runtime = instances[key.position]?.takeIf { it.key == key } ?: return false
        runtime.reroll()
        return true
    }

    /** Discard runtime state on plugin disable without affecting persistent placement PDC. */
    fun discardRuntime() {
        instances.clear()
    }

    private class Snapshot(
        override val key: GeneratorKey,
        override val level: Int,
        override val timer: Int,
        override val waitingForNextLevel: Boolean,
        saved: Array<ItemStack?>
    ) : GeneratorInstance {
        private val contents = saved.map { it?.clone() }

        override val slots: List<ItemStack?>
            get() = Collections.unmodifiableList(contents.map { it?.clone() })
    }

    private inner class Runtime(val key: GeneratorKey, private val profile: GeneratorProfile) {
        private val slots = arrayOfNulls<ItemStack>(10)
        private var level = 0
        private var timer = 0
        private var waitingForNextLevel = false

        fun snapshot(): GeneratorInstance = Snapshot(key, level, timer, waitingForNextLevel, slots)

        private fun reset() {
            slots.fill(null)
            level = 0
            timer = 0
            waitingForNextLevel = false
        }

        fun take(): List<ItemStack> {
            val taken = slots.filterNotNull().map { it.clone() }
            if (taken.isNotEmpty()) reset()
            return Collections.unmodifiableList(taken)
        }

        fun reroll() {
            val count = slots.indexOfFirst { it == null }.let { if (it == -1) slots.size else it }
            repeat(count) { index -> slots[index] = draw(profile.levels[level]) }
        }

        fun tick(): Boolean {
            var slotsChanged = false
            if (waitingForNextLevel) {
                val next = profile.levels[level + 1]
                if (timer >= next.generationTime) {
                    slots.fill(null)
                    level++
                    timer = 0
                    slots[0] = draw(next)
                    waitingForNextLevel = false
                    return true
                }
                timer++
                return slotsChanged
            }

            val current = profile.levels[level]
            if (timer >= current.generationTime) {
                timer = 0
                val firstFree = (0 until current.maxItemCount).firstOrNull { slots[it] == null }
                if (firstFree != null) {
                    slots[firstFree] = draw(current)
                    slotsChanged = true
                }
                if (level < profile.levels.lastIndex && slots[current.maxItemCount - 1] != null) {
                    waitingForNextLevel = true
                }
            } else {
                timer++
            }
            return slotsChanged
        }

        private fun draw(current: GeneratorLevel): ItemStack {
            // Divide before summing so even several very large finite weights cannot overflow.
            val positive = current.probabilities.filter { it.weight > 0.0 }
            val scale = positive.maxOf { it.weight }
            val total = positive.sumOf { it.weight / scale }
            var choice = random.nextDouble(total)
            for (entry in positive) {
                choice -= entry.weight / scale
                if (choice < 0.0) return entry.prototype
            }
            // Floating-point rounding at the upper boundary must not create a missing drop.
            return positive.last().prototype
        }
    }
}
