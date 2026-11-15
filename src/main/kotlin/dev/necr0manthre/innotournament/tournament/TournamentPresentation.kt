package dev.necr0manthre.innotournament.tournament

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin

/** Owns the live and frozen-result sidebar presentation for one tournament run. */
class TournamentPresentation(
    private val plugin: Plugin,
    private val transport: TournamentSidebarTransport,
    private val captureCurrent: () -> (Player) -> TournamentLiveSidebarBuilder.Sidebar
) : Listener, AutoCloseable {
    private var activated = false
    private var closed = false
    private var refreshTaskId: Int? = null
    private var finalResult: TournamentResult? = null

    /** Register listeners, start the live refresh, then draw the current view immediately. */
    fun activate() {
        checkPrimaryThread()
        check(!closed) { "Presentation is closed" }
        check(!activated) { "Presentation is already active" }
        activated = true

        Bukkit.getPluginManager().registerEvents(this, plugin)
        refreshTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, Runnable(::refreshPeriodically), 20L, 20L)
        refreshLive()
    }

    /** Freeze and show the result, then make one synchronous announcement attempt. */
    fun finishNormally(result: TournamentResult) {
        checkPrimaryThread()
        check(activated && !closed) { "Presentation is not active" }
        if (finalResult != null) return
        finalResult = result
        stopRefreshTask()

        val viewers = Bukkit.getOnlinePlayers().toList()
        viewers.forEach { displayResult(it, result) }
        val announcement = TournamentResultRenderer.finalAnnouncement(result)
        viewers.forEach { it.sendMessage(announcement) }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        checkPrimaryThread()
        if (!activated || closed) return
        val result = finalResult
        if (result == null) displayLive(event.player, captureCurrent()) else displayResult(event.player, result)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        checkPrimaryThread()
        transport.release(event.player)
    }

    override fun close() {
        checkPrimaryThread()
        if (closed) return
        stopRefreshTask()
        HandlerList.unregisterAll(this)
        transport.close()
        closed = true
    }

    private fun refreshLive() {
        if (!activated || closed || finalResult != null) return
        val renderViewer = captureCurrent()
        Bukkit.getOnlinePlayers().forEach { displayLive(it, renderViewer) }
    }

    private fun refreshPeriodically() {
        try {
            refreshLive()
        } catch (failure: Throwable) {
            try {
                stopRefreshTask()
            } catch (cleanupFailure: Throwable) {
                failure.addSuppressed(cleanupFailure)
            }
            throw failure
        }
    }

    private fun displayLive(player: Player, renderViewer: (Player) -> TournamentLiveSidebarBuilder.Sidebar) {
        val sidebar = renderViewer(player)
        transport.display(player, sidebar.title, sidebar.lines)
    }

    private fun displayResult(player: Player, result: TournamentResult) {
        val sidebar = TournamentResultRenderer.sidebar(result, player.uniqueId)
        transport.display(player, sidebar.title, sidebar.lines)
    }

    private fun stopRefreshTask() {
        val taskId = refreshTaskId ?: return
        Bukkit.getScheduler().cancelTask(taskId)
        refreshTaskId = null
    }

    private fun checkPrimaryThread() {
        check(Bukkit.isPrimaryThread()) { "Presentation must be accessed on the server thread" }
    }
}
