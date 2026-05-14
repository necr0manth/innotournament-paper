package dev.necr0manthre.innotournament.util

import com.destroystokyo.paper.event.server.ServerTickEndEvent
import com.destroystokyo.paper.event.server.ServerTickStartEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

object ServerScheduler : Listener {
    private val serverPostScheduler = Scheduler()
    private val serverPreScheduler = Scheduler()

    fun init() {
    }

    @EventHandler
    fun onServerTick(event: ServerTickStartEvent) {
        serverPreScheduler.tick()
    }

    @EventHandler
    fun onServerTick(event: ServerTickEndEvent) {
        serverPostScheduler.tick()
    }

    fun scheduleServerPost(ticks: Int, task: Runnable) {
        serverPostScheduler.schedule(ticks, task)
    }

    fun scheduleServerPre(ticks: Int, task: Runnable) {
        serverPreScheduler.schedule(ticks, task)
    }
}
