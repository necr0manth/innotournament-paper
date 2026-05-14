package dev.necr0manthre.innotournament.util


class Scheduler {
    private val lock = Any()
    private val pendingQueue: MutableList<Task> = mutableListOf()
    private val activeQueue: MutableList<Task> = mutableListOf()

    fun schedule(ticks: Int, task: Runnable) {
        synchronized(lock) {
            pendingQueue.add(Task(ticks, task))
        }
    }

    fun tick() {
        synchronized(lock) {
            if (!pendingQueue.isEmpty()) {
                activeQueue.addAll(pendingQueue)
                pendingQueue.clear()
            }
        }

        activeQueue.removeIf { it.tick() }
    }

    private data class Task(
        var ticks: Int = 0,
        val action: Runnable
    ) {
        fun tick(): Boolean {
            if (--ticks > 0) return false
            action.run()
            return true
        }
    }
}
