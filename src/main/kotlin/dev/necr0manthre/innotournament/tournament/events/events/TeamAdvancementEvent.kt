package dev.necr0manthre.innotournament.tournament.events.events

import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.FirstTeamAdvancement
import dev.necr0manthre.innotournament.tournament.events.SimpleEvent
import dev.necr0manthre.innotournament.tournament.events.TournamentEvent
import dev.necr0manthre.innotournament.tournament.events.ValueType
import dev.necr0manthre.innotournament.tournament.events.TournamentEventInstance
import dev.necr0manthre.innotournament.tournament.events.event_data.TeamAdvancement
import org.bukkit.Bukkit
import org.bukkit.advancement.Advancement

class TeamAdvancementEvent(private val advancement: Advancement) : TournamentEvent<TeamAdvancement> {
    override val valueType = ValueType.of(TeamAdvancement::class)
    override fun subscribe(tournament: Tournament): TournamentEventInstance<TeamAdvancement> {
        val backingEvent = SimpleEvent<TeamAdvancement>()
        var handler: ((FirstTeamAdvancement) -> Unit)? = { event ->
            if (event.key == advancement.key) {
                backingEvent.invoke(TeamAdvancement(event.team, Bukkit.getAdvancement(event.key)))
            }
        }
        tournament.firstTeamAdvancements.subscribe(handler!!)
        return TournamentEventInstance(tournament, backingEvent) {
            handler?.let(tournament.firstTeamAdvancements::unsubscribe)
            handler = null
        }
    }
}
