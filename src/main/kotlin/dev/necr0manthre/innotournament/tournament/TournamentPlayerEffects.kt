package dev.necr0manthre.innotournament.tournament

import dev.necr0manthre.innotournament.tournament.environment.TournamentEnvironment
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.util.Vector

/** Applies Bukkit effects for a newly created player participation. */
class TournamentPlayerEffects(private val environment: TournamentEnvironment) {
    fun resetNewParticipation(player: Player) {
        player.health = player.maxHealth
        player.foodLevel = 20
        player.saturation = 1f
        player.inventory.clear()

        val server = player.server
        val advancements = server.advancementIterator()
        while (advancements.hasNext()) {
            val progress = player.getAdvancementProgress(advancements.next())
            progress.awardedCriteria.toList().forEach(progress::revokeCriteria)
        }

        player.setRespawnLocation(null, false)
        player.gameMode = GameMode.SURVIVAL

        val spawn = environment.gameSpawnLocation().apply {
            yaw = 0f
            pitch = 0f
        }
        check(player.teleport(spawn, PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            "Could not teleport player to the tournament game spawn"
        }
        player.velocity = Vector()

        player.exp = 0f
        player.level = 0
        player.totalExperience = 0
        player.clearActivePotionEffects()
    }
}
