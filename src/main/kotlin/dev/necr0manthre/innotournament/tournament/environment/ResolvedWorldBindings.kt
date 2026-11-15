package dev.necr0manthre.innotournament.tournament.environment

import org.bukkit.NamespacedKey
import org.bukkit.World
import java.util.UUID

/** Concrete worlds selected before any tournament world effects. */
class ResolvedWorldBindings(
    val preStartWorld: World,
    val gameWorld: World,
    val normalWorld: World,
    val netherWorld: World,
    val endWorld: World
) {
    /** World IDs that belong to this tournament's gameplay environment. */
    val gameplayWorldIds: Set<UUID>
        get() = listOf(gameWorld, normalWorld, netherWorld, endWorld).mapTo(linkedSetOf()) { it.uid }

    companion object {
        /**
         * Resolve configured dimension keys and the three vanilla-related worlds exactly.
         * [worlds] is supplied by the caller; no server or environment-order lookup occurs.
         * For custom related-world compositions, use the explicit-world constructor.
         */
        fun resolve(
            preStartKey: NamespacedKey,
            gameKey: NamespacedKey,
            worlds: Collection<World>
        ): ResolvedWorldBindings {
            fun worldFor(key: NamespacedKey): World {
                val matches = worlds.filter { it.key == key }
                require(matches.isNotEmpty()) { "World '$key' is missing" }
                val world = matches.first()
                require(matches.all { it === world }) { "World key '$key' is ambiguous" }
                return world
            }

            return ResolvedWorldBindings(
                preStartWorld = worldFor(preStartKey),
                gameWorld = worldFor(gameKey),
                normalWorld = worldFor(NamespacedKey.minecraft("overworld")),
                netherWorld = worldFor(NamespacedKey.minecraft("the_nether")),
                endWorld = worldFor(NamespacedKey.minecraft("the_end"))
            )
        }
    }
}
