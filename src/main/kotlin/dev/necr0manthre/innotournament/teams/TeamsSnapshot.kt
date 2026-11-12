package dev.necr0manthre.innotournament.teams

import net.kyori.adventure.text.Component
import java.util.Collections
import java.util.UUID

/** Immutable snapshot data shared by TeamManager and the persistence codec. */
class TeamSnapshot(
    val id: String,
    val displayName: Component,
    val acceptAll: Boolean,
    members: List<UUID>,
    val ownerUuid: UUID?
) {
    val members: List<UUID> = Collections.unmodifiableList(ArrayList(members))
}

class TeamsSnapshot(val version: Int = 1, teams: List<TeamSnapshot>) {
    val teams: List<TeamSnapshot> = Collections.unmodifiableList(ArrayList(teams))
}
