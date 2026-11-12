package dev.necr0manthre.innotournament.presentation.map

import dev.necr0manthre.innotournament.generators.GeneratorKey
import dev.necr0manthre.innotournament.generators.GeneratorPlacementChange
import dev.necr0manthre.innotournament.generators.GeneratorPlacementSource
import dev.necr0manthre.innotournament.generators.GeneratorPlacementSubscription
import dev.necr0manthre.innotournament.generators.GeneratorPosition
import org.bukkit.World
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.Plugin
import java.util.LinkedHashMap

/** Delivers the generator snapshot to optional JourneyMap clients through Paper's plugin messaging. */
class JourneyMapDelivery(
    private val plugin: Plugin,
    source: GeneratorPlacementSource,
    private val store: MapPresentationStore,
    private val enabled: Boolean
) : Listener, AutoCloseable {
    private val server = plugin.server
    private val placements = LinkedHashMap<GeneratorPosition, GeneratorKey>()
    private var subscription: GeneratorPlacementSubscription? = null
    private var registered = false
    private var channelRegistered = false
    private var closed = false

    init {
        check(server.isPrimaryThread) { "JourneyMap delivery must be created on the primary server thread" }
        try {
            subscription = source.subscribe(::onPlacementChange)
            subscription!!.initial.forEach { placements[it.position] = it }
            channelRegistered = true
            server.messenger.registerOutgoingPluginChannel(plugin, JourneyMapWaypointMessages.CHANNEL)
            registered = true
            server.pluginManager.registerEvents(this, plugin)
            refresh(server.onlinePlayers.toList())
        } catch (failure: Throwable) {
            closeAfterFailedStart(failure)
            throw failure
        }
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        requireOpen()
        refresh(listOf(event.player))
    }

    @EventHandler
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        requireOpen()
        refresh(listOf(event.player))
    }

    override fun close() {
        if (closed) return
        check(server.isPrimaryThread) { "JourneyMap delivery must close on the primary server thread" }
        closed = true
        var failure: Throwable? = null
        fun release(action: () -> Unit) {
            try {
                action()
            } catch (caught: Throwable) {
                val prior = failure
                if (prior == null) failure = caught else prior.addSuppressed(caught)
            }
        }

        if (registered) {
            release { org.bukkit.event.HandlerList.unregisterAll(this) }
            registered = false
        }
        if (channelRegistered) {
            release { server.messenger.unregisterOutgoingPluginChannel(plugin, JourneyMapWaypointMessages.CHANNEL) }
            channelRegistered = false
        }
        subscription?.let { value -> release { value.close() } }
        subscription = null
        failure?.let { throw it }
    }

    private fun refresh(players: List<org.bukkit.entity.Player>) {
        requireOpen()
        players.forEach { player ->
            store.publishedWaypointNames
                .filter(::isOwnedManagedName)
                .forEach { send(player, JourneyMapWaypointMessages.delete(it)) }
        }
        if (!enabled) return

        val snapshots = players.associateWith { player ->
            placements.values.filter { it.position.worldId == player.world.uid }
        }
        rememberBeforeCreate(snapshots.values.flatten())
        snapshots.forEach { (player, snapshot) ->
            snapshot.forEach { key ->
                send(player, JourneyMapWaypointMessages.create(key, player.world.key))
            }
        }
    }

    private fun onPlacementChange(change: GeneratorPlacementChange) {
        requireOpen()
        when (change) {
            is GeneratorPlacementChange.Discovered -> {
                val old = placements[change.key.position]
                if (old == change.key) return
                placements[change.key.position] = change.key
                if (!enabled) return
                val name = JourneyMapWaypointMessages.managedName(change.key.position)
                store.rememberWaypointNames(listOf(name))
                server.onlinePlayers
                    .filter { it.world.uid == change.key.position.worldId }
                    .forEach { send(it, JourneyMapWaypointMessages.create(change.key, it.world.key)) }
            }
            is GeneratorPlacementChange.Removed -> {
                if (placements[change.key.position] != change.key) return
                placements.remove(change.key.position)
                if (!enabled) return
                val name = JourneyMapWaypointMessages.managedName(change.key.position)
                server.onlinePlayers
                    .filter { it.world.uid == change.key.position.worldId }
                    .forEach { send(it, JourneyMapWaypointMessages.delete(name)) }
            }
        }
    }

    private fun rememberBeforeCreate(snapshot: List<GeneratorKey>) {
        val names = snapshot.map { JourneyMapWaypointMessages.managedName(it.position) }
        store.rememberWaypointNames(names)
    }

    private fun send(player: org.bukkit.entity.Player, message: ByteArray) {
        player.sendPluginMessage(plugin, JourneyMapWaypointMessages.CHANNEL, message)
    }

    private fun isOwnedManagedName(name: String): Boolean = MANAGED_NAME.matches(name)

    private fun requireOpen() {
        check(!closed) { "JourneyMap delivery is closed" }
    }

    private fun closeAfterFailedStart(failure: Throwable) {
        try {
            close()
        } catch (cleanupFailure: Throwable) {
            failure.addSuppressed(cleanupFailure)
        }
    }

    private companion object {
        val MANAGED_NAME = Regex(
            "innotournament_[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}_-?\\d+_-?\\d+_-?\\d+"
        )
    }
}
