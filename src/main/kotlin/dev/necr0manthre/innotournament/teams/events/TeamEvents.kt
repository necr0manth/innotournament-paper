package dev.necr0manthre.innotournament.teams.events

import dev.necr0manthre.innotournament.teams.PluginTeam
import dev.necr0manthre.innotournament.tournament.events.EventSubscriptionView
import dev.necr0manthre.innotournament.tournament.events.SimpleEvent
import dev.necr0manthre.innotournament.tournament.events.asSubscriptionView

object TeamEvents {
    private val beforeJoinEvent = SimpleEvent<PlayerTeamEvent>()
    private val afterJoinEvent = SimpleEvent<PlayerTeamEvent>()
    private val beforeLeaveEvent = SimpleEvent<PlayerTeamEvent>()
    private val afterLeaveEvent = SimpleEvent<PlayerTeamEvent>()
    private val teamRemoveEvent = SimpleEvent<PluginTeam>()

    val beforeJoin: EventSubscriptionView<PlayerTeamEvent> = beforeJoinEvent.asSubscriptionView()
    val afterJoin: EventSubscriptionView<PlayerTeamEvent> = afterJoinEvent.asSubscriptionView()
    val beforeLeave: EventSubscriptionView<PlayerTeamEvent> = beforeLeaveEvent.asSubscriptionView()
    val afterLeave: EventSubscriptionView<PlayerTeamEvent> = afterLeaveEvent.asSubscriptionView()
    val teamRemove: EventSubscriptionView<PluginTeam> = teamRemoveEvent.asSubscriptionView()

    internal fun emitBeforeJoin(event: PlayerTeamEvent) = beforeJoinEvent.invoke(event)
    internal fun emitAfterJoin(event: PlayerTeamEvent) = afterJoinEvent.invoke(event)
    internal fun emitBeforeLeave(event: PlayerTeamEvent) = beforeLeaveEvent.invoke(event)
    internal fun emitAfterLeave(event: PlayerTeamEvent) = afterLeaveEvent.invoke(event)
    internal fun emitTeamRemove(team: PluginTeam) = teamRemoveEvent.invoke(team)
}
