package dev.necr0manthre.innotournament.tournament.events.events

import com.destroystokyo.paper.event.server.ServerTickEndEvent
import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.events.BukkitEventBasedTournamentEvent
import dev.necr0manthre.innotournament.tournament.events.event_data.PlayerHolder
import org.bukkit.Bukkit
import java.util.*

class PlayerTickEvent(tournament: Tournament, val period: Int) :
    BukkitEventBasedTournamentEvent<ServerTickEndEvent, PlayerHolder>(
        ServerTickEndEvent::class,
        ServerTickEndEvent::getHandlerList,
        tournament
    ) {
    private val counter = mutableMapOf<UUID, Int>()
    override fun handle(
        event: ServerTickEndEvent,
        callback: (PlayerHolder) -> Unit
    ) {
        for (player in Bukkit.getServer().onlinePlayers) {
            if (counter.compute(player.uniqueId) { _, t -> ((t ?: -1) + 1) % period } == 0)
                callback(PlayerHolder(tournament.getPlayer(player)))
        }
    }
}
