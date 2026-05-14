package dev.necr0manthre.innotournament.tournament.events.events

import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.events.ITeamAdvancementEventHandler
import dev.necr0manthre.innotournament.tournament.events.TournamentEvent
import dev.necr0manthre.innotournament.tournament.events.event_data.TeamAdvancement
import java.util.function.Consumer

class TeamAchievementEvent(private val achievement: String?) :
    TournamentEvent<TeamAdvancement?, ITeamAdvancementEventHandler?>() {
    override fun subscribeInternal(
        tournament: Tournament,
        callback: Consumer<TeamAdvancement?>
    ): ITeamAdvancementEventHandler {
        val handler = ITeamAdvancementEventHandler { event: TeamAdvancement? ->
            if (event.getAdvancement().id().toString().equals(achievement)) {
                callback.accept(event)
            }
        }
        tournament.advancementHandler.teamAdvancementEvent.register(handler)
        return handler
    }

    override fun removeInternal(
        tournament: Tournament,
        callback: Consumer<TeamAdvancement?>?,
        obj: ITeamAdvancementEventHandler?
    ) {
        tournament.advancementHandler.teamAdvancementEvent.unregister(obj)
    }
}
