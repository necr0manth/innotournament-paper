package dev.necr0manthre.innotournament.tournament

import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.teams.PluginTeam.TeamManager
import dev.necr0manthre.innotournament.teams.TeamCreateResult
import net.kyori.adventure.text.Component
import org.bukkit.damage.DamageSource
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.TNTPrimed
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.UUID

class TournamentZeroLifeCombatTest {
    @Test
    fun zeroLifeParticipantCannotDamageAnotherPlayerDirectlyOrByProjectile() = withTournament { tournament, attacker ->
        val target = player(UUID.randomUUID())
        val melee = damageEvent(target, attacker, attacker)
        invokeDamageHandler(tournament, melee)
        assertTrue(melee.isCancelled)

        val sourceOnlyDamage = EntityDamageByEntityEvent(
            attacker, target, DamageCause.ENTITY_ATTACK, damageSource(null, null), 1.0
        )
        invokeDamageHandler(tournament, sourceOnlyDamage)
        assertTrue(sourceOnlyDamage.isCancelled)

        val projectile = proxy<Projectile> { method ->
            when (method) {
                "getShooter" -> attacker
                "getOwnerUniqueId" -> attacker.uniqueId
                else -> null
            }
        }
        val arrowHit = damageEvent(target, null, projectile)
        invokeDamageHandler(tournament, arrowHit)
        assertTrue(arrowHit.isCancelled)

        val ownerOnlyProjectile = proxy<Projectile> { method ->
            if (method == "getOwnerUniqueId") attacker.uniqueId else null
        }
        val ownedArrowHit = damageEvent(target, null, ownerOnlyProjectile)
        invokeDamageHandler(tournament, ownedArrowHit)
        assertTrue(ownedArrowHit.isCancelled)

        val self = damageEvent(attacker, attacker, attacker)
        invokeDamageHandler(tournament, self)
        assertFalse(self.isCancelled)

        val mob = damageEvent(proxy<Entity> { null }, attacker, attacker)
        invokeDamageHandler(tournament, mob)
        assertFalse(mob.isCancelled)
    }

    @Test
    fun zeroLifeParticipantOwnedTntDamageIsBlocked() = withTournament { tournament, attacker ->
        val tnt = proxy<TNTPrimed> { method ->
            when (method) {
                "getSource" -> attacker
                else -> null
            }
        }
        val event = damageEvent(player(UUID.randomUUID()), tnt, tnt)

        invokeDamageHandler(tournament, event)

        assertTrue(event.isCancelled)
    }

    @Test
    fun positiveLivesAndOutsidersCanDamagePlayers() = withTournament(lives = 1) { tournament, attacker ->
        val otherPlayer = damageEvent(player(UUID.randomUUID()), attacker, attacker)
        invokeDamageHandler(tournament, otherPlayer)
        assertFalse(otherPlayer.isCancelled)

        val outsider = player(UUID.randomUUID())
        val outsiderHit = damageEvent(player(UUID.randomUUID()), outsider, outsider)
        invokeDamageHandler(tournament, outsiderHit)
        assertFalse(outsiderHit.isCancelled)
    }

    @Test
    fun stalePhaseAndParticipationDoNotBlockDamage() = withTournament { tournament, attacker ->
        setPhase(tournament, TournamentPhase.ENDED)
        val ended = damageEvent(player(UUID.randomUUID()), attacker, attacker)
        invokeDamageHandler(tournament, ended)
        assertFalse(ended.isCancelled)

        setPhase(tournament, TournamentPhase.STARTED)
        val participant = tournament.getPlayer(PluginPlayer.get(attacker.uniqueId))!!
        val manager = Tournament::class.java.getDeclaredField("teamManager").apply { isAccessible = true }
            .get(tournament) as TeamManager
        check(manager.tryKickPlayerFromTeam(participant.team.pluginTeam, participant.pluginPlayer).isSuccess)
        val stale = damageEvent(player(UUID.randomUUID()), attacker, attacker)
        invokeDamageHandler(tournament, stale)
        assertFalse(stale.isCancelled)
    }

    private fun withTournament(
        lives: Int = 0,
        action: (Tournament, Player) -> Unit
    ) = withPrimaryTestServer {
        val manager = TeamManager()
        val attacker = player(UUID.randomUUID())
        val owner = PluginPlayer.get(UUID.randomUUID())
        val team = (manager.createNewTeam(owner, "combat", Component.text("Combat")) as TeamCreateResult.Created).team
        val attackerPluginPlayer = PluginPlayer.get(attacker.uniqueId)
        check(team.tryAddPlayerToTeam(attackerPluginPlayer).isSuccess)
        val tournament = testTournament(manager)
        setPhase(tournament, TournamentPhase.STARTED)
        tournament.checkTeam(team)
        val participation = tournament.getPlayer(attackerPluginPlayer)!!
        markTournamentPlayerPrepared(participation)
        if (lives != participation.lives) participation.setLives(lives)
        action(tournament, attacker)
    }

    private fun damageEvent(victim: Entity, causing: Entity?, direct: Entity?): EntityDamageEvent {
        return EntityDamageEvent(victim, DamageCause.CUSTOM, damageSource(causing, direct), 1.0)
    }

    private fun damageSource(causing: Entity?, direct: Entity?): DamageSource = proxy { method ->
        when (method) {
            "getCausingEntity" -> causing
            "getDirectEntity" -> direct
            else -> null
        }
    }

    private fun invokeDamageHandler(tournament: Tournament, event: EntityDamageEvent) {
        Tournament::class.java.getDeclaredMethod("handleOutgoingDamage", EntityDamageEvent::class.java)
            .apply { isAccessible = true }
            .invoke(tournament, event)
    }

    private fun setPhase(tournament: Tournament, phase: TournamentPhase) {
        Tournament::class.java.getDeclaredField("phase").apply { isAccessible = true }.set(tournament, phase)
    }

    private fun player(uuid: UUID): Player = proxy { method ->
        when (method) {
            "getUniqueId" -> uuid
            else -> null
        }
    }

    private inline fun <reified T> proxy(noinline handler: (String) -> Any?): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java)
    ) { _, method, _ -> handler(method.name) } as T
}
