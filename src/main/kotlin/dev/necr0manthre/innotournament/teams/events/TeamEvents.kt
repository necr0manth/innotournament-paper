package dev.necr0manthre.innotournament.teams.events

import dev.necr0manthre.innotournament.teams.PluginTeam

/** Subscription-only compatibility facade; PluginTeam owns every backing event. */
object TeamEvents {
    val beforeJoin get() = PluginTeam.beforeJoin
    val afterJoin get() = PluginTeam.afterJoin
    val beforeLeave get() = PluginTeam.beforeLeave
    val afterLeave get() = PluginTeam.afterLeave
    val beforeCreate get() = PluginTeam.beforeCreate
    val afterCreate get() = PluginTeam.afterCreate
    val beforeRemove get() = PluginTeam.beforeRemove
    val afterRemove get() = PluginTeam.afterRemove
    val beforeRename get() = PluginTeam.beforeRename
    val afterRename get() = PluginTeam.afterRename
    val beforeOwnerTransfer get() = PluginTeam.beforeOwnerTransfer
    val afterOwnerTransfer get() = PluginTeam.afterOwnerTransfer
}
