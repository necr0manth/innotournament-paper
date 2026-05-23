package dev.necr0manthre.innotournament.tournament.events.events

import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.TournamentPlayer
import dev.necr0manthre.innotournament.tournament.events.BukkitEventBasedTournamentEvent
import dev.necr0manthre.innotournament.tournament.events.event_data.PlayerHolder
import org.bukkit.advancement.Advancement
import org.bukkit.event.player.PlayerAdvancementDoneEvent

class AdvancementEvent(private val advancement: Advancement, tournament: Tournament) : BukkitEventBasedTournamentEvent<PlayerAdvancementDoneEvent, PlayerHolder>(
    PlayerAdvancementDoneEvent::class,
    PlayerAdvancementDoneEvent::getHandlerList,
    tournament,
) {
    override fun handle(
        event: PlayerAdvancementDoneEvent,
        callback: (PlayerHolder) -> Unit
    ) {
        if(event.advancement == advancement)
            callback(PlayerHolder(TournamentPlayer.get(event.player)))
    }
}
