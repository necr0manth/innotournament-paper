package dev.necr0manthre.innotournament.tournament

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.ScoreboardManager
import java.util.UUID

class TournamentSidebarTransport(private val scoreboardManager: ScoreboardManager) : AutoCloseable {
    private class Viewer(
        val player: Player,
        val previousScoreboard: Scoreboard,
        val scoreboard: Scoreboard
    ) {
        val rowEntries = List(MAX_LINES) { "it-${UUID.randomUUID()}" }
        var objective: Objective? = null
        var title: Component? = null
        var lines: List<Component> = emptyList()
        var renderedLineCount = 0
        var rowsReady = false
        var initialized = false
        var surrendered = false
    }

    private val viewers = mutableMapOf<UUID, Viewer>()
    private var closing = false
    private var closed = false

    fun display(player: Player, title: Component, lines: List<Component>) {
        check(!closing && !closed) { "Sidebar transport is closed" }
        check(player.isOnline) { "Cannot display a sidebar for an offline player" }
        require(lines.size <= MAX_LINES) { "A sidebar cannot contain more than $MAX_LINES lines" }

        val id = player.uniqueId
        var viewer = viewers[id]
        if (viewer != null && viewer.player !== player) {
            release(viewer, restore = false)
            viewers.remove(id)
            viewer = null
        }

        if (viewer == null) {
            val previous = player.scoreboard
            val personal = scoreboardManager.newScoreboard
            viewer = Viewer(player, previous, personal)
            viewers[id] = viewer
            viewer.objective = personal.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, title)
            viewer.objective!!.displaySlot = DisplaySlot.SIDEBAR
            updateRows(viewer, lines)
            viewer.title = title
            viewer.lines = lines.toList()
            viewer.rowsReady = true
            viewer.initialized = true

            // Keep the target recorded before this call in case the setter changes it and then throws.
            player.scoreboard = personal
            return
        }

        if (viewer.surrendered) return
        check(viewer.initialized) { "Sidebar initialization failed; release this viewer before displaying again" }
        if (!ownsScoreboard(viewer)) {
            viewer.surrendered = true
            releaseObjective(viewer)
            return
        }

        if (viewer.title != title) {
            viewer.objective!!.displayName(title)
            viewer.title = title
        }
        val currentLines = lines.toList()
        if (!viewer.rowsReady || viewer.lines != currentLines) {
            updateRows(viewer, currentLines)
            viewer.lines = currentLines
            viewer.rowsReady = true
        }
    }

    fun release(player: Player) {
        val viewer = viewers[player.uniqueId] ?: return
        if (viewer.player !== player) return
        release(viewer, restore = true)
        viewers.remove(player.uniqueId)
    }

    override fun close() {
        if (closed) return
        closing = true
        val iterator = viewers.iterator()
        while (iterator.hasNext()) {
            release(iterator.next().value, restore = true)
            iterator.remove()
        }
        closed = true
    }

    private fun updateRows(viewer: Viewer, lines: List<Component>) {
        val scoreboard = viewer.scoreboard
        val objective = viewer.objective!!
        viewer.rowsReady = false
        for (index in lines.size until viewer.renderedLineCount) {
            scoreboard.resetScores(viewer.rowEntries[index])
        }
        lines.forEachIndexed { index, line ->
            val previousLine = viewer.lines.getOrNull(index)
            if (previousLine != line) {
                val score = objective.getScore(viewer.rowEntries[index])
                score.customName(line)
                if (previousLine == null) score.score = MAX_LINES - index
            }
            viewer.renderedLineCount = index + 1
        }
        viewer.renderedLineCount = lines.size
    }

    private fun ownsScoreboard(viewer: Viewer): Boolean =
        viewer.player.isOnline && viewer.player.scoreboard === viewer.scoreboard

    private fun release(viewer: Viewer, restore: Boolean) {
        if (restore && !viewer.surrendered && ownsScoreboard(viewer)) {
            viewer.player.scoreboard = viewer.previousScoreboard
        }
        releaseObjective(viewer)
    }

    private fun releaseObjective(viewer: Viewer) {
        val objective = viewer.objective ?: return
        if (objective.scoreboard != null) objective.unregister()
    }

    private companion object {
        const val MAX_LINES = 15
        const val OBJECTIVE_NAME = "innotournament"
    }
}
