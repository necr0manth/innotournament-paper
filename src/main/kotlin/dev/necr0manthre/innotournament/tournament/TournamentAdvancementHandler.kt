package dev.necr0manthre.innotournament.tournament

import dev.necr0manthre.innotournament.Innotournament
import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.tournament.events.EventSubscriptionView
import dev.necr0manthre.innotournament.tournament.events.SimpleEvent
import dev.necr0manthre.innotournament.tournament.events.asSubscriptionView
import dev.necr0manthre.innotournament.tournament.events.event_data.TeamAdvancement
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerAdvancementDoneEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.NamespacedKey
import org.bukkit.advancement.Advancement
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.LinkedHashMap

class TournamentAdvancementHandler(tournament: Tournament) {
    private val tournament = WeakReference(tournament)
    private val teamAdvancementBackingEvent = SimpleEvent<TeamAdvancement>()
    private val teamAdvancements = LinkedHashMap<TournamentTeam, LinkedHashMap<NamespacedKey, Advancement>>()
    val teamAdvancementEvent: EventSubscriptionView<TeamAdvancement> = teamAdvancementBackingEvent.asSubscriptionView()
    private var listener: Listener? = null

    fun register() {
        if (listener != null) return
        listener = object : Listener {
            @EventHandler
            fun onAdvancement(event: PlayerAdvancementDoneEvent) {
                handle(event)
            }

            @EventHandler
            fun onJoin(event: PlayerJoinEvent) {
                handleJoin(event)
            }
        }.also { Innotournament.instance.server.pluginManager.registerEvents(it, Innotournament.instance) }
    }

    fun unregister() {
        listener?.let { HandlerList.unregisterAll(it) }
        listener = null
    }

    fun advancements(team: TournamentTeam): Set<NamespacedKey> = Collections.unmodifiableSet(
        LinkedHashSet(teamAdvancements[team]?.keys.orEmpty())
    )

    private fun record(team: TournamentTeam, advancement: Advancement): Boolean =
        teamAdvancements.getOrPut(team, ::LinkedHashMap).putIfAbsent(advancement.key, advancement) == null

    private fun handle(event: PlayerAdvancementDoneEvent) {
        val currentTournament = tournament.get() ?: return
        val team = PluginPlayer.get(event.player).team?.let { currentTournament.getTeam(it) } ?: return
        if (record(team, event.advancement)) {
            teamAdvancementBackingEvent.invoke(TeamAdvancement(team, event.advancement))
        }

        team.players.forEach { player ->
            val online = player.player ?: return@forEach
            val progress = online.getAdvancementProgress(event.advancement)
            progress.remainingCriteria.forEach(progress::awardCriteria)
        }
    }

    private fun handleJoin(event: PlayerJoinEvent) {
        val currentTournament = tournament.get() ?: return
        val team = PluginPlayer.get(event.player).team?.let { currentTournament.getTeam(it) } ?: return
        teamAdvancements[team]?.values.orEmpty().forEach { advancement ->
            val progress = event.player.getAdvancementProgress(advancement)
            progress.remainingCriteria.forEach(progress::awardCriteria)
        }
    }
}
