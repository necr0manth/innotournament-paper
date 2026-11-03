package dev.necr0manthre.innotournament.teams.events

import dev.necr0manthre.innotournament.teams.TeamManager

/** Subscription-only view of one manager's team events. */
class TeamEvents(private val manager: TeamManager) {
    val beforeJoin get() = manager.beforeJoin
    val afterJoin get() = manager.afterJoin
    val beforeLeave get() = manager.beforeLeave
    val afterLeave get() = manager.afterLeave
    val beforeCreate get() = manager.beforeCreate
    val afterCreate get() = manager.afterCreate
    val beforeRemove get() = manager.beforeRemove
    val afterRemove get() = manager.afterRemove
    val beforeRename get() = manager.beforeRename
    val afterRename get() = manager.afterRename
    val beforeOwnerTransfer get() = manager.beforeOwnerTransfer
    val afterOwnerTransfer get() = manager.afterOwnerTransfer
}
