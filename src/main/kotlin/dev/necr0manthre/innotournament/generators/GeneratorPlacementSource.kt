package dev.necr0manthre.innotournament.generators

import java.util.Collections

/**
 * Read-only placement facts for MapPresentation and other views. Use on the main server thread.
 * Subscribing returns a snapshot and installs the change callback in the same call, so a view
 * can apply [GeneratorPlacementSubscription.initial] before processing subsequent callbacks.
 * Only placements first encountered in loaded chunks are known; later unload is not removal.
 */
interface GeneratorPlacementSource {
    fun subscribe(onChange: (GeneratorPlacementChange) -> Unit): GeneratorPlacementSubscription
}

/** A placed generator was discovered, or its owner completed physical removal. */
sealed interface GeneratorPlacementChange {
    data class Discovered(val key: GeneratorKey) : GeneratorPlacementChange
    data class Removed(val key: GeneratorKey) : GeneratorPlacementChange
}

/** A detached initial snapshot and a listener handle. Close on the main server thread. */
class GeneratorPlacementSubscription(
    val initial: List<GeneratorKey>,
    private val unsubscribe: () -> Unit
) : AutoCloseable {
    private var closed = false

    override fun close() {
        if (closed) return
        unsubscribe()
        closed = true
    }
}

/**
 * Placement lifecycle's private mutable observation journal; never exposed to map consumers.
 * No PDC, ItemStacks or Bukkit worlds are retained. The lifecycle reports only completed owner
 * actions. Discovery after an ordinary chunk/world unload is idempotent, not a new placement.
 */
class GeneratorPlacementObservations : GeneratorPlacementSource, AutoCloseable {
    private val observed = LinkedHashMap<GeneratorPosition, GeneratorKey>()
    private val listeners = LinkedHashMap<Long, (GeneratorPlacementChange) -> Unit>()
    private var nextListenerId = 0L
    private var closed = false

    override fun subscribe(onChange: (GeneratorPlacementChange) -> Unit): GeneratorPlacementSubscription {
        check(!closed) { "Generator placement observations are closed" }
        val id = nextListenerId++
        listeners[id] = onChange
        return GeneratorPlacementSubscription(Collections.unmodifiableList(observed.values.toList())) {
            listeners.remove(id)
        }
    }

    /** Publish each completed chunk before discovering the next; a later corrupt chunk may throw. */
    fun <T> observeLoadedChunks(chunks: Iterable<T>, discoverChunk: (T) -> List<GeneratorKey>) {
        chunks.forEach { chunk ->
            val keys = discoverChunk(chunk)
            keys.forEach(::discovered)
        }
    }

    /** Called only after the runtime has activated a validated PDC placement and its displays. */
    fun discovered(key: GeneratorKey) {
        check(!closed) { "Generator placement observations are closed" }
        val previous = observed[key.position]
        check(previous == null || previous == key) {
            "Generator placement type changed without confirmed removal: old=$previous new=$key"
        }
        if (previous == key) return
        observed[key.position] = key
        publish(GeneratorPlacementChange.Discovered(key))
    }

    /** Called only after the owner has removed PDC, runtime and the physical block. */
    fun removed(key: GeneratorKey) {
        check(!closed) { "Generator placement observations are closed" }
        check(observed[key.position] == key) {
            "Removing an unobserved or different generator placement: $key"
        }
        observed.remove(key.position)
        publish(GeneratorPlacementChange.Removed(key))
    }

    private fun publish(change: GeneratorPlacementChange) {
        // Unsubscribing during a callback must prevent later delivery in this same dispatch.
        listeners.keys.toList().forEach { id -> listeners[id]?.invoke(change) }
    }

    /** Shutdown is not a confirmed generator removal; do not emit Removed events. */
    override fun close() {
        if (closed) return
        closed = true
        listeners.clear()
        observed.clear()
    }
}
