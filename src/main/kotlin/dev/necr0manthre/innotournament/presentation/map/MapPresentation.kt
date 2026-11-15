package dev.necr0manthre.innotournament.presentation.map

import dev.necr0manthre.innotournament.generators.GeneratorKey
import dev.necr0manthre.innotournament.generators.GeneratorPlacementChange
import dev.necr0manthre.innotournament.generators.GeneratorPlacementSource
import dev.necr0manthre.innotournament.generators.GeneratorPlacementSubscription
import dev.necr0manthre.innotournament.generators.GeneratorPosition
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.WorldLoadEvent
import org.bukkit.map.MapView
import org.bukkit.map.MapRenderer
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.util.LinkedHashMap

/** Owns the plugin's published map views and their live generator-marker renderers. */
class MapPresentation(
    private val plugin: Plugin,
    source: GeneratorPlacementSource,
    private val store: MapPresentationStore
) : Listener, AutoCloseable {
    private data class OwnedView(
        val area: MapAreaKey,
        val map: MapView,
        val iconRenderer: GeneratorMapIconRenderer,
        val renderers: List<MapRenderer>
    )

    private val placements = LinkedHashMap<GeneratorPosition, GeneratorKey>()
    private val views = LinkedHashMap<MapAreaKey, OwnedView>()
    private val subscription: GeneratorPlacementSubscription
    private var task: BukkitTask? = null
    private var dirty = true
    private var closed = false

    init {
        check(Bukkit.isPrimaryThread()) { "Map presentation must be created on the primary server thread" }
        subscription = source.subscribe(::onPlacementChange)
        try {
            subscription.initial.forEach { placements[it.position] = it }
            Bukkit.getPluginManager().registerEvents(this, plugin)
            store.records.forEach { record ->
                Bukkit.getWorld(record.area.worldId)?.let { world -> restore(record, world) }
            }
            task = Bukkit.getScheduler().runTaskTimer(plugin, ::updateDirtyViews, 20L, 20L)
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    /** Returns the shared immutable-area view, creating and recording it only when needed. */
    fun getOrCreateView(world: World, area: MapAreaKey): MapView {
        requireOpen()
        check(Bukkit.isPrimaryThread()) { "Map views must be accessed on the primary server thread" }
        require(world.uid == area.worldId) { "Map area world ${area.worldId} does not match ${world.uid}" }

        views[area]?.let { return it.map }
        val existingRecord = store.recordForArea(area)
        if (existingRecord != null) {
            return restore(existingRecord, world).map
        }

        val map = Bukkit.createMap(world)
        map.centerX = area.centerX
        map.centerZ = area.centerZ
        map.scale = MapView.Scale.values()[area.scale]
        store.addRecord(MapPresentationRecord(map.id, area))
        return attach(map, area, world).map
    }

    /** Reattaches persisted views when their exact Bukkit world becomes available. */
    @EventHandler
    fun onWorldLoad(event: WorldLoadEvent) {
        requireOpen()
        store.records.filter { it.area.worldId == event.world.uid }.forEach { restore(it, event.world) }
    }

    override fun close() {
        if (closed) return
        check(Bukkit.isPrimaryThread()) { "Map presentation must close on the primary server thread" }
        closed = true
        task?.cancel()
        task = null
        org.bukkit.event.HandlerList.unregisterAll(this)
        views.values.forEach { owned -> owned.renderers.forEach(owned.map::removeRenderer) }
        views.clear()
        subscription.close()
    }

    private fun restore(record: MapPresentationRecord, world: World): OwnedView {
        require(world.uid == record.area.worldId) {
            "Map ${record.id} belongs to ${record.area.worldId}, not ${world.uid}"
        }
        views[record.area]?.let { owned ->
            check(owned.map.id == record.id) { "Conflicting map IDs for area ${record.area}" }
            return owned
        }

        val map = Bukkit.getMap(record.id)
            ?: error("Published map ${record.id} is missing for area ${record.area}")
        check(map.world?.uid == record.area.worldId) { "Published map ${record.id} has a conflicting world" }
        check(map.centerX == record.area.centerX && map.centerZ == record.area.centerZ) {
            "Published map ${record.id} has a conflicting center"
        }
        check(map.scale.ordinal == record.area.scale) { "Published map ${record.id} has a conflicting scale" }
        return attach(map, record.area, world)
    }

    private fun attach(map: MapView, area: MapAreaKey, world: World): OwnedView {
        views[area]?.let { return it }
        if (world.environment != World.Environment.NETHER) map.setTrackingPosition(true)
        val icons = GeneratorMapIconRenderer()
        val renderers = buildList {
            if (world.environment == World.Environment.NETHER) {
                add(NetherMapBackgroundRenderer())
                add(NetherPlayerCursorRenderer(area))
            }
            // Cursor list order controls which icons cover others, so generator icons come last.
            add(icons)
        }
        val added = ArrayList<MapRenderer>(renderers.size)
        try {
            renderers.forEach { renderer ->
                map.addRenderer(renderer)
                added.add(renderer)
            }
        } catch (failure: Throwable) {
            added.forEach(map::removeRenderer)
            throw failure
        }

        val owned = OwnedView(area, map, icons, renderers)
        views[area] = owned
        dirty = true
        return owned
    }

    private fun onPlacementChange(change: GeneratorPlacementChange) {
        requireOpen()
        when (change) {
            is GeneratorPlacementChange.Discovered -> {
                if (placements[change.key.position] != change.key) {
                    placements[change.key.position] = change.key
                    dirty = true
                }
            }
            is GeneratorPlacementChange.Removed -> {
                if (placements[change.key.position] == change.key) {
                    placements.remove(change.key.position)
                    dirty = true
                }
            }
        }
    }

    private fun updateDirtyViews() {
        if (!dirty) return
        val snapshot = placements.values.toList()
        views.values.forEach { owned ->
            owned.iconRenderer.update(GeneratorMapProjection.icons(snapshot, owned.area))
        }
        dirty = false
    }

    private fun requireOpen() {
        check(!closed) { "Map presentation is closed" }
    }
}
