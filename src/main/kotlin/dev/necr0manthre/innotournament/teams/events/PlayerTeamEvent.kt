package dev.necr0manthre.innotournament.teams.events

import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.teams.PluginTeam
import net.kyori.adventure.text.Component

/** Pre-operation events are cancellable, and are only published before a mutation. */
data class PlayerTeamEvent(
    val player: PluginPlayer,
    val team: PluginTeam,
    val sizeBefore: Int,
    var canceled: Boolean = false
)

/** Frozen facts about one completed join or leave (including a bulk delete's leaves). */
data class TeamChange(
    val player: PluginPlayer,
    val team: PluginTeam,
    val sizeBefore: Int,
    val sizeAfter: Int
)

data class TeamCreateEvent(
    val creator: PluginPlayer,
    val id: String,
    val displayName: Component,
    var canceled: Boolean = false
)

data class TeamCreated(val creator: PluginPlayer, val team: PluginTeam)

data class TeamRemoveEvent(val team: PluginTeam, var canceled: Boolean = false)

data class TeamRenameEvent(
    val team: PluginTeam,
    val oldId: String,
    val newId: String,
    val oldName: Component,
    val newName: Component,
    var canceled: Boolean = false
)

data class TeamRenameChange(
    val team: PluginTeam,
    val oldId: String,
    val newId: String,
    val oldName: Component,
    val newName: Component
)

data class TeamOwnerTransferEvent(
    val team: PluginTeam,
    val oldOwner: PluginPlayer?,
    val newOwner: PluginPlayer,
    var canceled: Boolean = false
)

data class TeamOwnerChange(
    val team: PluginTeam,
    val oldOwner: PluginPlayer?,
    val newOwner: PluginPlayer
)
