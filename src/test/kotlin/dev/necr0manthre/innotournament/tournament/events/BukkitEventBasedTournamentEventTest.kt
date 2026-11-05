package dev.necr0manthre.innotournament.tournament.events

import dev.necr0manthre.innotournament.Innotournament
import dev.necr0manthre.innotournament.teams.PluginTeam
import dev.necr0manthre.innotournament.tournament.testTournament
import org.bukkit.Bukkit
import org.bukkit.Server
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.plugin.EventExecutor
import org.bukkit.plugin.PluginManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy

class BukkitEventBasedTournamentEventTest {
    @Test
    fun ignoresOtherEventsSharingTheHandlerListAndUnsubscribesNormally() {
        var executor: EventExecutor? = null
        var listener: Listener? = null
        var unregisterCalls = 0
        val pluginManager = Proxy.newProxyInstance(
            PluginManager::class.java.classLoader,
            arrayOf(PluginManager::class.java)
        ) { _, method, args ->
            if (method.name == "registerEvent") {
                listener = args!![1] as Listener
                executor = args[3] as EventExecutor
            }
            null
        } as PluginManager
        val server = Proxy.newProxyInstance(
            Server::class.java.classLoader,
            arrayOf(Server::class.java)
        ) { _, method, _ -> if (method.name == "getPluginManager") pluginManager else null } as Server
        val bukkitServerField = Bukkit::class.java.getDeclaredField("server").apply { isAccessible = true }
        val previousServer = bukkitServerField.get(null)
        val pluginField = sequenceOf(Innotournament::class.java, Innotournament.Companion::class.java)
            .flatMap { it.declaredFields.asSequence() }
            .first { it.type == Innotournament::class.java }
            .apply { isAccessible = true }
        val pluginFieldOwner = if (Modifier.isStatic(pluginField.modifiers)) null else Innotournament.Companion
        val previousPlugin = pluginField.get(pluginFieldOwner)
        pluginField.set(pluginFieldOwner, allocate(Innotournament::class.java))
        bukkitServerField.set(null, server)

        try {
            val event = object : BukkitEventBasedTournamentEvent<PlayerDeathEvent, String>(
                PlayerDeathEvent::class,
                { unregisterCalls++; PlayerDeathEvent.getHandlerList() },
                ValueType.of(String::class)
            ) {
                override fun subscribe(tournament: dev.necr0manthre.innotournament.tournament.Tournament) =
                    subscribeBukkit(tournament) { _, callback -> callback("player death") }
            }
            val instance = event.subscribe(testTournament(PluginTeam.TeamManager()))
            val registeredExecutor = checkNotNull(executor)
            val registeredListener = checkNotNull(listener)
            var calls = 0
            instance.addListener(ITournamentEventListener { _, _ -> calls++ })

            registeredExecutor.execute(registeredListener, allocate(EntityDeathEvent::class.java))
            assertEquals(0, calls)

            registeredExecutor.execute(registeredListener, allocate(PlayerDeathEvent::class.java))
            assertEquals(1, calls)

            instance.unsubscribe()
            assertEquals(1, unregisterCalls)
            registeredExecutor.execute(registeredListener, allocate(PlayerDeathEvent::class.java))
            assertEquals(1, calls)
        } finally {
            bukkitServerField.set(null, previousServer)
            pluginField.set(pluginFieldOwner, previousPlugin)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> allocate(type: Class<T>): T {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafeField = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = unsafeField.get(null)
        return unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, type) as T
    }
}
