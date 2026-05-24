package dev.necr0manthre.innotournament

import dev.necr0manthre.innotournament.init.InnoTournamentEventHandlers
import dev.necr0manthre.innotournament.init.InnoTournamentEvents
import dev.necr0manthre.innotournament.util.ServerScheduler
import org.bukkit.plugin.java.JavaPlugin

class Innotournament : JavaPlugin() {

    override fun onEnable() {
        instance = this
        InnoTournamentEvents.init()
        InnoTournamentEventHandlers.init()
//        PlayerEvent.PLAYER_JOIN.register({ player ->
//            Dispatcher.sendToClient(
//                WaypointPacket(
//                    "Stronghold",
//                    BlockPos(-13904, -37, -9984),
//                    Level.OVERWORLD
//                ), player
//            )
//        })
        ServerScheduler.init()
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }

    companion object {
        lateinit var instance: Innotournament
    }
}
