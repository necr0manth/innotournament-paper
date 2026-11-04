package dev.necr0manthre.innotournament.teams.invites

import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.teams.PluginTeam.TeamManager
import dev.necr0manthre.innotournament.teams.TeamCreateResult
import dev.necr0manthre.innotournament.teams.events.PlayerTeamEvent
import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class InviteSystemTest {
    private class Fixture : AutoCloseable {
        val manager = TeamManager()
        val owner = PluginPlayer.get(UUID.randomUUID())
        val player = PluginPlayer.get(UUID.randomUUID())
        val team = (manager.createNewTeam(owner, "team", Component.text("Team")) as TeamCreateResult.Created).team
        val system = InviteSystem(manager)
        override fun close() = system.close()
    }

    @Test fun `duplicate keeps record and deadline and views cannot mutate indexes`() = Fixture().use { f ->
        var issued = 0
        f.system.issued.subscribe {
            issued++
            assertTrue(it in f.team.invites.invites && it in f.player.invites.invites)
        }
        val invite = f.system.invitePlayerToTeam(f.player, f.team, 3)!!
        f.system.tick()
        assertSame(invite, f.system.invitePlayerToTeam(f.player, f.team, 1000))
        assertEquals(3L, invite.expiresAtTick)
        assertEquals(1, issued)
        assertThrows(UnsupportedOperationException::class.java) {
            (f.player.invites.invites as MutableSet<Invite>).clear()
        }
        assertTrue(f.system.rejectInvite(f.player, f.team))
        assertFalse(invite.isValid)
        assertTrue(f.team.invites.invites.isEmpty() && f.player.invites.invites.isEmpty())
        val replacement = f.system.invitePlayerToTeam(f.player, f.team, 3)!!
        assertNotSame(invite, replacement)
        assertTrue(f.system.revokeInvite(f.player, f.team))
        assertFalse(f.system.revokeInvite(f.player, f.team))
        assertFalse(replacement.isValid)
    }

    @Test fun `invalid lifetime and invitations to current member do not create records`() = Fixture().use { f ->
        for (ticks in listOf(0, -1)) {
            assertThrows(IllegalArgumentException::class.java) {
                f.system.invitePlayerToTeam(f.player, f.team, ticks)
            }
        }
        assertNull(f.system.invitePlayerToTeam(f.owner, f.team, 1))
        assertTrue(f.team.invites.invites.isEmpty())
        assertEquals(Int.MAX_VALUE.toLong(), f.system.invitePlayerToTeam(f.player, f.team, Int.MAX_VALUE)!!.expiresAtTick)
    }

    @Test fun `expiry occurs on exact tick and callback replacement survives`() = Fixture().use { f ->
        val old = f.system.invitePlayerToTeam(f.player, f.team, 2)!!
        var expired = 0
        var replacement: Invite? = null
        f.system.expired.subscribe {
            expired++
            assertSame(old, it)
            assertFalse(it.isValid)
            assertTrue(f.team.invites.invites.isEmpty() && f.player.invites.invites.isEmpty())
            replacement = f.system.invitePlayerToTeam(f.player, f.team, 10)
        }
        f.system.tick()
        assertTrue(old.isValid)
        f.system.tick()
        assertTrue(old.isExpired)
        assertEquals(1, expired)
        assertSame(replacement, f.system.getInvite(f.player, f.team))
        f.system.tick()
        assertEquals(1, expired)
    }

    @Test fun `expired records are invalid even when earlier expiry subscriber interrupts cleanup`() = Fixture().use { f ->
        f.system.invitePlayerToTeam(f.player, f.team, 1)
        val other = PluginPlayer.get(UUID.randomUUID())
        val pending = f.system.invitePlayerToTeam(other, f.team, 1)!!
        f.system.expired.subscribe { throw IllegalStateException("stop") }
        assertThrows(IllegalStateException::class.java) { f.system.tick() }
        assertTrue(pending.isExpired)
        assertFalse(pending.isValid)
        assertNull(f.system.getInvite(other, f.team))
        assertTrue(other.invites.invites.isEmpty())
    }

    @Test fun `removed team is invalid even if earlier removal subscriber interrupts cleanup`() {
        val manager = TeamManager()
        val owner = PluginPlayer.get(UUID.randomUUID())
        val player = PluginPlayer.get(UUID.randomUUID())
        val team = (manager.createNewTeam(owner, "same", Component.text("Same")) as TeamCreateResult.Created).team
        manager.afterRemove.subscribe { throw IllegalStateException("stop") }
        InviteSystem(manager).use { system ->
            val invite = system.invitePlayerToTeam(player, team, 10)!!
            assertThrows(IllegalStateException::class.java) { manager.removeTeam(team) }
            assertFalse(invite.isValid)
            assertTrue(player.invites.invites.isEmpty())
            val replacement = (manager.createNewTeam(owner, "same", Component.text("Same")) as TeamCreateResult.Created).team
            assertNull(system.getInvite(player, replacement))
        }
    }

    @Test fun `rename ownership and another membership preserve invitations`() = Fixture().use { f ->
        val invite = f.system.invitePlayerToTeam(f.player, f.team, 10)!!
        f.team.changeTeamName(Component.text("Renamed"))
        assertTrue(invite.isValid)
        val successor = PluginPlayer.get(UUID.randomUUID())
        assertTrue(f.team.tryAddPlayerToTeam(successor).isSuccess)
        f.team.transferOwnership(successor)
        assertTrue(invite.isValid)
        val otherOwner = PluginPlayer.get(UUID.randomUUID())
        val other = (f.manager.createNewTeam(otherOwner, "other", Component.text("Other")) as TeamCreateResult.Created).team
        assertTrue(other.tryAddPlayerToTeam(f.player).isSuccess)
        assertTrue(invite.isValid)
        assertTrue(f.manager.removeTeam(f.team))
        assertFalse(invite.isValid)
        assertTrue(f.player.invites.invites.isEmpty())
    }

    @Test fun `closing clears indexes and stops future issuance`(): Unit = Fixture().use { f ->
        val invite = f.system.invitePlayerToTeam(f.player, f.team, 10)!!
        f.system.close()
        f.system.close()
        assertFalse(invite.isValid)
        assertTrue(f.team.invites.invites.isEmpty() && f.player.invites.invites.isEmpty())
        assertThrows(IllegalStateException::class.java) { f.system.invitePlayerToTeam(f.player, f.team, 10) }
    }

}
