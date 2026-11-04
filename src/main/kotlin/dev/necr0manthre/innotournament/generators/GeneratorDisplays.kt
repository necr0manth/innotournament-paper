package dev.necr0manthre.innotournament.generators

import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.joml.Matrix4f
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * The visual owner for already registered generators. The caller owns the main-thread tick,
 * loaded chunks and lifecycle: sync after registration/slot changes, animate each server tick,
 * remove before unregister/unload, and close on plugin disable. No placement state lives here.
 */
class GeneratorDisplays(private val items: GeneratorItems) : AutoCloseable {
    private val ownerKey = NamespacedKey("innotournament", "generator_display_owner")
    private val roleKey = NamespacedKey("innotournament", "generator_display_role")
    private val positionKey = NamespacedKey("innotournament", "generator_display_position")
    private val active = LinkedHashMap<GeneratorPosition, Shown>()

    private class Shown(
        val key: GeneratorKey,
        val world: World,
        val body: ItemDisplay
    ) {
        val slots = arrayOfNulls<ItemDisplay>(10)
    }

    /** Render a detached domain snapshot; repeated calls for the same key reuse the displays. */
    fun sync(world: World, instance: GeneratorInstance) {
        val key = instance.key
        val position = key.position
        require(world.uid == position.worldId) { "World does not match generator at $position" }
        check(world.isChunkLoaded(position.x shr 4, position.z shr 4)) {
            "Generator chunk must already be loaded at $position"
        }

        val contents = instance.slots // detached clones from ItemGeneratorSystem
        check(contents.size == 10) { "Generator snapshot must contain ten slots at $position" }
        val shown = active[position] ?: run {
            // A fresh owner can replace only its own orphaned displays at this exact placement.
            clearTaggedAt(world, position)
            val bodyItem = items.create(when (key.type) {
                GeneratorType.ITEM_GENERATOR -> SpecialItemType.ITEM_GENERATOR
                GeneratorType.BOOK_GENERATOR -> SpecialItemType.BOOK_GENERATOR
                GeneratorType.NETHER_GENERATOR -> SpecialItemType.NETHER_GENERATOR
            })
            val center = Location(world, position.x + 0.5, position.y + 0.5, position.z + 0.5)
            val body = spawn(world, center, position, "body", bodyItem, ItemDisplay.ItemDisplayTransform.NONE)
            Shown(key, world, body).also { active[position] = it }
        }
        check(shown.key == key) { "Conflicting generator type at $position" }
        check(isOwned(shown.body, position, "body")) { "Lost generator body at $position" }

        contents.forEachIndexed { index, stack ->
            val role = "slot:$index"
            val old = shown.slots[index]
            if (old != null) check(isOwned(old, position, role)) { "Lost generator display $role at $position" }
            when {
                stack == null && old != null -> {
                    old.remove()
                    shown.slots[index] = null
                }
                stack != null && old == null -> {
                    val base = Location(world, position.x + 0.5, position.y + height(key.type), position.z + 0.5)
                    shown.slots[index] = spawn(
                        world, base, position, role, stack, ItemDisplay.ItemDisplayTransform.GROUND
                    )
                }
                stack != null && old != null && old.itemStack != stack -> old.setItemStack(stack.clone())
            }
        }
        animate(shown)
    }

    /** Called by the adapter's existing synchronous tick; does not schedule its own task. */
    fun animate() {
        active.values.forEach { animate(it) }
    }

    /** A stale key never removes another type placed at the same coordinates. */
    fun remove(key: GeneratorKey): Boolean {
        val shown = active[key.position] ?: return false
        if (shown.key != key) return false
        active.remove(key.position)
        removeOwned(shown)
        return true
    }

    override fun close() {
        active.values.forEach(::removeOwned)
        active.clear()
    }

    private fun animate(shown: Shown) {
        val count = shown.slots.count { it != null }
        if (count == 0) return
        val time = shown.world.fullTime.toDouble()
        var visualIndex = 0
        shown.slots.forEach { display ->
            if (display == null) return@forEach
            // Compact occupied slots around the ring; empty holes must not overlap items.
            val ordinal = visualIndex++
            val bob = (sin(time / 8.0 + 2.0 * PI * ordinal / count) * 0.1).toFloat()
            val transform = if (count == 1) {
                Matrix4f()
                    .rotateY(Math.toRadians(time % 360.0).toFloat())
                    .translate(0f, bob, 0f)
            } else {
                val scale = max(1.15f - 0.15f * count, 0.6f)
                Matrix4f()
                    .rotateY(Math.toRadians(time % 360.0 + ordinal * 360.0 / count).toFloat())
                    .translate(0.5f, 0f, 0f)
                    .rotateY(Math.toRadians(time * 4.0 % 360.0).toFloat())
                    .scale(scale)
                    .translate(0f, bob, 0f)
            }
            // Original ItemGeneratorRenderer used GROUND context and the same transform order.
            display.setTransformationMatrix(transform)
            display.setInterpolationDelay(0)
            display.setInterpolationDuration(1)
        }
    }

    private fun spawn(
        world: World,
        location: Location,
        position: GeneratorPosition,
        role: String,
        item: ItemStack,
        transform: ItemDisplay.ItemDisplayTransform
    ): ItemDisplay = world.spawn(location, ItemDisplay::class.java) { display ->
        // The Paper spawn initializer runs *before* insertion into the world.
        display.setPersistent(false)
        display.persistentDataContainer.set(ownerKey, PersistentDataType.STRING, "innotournament:generator_displays")
        display.persistentDataContainer.set(roleKey, PersistentDataType.STRING, role)
        display.persistentDataContainer.set(positionKey, PersistentDataType.STRING, marker(position))
        display.setBillboard(Display.Billboard.FIXED)
        display.setItemDisplayTransform(transform)
        display.setItemStack(item.clone())
        // ItemDisplay NONE renders the 0..16 block-model cube centered on this block center.
        if (role == "body") display.setTransformationMatrix(Matrix4f())
    }

    private fun height(type: GeneratorType): Double = when (type) {
        GeneratorType.ITEM_GENERATOR -> 0.7
        GeneratorType.BOOK_GENERATOR -> 0.6
        GeneratorType.NETHER_GENERATOR -> 1.0
    }

    private fun marker(position: GeneratorPosition): String =
        "${position.worldId}:${position.x}:${position.y}:${position.z}"

    private fun isOwned(display: ItemDisplay, position: GeneratorPosition, role: String): Boolean {
        if (!display.isValid) return false
        val pdc = display.persistentDataContainer
        return pdc.get(ownerKey, PersistentDataType.STRING) == "innotournament:generator_displays" &&
            pdc.get(positionKey, PersistentDataType.STRING) == marker(position) &&
            pdc.get(roleKey, PersistentDataType.STRING) == role
    }

    private fun removeOwned(shown: Shown) {
        val position = shown.key.position
        if (isOwned(shown.body, position, "body")) shown.body.remove()
        shown.slots.forEachIndexed { index, display ->
            if (display != null && isOwned(display, position, "slot:$index")) display.remove()
        }
    }

    private fun clearTaggedAt(world: World, position: GeneratorPosition) {
        val center = Location(world, position.x + 0.5, position.y + 0.5, position.z + 0.5)
        world.getNearbyEntities(center, 0.75, 1.25, 0.75).forEach { entity ->
            if (entity !is ItemDisplay) return@forEach
            if (isOwned(entity, position, "body") ||
                (0 until 10).any { isOwned(entity, position, "slot:$it") }
            ) entity.remove()
        }
    }
}
