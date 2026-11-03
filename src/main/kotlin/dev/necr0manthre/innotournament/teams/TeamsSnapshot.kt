package dev.necr0manthre.innotournament.teams

import net.kyori.adventure.text.Component
import java.util.Collections
import java.util.UUID

/** Immutable transport data for a future storage codec; no persistence is performed here. */
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
