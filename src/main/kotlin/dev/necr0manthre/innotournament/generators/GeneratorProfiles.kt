package dev.necr0manthre.innotournament.generators

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.util.Collections

/** Logical IDs are also the placement-PDC values; profiles are loaded before the system starts. */
enum class GeneratorType(val id: String, val profileFile: String) {
    ITEM_GENERATOR("item_generator", "itemGen.json"),
    BOOK_GENERATOR("book_generator", "bookGen.json"),
    NETHER_GENERATOR("nether_generator", "netherGen.json");

    companion object {
        fun fromId(id: String): GeneratorType? = entries.firstOrNull { it.id == id }
    }
}

/** A resolved entry from the profile reader. Neither the supplied nor returned stack is retained by reference. */
class GeneratorWeightedItem(val sourceKey: String, prototype: ItemStack, val weight: Double) {
    private val savedPrototype = prototype.clone()

    val prototype: ItemStack
        get() = savedPrototype.clone()
}

/** A level is readable but has no writable backing table or mutable ItemStack references. */
class GeneratorLevel(
    val generationTime: Int,
    val maxItemCount: Int,
    probabilities: List<GeneratorWeightedItem>
) {
    val probabilities: List<GeneratorWeightedItem> = Collections.unmodifiableList(
        probabilities.map { GeneratorWeightedItem(it.sourceKey, it.prototype, it.weight) }
    )
}

/**
 * Fully resolved, immutable definition. The separate Paper reader validates JSON/SNBT and builds
 * these typed values; this boundary additionally validates all values used by the state machine.
 * The source path, level index and original probability key are retained for useful failures.
 */
class GeneratorProfile(
    val type: GeneratorType,
    val sourcePath: String,
    levels: List<GeneratorLevel>
) {
    val levels: List<GeneratorLevel> = Collections.unmodifiableList(
        levels.map { GeneratorLevel(it.generationTime, it.maxItemCount, it.probabilities) }
    )

    init {
        require(sourcePath.isNotBlank()) { "Generator profile (${type.id}) needs a source path" }
        require(this.levels.isNotEmpty()) { "$sourcePath (${type.id}): empty generator levels" }
        this.levels.forEachIndexed { index, level ->
            val where = "$sourcePath (${type.id}), level $index"
            require(level.generationTime >= 0) { "$where: generationTime must be >= 0" }
            require(level.maxItemCount in 1..10) { "$where: maxItemCount must be 1..10" }
            require(level.probabilities.isNotEmpty()) { "$where: probabilities must not be empty" }

            var hasPositiveWeight = false
            level.probabilities.forEach { entry ->
                val atEntry = "$where, entry '${entry.sourceKey}'"
                require(entry.sourceKey.isNotBlank()) { "$atEntry: empty probability key" }
                require(entry.weight.isFinite() && entry.weight >= 0.0) {
                    "$atEntry: weight must be finite and non-negative"
                }
                if (entry.weight > 0.0) hasPositiveWeight = true
                val stack = entry.prototype
                require(stack.type != Material.AIR && stack.amount > 0 && stack.amount <= stack.maxStackSize) {
                    "$atEntry: invalid or empty ItemStack (material=${stack.type}, count=${stack.amount})"
                }
            }
            require(hasPositiveWeight) { "$where: total probability weight must be positive" }
        }
    }
}

/** An atomic set of all three frozen definitions; no partial activation or hot reload. */
class GeneratorProfiles(profiles: Collection<GeneratorProfile>) {
    private val byType: Map<GeneratorType, GeneratorProfile>

    init {
        val supplied = profiles.toList()
        require(supplied.size == GeneratorType.entries.size && supplied.map { it.type }.toSet().size == GeneratorType.entries.size) {
            "Expected one profile for each generator type: ${GeneratorType.entries.joinToString { it.id }}"
        }
        byType = supplied.associate { profile ->
            profile.type to GeneratorProfile(profile.type, profile.sourcePath, profile.levels)
        }
    }

    operator fun get(type: GeneratorType): GeneratorProfile = byType.getValue(type)
}
