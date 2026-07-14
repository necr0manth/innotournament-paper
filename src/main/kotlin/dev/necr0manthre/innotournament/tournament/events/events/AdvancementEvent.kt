package dev.necr0manthre.innotournament.tournament.events.events

import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.events.BukkitEventBasedTournamentEvent
import dev.necr0manthre.innotournament.tournament.events.TournamentEventInstance
import dev.necr0manthre.innotournament.tournament.events.event_data.PlayerHolder
import org.bukkit.advancement.Advancement
import org.bukkit.event.player.PlayerAdvancementDoneEvent

class AdvancementEvent(private val advancement: Advancement) :
    BukkitEventBasedTournamentEvent<PlayerAdvancementDoneEvent, PlayerHolder>(
        PlayerAdvancementDoneEvent::class,
        PlayerAdvancementDoneEvent::getHandlerList
    ) {
    override fun subscribe(tournament: Tournament): TournamentEventInstance<PlayerHolder> {
        return subscribeBukkit(tournament) { event, callback ->
            callback(PlayerHolder(tournament.getPlayer(PluginPlayer.get(event.player)) ?: return@subscribeBukkit))
        }
    }
}
