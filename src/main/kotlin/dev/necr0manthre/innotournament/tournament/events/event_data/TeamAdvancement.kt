package dev.necr0manthre.innotournament.tournament.events.event_data

import dev.necr0manthre.innotournament.tournament.TournamentTeam
import org.bukkit.advancement.Advancement

data class TeamAdvancement(
    override val team: TournamentTeam?,
    val advancement: Advancement? = null
) : ITeamProvider
