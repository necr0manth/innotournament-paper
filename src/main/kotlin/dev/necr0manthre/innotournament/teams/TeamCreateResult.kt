package dev.necr0manthre.innotournament.teams

/** Expected creation refusals never publish a partial team. */
sealed interface TeamCreateResult {
    data class Created(val team: PluginTeam) : TeamCreateResult
    data object ALREADY_IN_TEAM : TeamCreateResult
    data object ID_ALREADY_EXISTS : TeamCreateResult
    data object CANCELLED : TeamCreateResult
}
