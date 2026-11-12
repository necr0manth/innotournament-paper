package dev.necr0manthre.innotournament.generators

import net.kyori.adventure.nbt.ByteBinaryTag
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.kyori.adventure.nbt.IntBinaryTag
import net.kyori.adventure.nbt.LongBinaryTag
import net.kyori.adventure.nbt.NumberBinaryTag
import net.kyori.adventure.nbt.ShortBinaryTag
import net.kyori.adventure.nbt.StringBinaryTag
import net.kyori.adventure.nbt.TagStringIO
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemFactory
import org.bukkit.inventory.ItemStack
import java.util.Locale

/** Converts one original probability key (vanilla ID, special ID, or SNBT item envelope). */
class GeneratorItemResolver(
    private val itemFactory: ItemFactory = Bukkit.getItemFactory(),
    private val generatorItems: GeneratorItems = GeneratorItems()
) {
    /** The profile reader adds its file/level/key context to any [IllegalArgumentException]. */
    fun resolve(sourceKey: String): ItemStack {
        val item = parse(sourceKey)
        try {
            // A special ID never enters the vanilla registry. With no components, use its
            // named factory prototype; otherwise stamp the configured vanilla carrier.
            val stack = if (item.special != null && !item.hasComponents) {
                generatorItems.create(item.special, item.count)
            } else {
                val parsed = itemFactory.createItemStack(item.argument)
                require(parsed.type != Material.AIR) { "resolved to AIR" }
                require(item.count <= parsed.maxStackSize) {
                    "count ${item.count} exceeds stack limit ${parsed.maxStackSize}"
                }
                parsed.amount = item.count
                if (item.special != null) generatorItems.stamp(item.special, parsed) else parsed
            }
            require(stack.type != Material.AIR && stack.amount in 1..stack.maxStackSize) {
                "invalid resulting stack (${stack.type}, count ${stack.amount})"
            }
            return stack
        } catch (cause: RuntimeException) {
            throw IllegalArgumentException("Generator item '$sourceKey': ${cause.message}", cause)
        }
    }

    private data class ItemArgument(
        val argument: String,
        val count: Int,
        val special: SpecialItemType?,
        val hasComponents: Boolean
    )

    private fun parse(sourceKey: String): ItemArgument {
        val input = sourceKey.trim()
        if (input.isEmpty()) invalid(sourceKey, "empty item ID")

        val id: String
        val count: Int
        val components: CompoundBinaryTag
        if (input.startsWith("{")) {
            val envelope = try {
                SNBT.asCompound(input)
            } catch (cause: Exception) {
                throw IllegalArgumentException("Generator item '$sourceKey': malformed SNBT envelope", cause)
            }
            val unknown = envelope.keySet() - ENVELOPE_FIELDS
            if (unknown.isNotEmpty()) invalid(sourceKey, "unknown SNBT fields: $unknown")
            id = (envelope.get("id") as? StringBinaryTag)?.value()
                ?: invalid(sourceKey, "SNBT id must be a string")
            count = when (val tag = envelope.get("count")) {
                null -> 1
                is ByteBinaryTag, is ShortBinaryTag, is IntBinaryTag, is LongBinaryTag -> {
                    val value = (tag as NumberBinaryTag).longValue()
                    if (value !in 1..Int.MAX_VALUE.toLong()) invalid(sourceKey, "invalid count $value")
                    value.toInt()
                }
                else -> invalid(sourceKey, "SNBT count must be a positive integer")
            }
            components = when (val tag = envelope.get("components")) {
                null -> CompoundBinaryTag.empty()
                is CompoundBinaryTag -> tag
                else -> invalid(sourceKey, "SNBT components must be a compound")
            }
        } else {
            id = input
            count = 1
            components = CompoundBinaryTag.empty()
        }

        val normalized = normalizeId(id, sourceKey)
        val special = SpecialItemType.fromId(id)
        if (id.startsWith("innotournament:") && special == null) {
            invalid(sourceKey, "unknown special item '$id'")
        }
        val carrier = if (special == null) normalized else "minecraft:${special.material.name.lowercase(Locale.ROOT)}"
        val componentIds = HashSet<String>()
        val arguments = components.keySet().map { name ->
            val componentId = normalizeId(name, sourceKey)
            if (!componentIds.add(componentId)) invalid(sourceKey, "duplicate component '$componentId'")
            val serialized = try {
                SNBT.asString(components.get(name)!!)
            } catch (cause: Exception) {
                throw IllegalArgumentException("Generator item '$sourceKey': cannot serialize component '$componentId'", cause)
            }
            "$componentId=$serialized"
        }
        val argument = if (arguments.isEmpty()) carrier else "$carrier[${arguments.joinToString(",")}]"
        return ItemArgument(argument, count, special, arguments.isNotEmpty())
    }

    private fun normalizeId(id: String, sourceKey: String): String {
        val valid = if (':' in id) NAMESPACED_ID.matches(id) else SIMPLE_ID.matches(id)
        if (!valid) invalid(sourceKey, "invalid ID '$id'")
        return if (':' in id) id else "minecraft:$id"
    }

    private fun invalid(sourceKey: String, reason: String): Nothing =
        throw IllegalArgumentException("Generator item '$sourceKey': $reason")

    companion object {
        private val ENVELOPE_FIELDS = setOf("id", "count", "components")
        private val SIMPLE_ID = Regex("[a-z0-9_./-]+")
        private val NAMESPACED_ID = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")
        // The same configured codec reads the envelope and emits every parsed component tag.
        private val SNBT = TagStringIO.builder()
            .acceptHeterogeneousLists(true)
            .emitHeterogeneousLists(true)
            .build()
    }
}
