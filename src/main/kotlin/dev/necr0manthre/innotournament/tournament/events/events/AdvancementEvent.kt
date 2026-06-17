package dev.necr0manthre.innotournament.tournament.events.events

import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.events.BukkitEventBasedTournamentEvent
import dev.necr0manthre.innotournament.tournament.events.event_data.PlayerHolder
import org.bukkit.advancement.Advancement
import org.bukkit.event.player.PlayerAdvancementDoneEvent

class AdvancementEvent(private val advancement: Advancement) : BukkitEventBasedTournamentEvent<PlayerAdvancementDoneEvent, PlayerHolder>(
    PlayerAdvancementDoneEvent::class,
    PlayerAdvancementDoneEvent::getHandlerList
) {
    override fun handle(
        tournament: Tournament,
        event: PlayerAdvancementDoneEvent,
        callback: (PlayerHolder) -> Unit
    ) {
        if(event.advancement == advancement)
            callback(PlayerHolder(tournament.getPlayer(event.player)))
    }
}
