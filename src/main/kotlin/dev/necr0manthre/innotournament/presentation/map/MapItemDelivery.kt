package dev.necr0manthre.innotournament.presentation.map

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.MapMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import java.util.UUID

enum class MapDeliveryResult { DELIVERED, ALREADY_CURRENT, DROPPED, UNAVAILABLE }

/** Delivers the shared current-area map without changing any other player inventory state. */
class MapItemDelivery(
    private val plugin: Plugin,
    private val presentation: MapPresentation
) {
    private val purposeKey = NamespacedKey(plugin, "map-purpose")
    private val worldKey = NamespacedKey(plugin, "map-world")
    private val currentAreas = LinkedHashMap<UUID, MapAreaKey>()

    /** Replaces the frozen map areas selected by the external tournament lifecycle owner. */
    fun setCurrentAreas(areas: Collection<MapAreaKey>) {
        check(Bukkit.isPrimaryThread()) { "Map areas must be selected on the primary server thread" }
        require(areas.map { it.worldId }.distinct().size == areas.size) {
            "Only one current map area may be selected per world"
        }
        currentAreas.clear()
        areas.forEach { currentAreas[it.worldId] = it }
    }

    /** Clears only the selection for future delivery; previously published maps remain available. */
    fun clearCurrentAreas() {
        check(Bukkit.isPrimaryThread()) { "Map areas must be cleared on the primary server thread" }
        currentAreas.clear()
    }

    fun ensureCurrentWorldMap(player: Player): MapDeliveryResult {
        check(Bukkit.isPrimaryThread()) { "Maps must be delivered on the primary server thread" }
        val world = player.world
        val area = currentAreas[world.uid] ?: return MapDeliveryResult.UNAVAILABLE
        val view = presentation.getOrCreateView(world, area)
        if (player.inventory.storageContents.any { it.isCurrentMap(area.worldId, view.id) } ||
            player.inventory.itemInOffHand.isCurrentMap(area.worldId, view.id)
        ) return MapDeliveryResult.ALREADY_CURRENT

        val map = ItemStack(Material.FILLED_MAP)
        val meta = map.itemMeta as MapMeta
        meta.setMapView(view)
        meta.displayName(Component.text("Generator Map", NamedTextColor.GOLD))
        meta.lore(
            listOf(
                Component.text("Yellow: ordinary generator", NamedTextColor.YELLOW),
                Component.text("Cyan: book generator", NamedTextColor.AQUA),
                Component.text("Red: Nether generator", NamedTextColor.RED),
                Component.text("Resolution: ${1 shl area.scale} blocks per pixel", NamedTextColor.GRAY)
            )
        )
        meta.persistentDataContainer.set(purposeKey, PersistentDataType.STRING, MAP_PURPOSE)
        meta.persistentDataContainer.set(worldKey, PersistentDataType.STRING, area.worldId.toString())
        map.itemMeta = meta

        val overflow = player.inventory.addItem(map).values
        overflow.forEach { player.world.dropItemNaturally(player.location, it) }
        return if (overflow.isEmpty()) MapDeliveryResult.DELIVERED else MapDeliveryResult.DROPPED
    }

    private fun ItemStack?.isCurrentMap(worldId: UUID, mapId: Int): Boolean {
        if (this == null || type != Material.FILLED_MAP) return false
        val meta = itemMeta as? MapMeta ?: return false
        return meta.persistentDataContainer.get(purposeKey, PersistentDataType.STRING) == MAP_PURPOSE &&
            meta.persistentDataContainer.get(worldKey, PersistentDataType.STRING) == worldId.toString() &&
            meta.hasMapView() &&
            meta.mapView?.id == mapId
    }

    companion object {
        private const val MAP_PURPOSE = "generator-map"
    }
}
