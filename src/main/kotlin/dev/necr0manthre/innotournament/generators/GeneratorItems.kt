package dev.necr0manthre.innotournament.generators

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/** Logical special-item IDs. Only the three block types correspond to [GeneratorType]. */
enum class SpecialItemType(val id: String, val material: Material, val itemName: String) {
    ITEM_GENERATOR(GeneratorType.ITEM_GENERATOR.id, Material.LODESTONE, "Item Generator"),
    BOOK_GENERATOR(GeneratorType.BOOK_GENERATOR.id, Material.CHISELED_STONE_BRICKS, "Book Generator"),
    NETHER_GENERATOR(GeneratorType.NETHER_GENERATOR.id, Material.POLISHED_BLACKSTONE_BRICKS, "Nether Generator"),
    INNO_DICE("inno_dice", Material.AMETHYST_SHARD, "Inno Dice");

    val modelMarker: String get() = "innotournament:$id"
    val generatorType: GeneratorType? get() = GeneratorType.fromId(id)

    companion object {
        fun fromId(id: String): SpecialItemType? = entries.firstOrNull {
            it.id == id || it.modelMarker == id
        }
    }
}

/** Creates special items without retaining mutable prototypes or trusting their visual appearance. */
class GeneratorItems {
    private val versionKey = NamespacedKey("innotournament", "special_item_version")
    private val typeKey = NamespacedKey("innotournament", "special_item_type")

    /** A fresh stack for the administrative give command or a generator display. */
    fun create(type: SpecialItemType, count: Int = 1): ItemStack {
        require(count in 1..type.material.maxStackSize) {
            "${type.id} count $count is outside 1..${type.material.maxStackSize}"
        }
        val stack = stamp(type, ItemStack(type.material, count))
        val meta = stack.itemMeta ?: error("Missing metadata for ${type.material}")
        meta.itemName(Component.text(type.itemName))
        stack.itemMeta = meta
        return stack
    }

    /**
     * Stamp a *copy* of an already resolved vanilla stack. Its configured name, lore and other
     * components survive; only our two PDC keys and the first model selector are authoritative.
     */
    fun stamp(type: SpecialItemType, provided: ItemStack): ItemStack {
        require(provided.type == type.material) {
            "${type.id} requires ${type.material}, got ${provided.type}"
        }
        require(provided.amount in 1..provided.maxStackSize) {
            "${type.id} count ${provided.amount} is outside 1..${provided.maxStackSize}"
        }

        val stack = provided.clone()
        val meta = stack.itemMeta ?: error("Missing metadata for ${type.material}")
        meta.persistentDataContainer.set(versionKey, PersistentDataType.INTEGER, 1)
        meta.persistentDataContainer.set(typeKey, PersistentDataType.STRING, type.id)

        val model = meta.customModelDataComponent
        val strings = model.strings.toMutableList()
        if (strings.isEmpty()) strings.add(type.modelMarker) else strings[0] = type.modelMarker
        model.strings = strings
        meta.setCustomModelDataComponent(model)
        stack.itemMeta = meta
        return stack
    }

    /** PDC version, PDC type and the vanilla carrier are required; model/name are not trusted. */
    fun identify(stack: ItemStack?): SpecialItemType? {
        if (stack == null || stack.amount <= 0) return null
        val pdc = stack.itemMeta?.persistentDataContainer ?: return null
        if (pdc.get(versionKey, PersistentDataType.INTEGER) != 1) return null
        val id = pdc.get(typeKey, PersistentDataType.STRING) ?: return null
        return SpecialItemType.entries.firstOrNull { it.id == id && it.material == stack.type }
    }
}
