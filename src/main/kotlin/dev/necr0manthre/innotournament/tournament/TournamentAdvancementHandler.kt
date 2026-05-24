package dev.necr0manthre.innotournament.tournament

import dev.necr0manthre.innotournament.Innotournament
import dev.necr0manthre.innotournament.tournament.events.SimpleEvent
import dev.necr0manthre.innotournament.tournament.events.event_data.TeamAdvancement
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerAdvancementDoneEvent
import org.bukkit.event.player.PlayerJoinEvent
import java.lang.ref.WeakReference

class TournamentAdvancementHandler(tournament: Tournament) {
    private val tournament = WeakReference(tournament)
    val teamAdvancementEvent = SimpleEvent<TeamAdvancement>()
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

    private fun handle(event: PlayerAdvancementDoneEvent) {
        val team = TournamentPlayer.get(event.player).team ?: return
        if (team.advancements.putIfAbsent(event.advancement, event.player.getAdvancementProgress(event.advancement)) == null) {
            teamAdvancementEvent.invoke(TeamAdvancement(team, event.advancement))
        }

        team.players.forEach { player ->
            val online = player.player ?: return@forEach
            val progress = online.getAdvancementProgress(event.advancement)
            progress.remainingCriteria.forEach(progress::awardCriteria)
        }
    }

    private fun handleJoin(event: PlayerJoinEvent) {
        tournament.get() ?: return
        val team = TournamentPlayer.get(event.player).team ?: return
        team.advancements.keys.forEach { advancement ->
            val progress = event.player.getAdvancementProgress(advancement)
            progress.remainingCriteria.forEach(progress::awardCriteria)
        }
    }
}

