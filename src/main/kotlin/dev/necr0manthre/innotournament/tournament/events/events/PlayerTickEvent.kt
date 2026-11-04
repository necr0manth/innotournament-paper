package dev.necr0manthre.innotournament.tournament.events.events

import com.destroystokyo.paper.event.server.ServerTickEndEvent
import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.events.BukkitEventBasedTournamentEvent
import dev.necr0manthre.innotournament.tournament.events.ValueType
import dev.necr0manthre.innotournament.tournament.events.TournamentEventInstance
import dev.necr0manthre.innotournament.tournament.events.event_data.PlayerHolder
import org.bukkit.Bukkit
import java.util.*

class PlayerTickEvent(val period: Int) :
    BukkitEventBasedTournamentEvent<ServerTickEndEvent, PlayerHolder>(
        ServerTickEndEvent::class,
        ServerTickEndEvent::getHandlerList,
        ValueType.of(PlayerHolder::class)
    ) {
    override fun subscribe(tournament: Tournament): TournamentEventInstance<PlayerHolder> {
        val counter = mutableMapOf<UUID, Int>()
        return subscribeBukkit(tournament) { _, callback ->
            for (player in Bukkit.getServer().onlinePlayers) {
                if (counter.compute(player.uniqueId) { _, t -> ((t ?: -1) + 1) % period } == 0)
                    callback(PlayerHolder(tournament.getPlayer(PluginPlayer.get(player)) ?: continue))
            }
        }
    }
}
