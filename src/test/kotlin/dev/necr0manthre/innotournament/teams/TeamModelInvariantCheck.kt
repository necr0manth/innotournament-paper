package dev.necr0manthre.innotournament.teams

import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.teams.events.PlayerTeamEvent
import dev.necr0manthre.innotournament.teams.events.TeamChange
import dev.necr0manthre.innotournament.teams.events.TeamCreated
import dev.necr0manthre.innotournament.teams.events.TeamRenameChange
import dev.necr0manthre.innotournament.teams.events.TeamEvents
import dev.necr0manthre.innotournament.teams.events.TeamOwnerChange
import dev.necr0manthre.innotournament.teams.events.TeamOwnerTransferEvent
import dev.necr0manthre.innotournament.teams.events.TeamRenameEvent
import dev.necr0manthre.innotournament.teams.events.TeamRemoveEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.GameMode
import org.bukkit.Location
import org.junit.jupiter.api.Test
import java.util.UUID

/** Run with ./gradlew test or verifyTeamModel; uses real model APIs, not Bukkit/player mocks. */
fun main() {
    val ids = (1L..5L).map { UUID(0, it) }
    val players = ids.map { PluginPlayer.get(it) }
    val (a, b, c, d, e) = players
    val blue = TeamSnapshot("blue", Component.text("Blue"), false, listOf(ids[0], ids[1]), ids[0])

    val invalid = TeamsSnapshot(teams = listOf(blue, TeamSnapshot(
        "duplicate", Component.text("Duplicate"), false, listOf(ids[1]), ids[1]
    )))
    check(runCatching { PluginTeam.initialize(invalid) }.exceptionOrNull() is IllegalArgumentException)
    check(PluginTeam.getAllTeams().isEmpty())

    PluginTeam.initialize(TeamsSnapshot(teams = listOf(blue, TeamSnapshot(
        "empty", Component.text("Empty"), true, emptyList(), null
    ))))
    check(runCatching { PluginTeam.initialize(TeamsSnapshot(teams = emptyList())) }
        .exceptionOrNull() is IllegalStateException)

    val teamA = checkNotNull(PluginTeam.getTeamById("blue"))
    val teamB = checkNotNull(PluginTeam.getTeamById("empty"))
    check(PluginPlayer.get(ids[0]) === a && a.team === teamA)
    check(teamA.players == listOf(a, b) && teamA.owner === a)
    check(runCatching { (teamA.players as MutableList<*>).clear() }
        .exceptionOrNull() is UnsupportedOperationException)
    check(runCatching { (PluginTeam.getAllTeams() as MutableList<*>).clear() }
        .exceptionOrNull() is UnsupportedOperationException)
    check(runCatching { (PluginTeam.snapshot().teams as MutableList<*>).clear() }
        .exceptionOrNull() is UnsupportedOperationException)
    check(runCatching { (PluginTeam.snapshot().teams.first().members as MutableList<*>).clear() }
        .exceptionOrNull() is UnsupportedOperationException)
    check(PluginTeam.snapshot().teams.first().members == listOf(ids[0], ids[1]))

    // A beforeJoin listener may rename another registered team into the pending id.
    val creator = PluginPlayer.get(UUID(0, 6))
    val creationEvents = mutableListOf<String>()
    val observedCreate: (TeamCreated) -> Unit = {
        if (it.creator === creator) {
            check(it.team.owner === creator && it.team.players == listOf(creator))
            creationEvents += "create"
        }
    }
    val observedJoin: (TeamChange) -> Unit = {
        if (it.player === creator) {
            check(it.player.team === it.team && it.team.players == listOf(creator))
            creationEvents += "join"
        }
    }
    TeamEvents.afterCreate.subscribe(observedCreate)
    TeamEvents.afterJoin.subscribe(observedJoin)
    var renameDelivered = false
    val observeRename: (TeamRenameChange) -> Unit = {
        if (it.team === teamA && it.newId == "claimed") {
            check(PluginTeam.getTeamById("claimed") === teamA)
            renameDelivered = true
        }
    }
    val claimId: (PlayerTeamEvent) -> Unit = {
        if (it.player === creator && it.team.id == "claimed") {
            check(teamA.changeTeamName(Component.text("claimed")))
        }
    }
    TeamEvents.afterRename.subscribe(observeRename)
    TeamEvents.beforeJoin.subscribe(claimId)
    check(PluginTeam.createNewTeam(creator, "claimed", Component.text("Claimed")) ===
        TeamCreateResult.ID_ALREADY_EXISTS)
    TeamEvents.beforeJoin.unsubscribe(claimId)
    TeamEvents.afterRename.unsubscribe(observeRename)
    check(renameDelivered && PluginTeam.getTeamById("claimed") === teamA)
    check(PluginTeam.getTeamById("blue") == null)
    check(PluginTeam.getAllTeams() == listOf(teamA, teamB))
    check(teamA.players == listOf(a, b) && teamA.owner === a && a.team === teamA && b.team === teamA)
    check(PluginTeam.snapshot().teams.first().let {
        it.id == "claimed" && it.members == listOf(ids[0], ids[1]) && it.ownerUuid == ids[0]
    })
    check(creator.team == null && creationEvents.isEmpty())
    check(teamA.changeTeamName(Component.text("blue")))

    val cancelCreationJoin: (PlayerTeamEvent) -> Unit = {
        if (it.player === creator && it.team.id == "vetoed") it.canceled = true
    }
    TeamEvents.beforeJoin.subscribe(cancelCreationJoin)
    check(PluginTeam.createNewTeam(creator, "vetoed", Component.text("Vetoed")) ===
        TeamCreateResult.CANCELLED)
    TeamEvents.beforeJoin.unsubscribe(cancelCreationJoin)
    check(PluginTeam.getTeamById("vetoed") == null && creator.team == null)
    check(PluginTeam.getAllTeams() == listOf(teamA, teamB) && creationEvents.isEmpty())

    val created = PluginTeam.createNewTeam(creator, "created", Component.text("Created"))
    check(created is TeamCreateResult.Created)
    check(created.team.owner === creator && created.team.players == listOf(creator))
    check(creator.team === created.team && PluginTeam.getTeamById("created") === created.team)
    check(creationEvents == listOf("create", "join"))
    TeamEvents.afterCreate.unsubscribe(observedCreate)
    TeamEvents.afterJoin.unsubscribe(observedJoin)
    check(PluginTeam.removeTeam(created.team))
    check(creator.team == null && PluginTeam.getTeamById("created") == null)
    check(PluginTeam.getAllTeams() == listOf(teamA, teamB))

    check(teamA.tryKickPlayerFromTeam(a).isSuccess)
    check(a.team == null && teamA.owner === b)
    check(teamA.transferOwnership(a).not())
    check(teamA.tryKickPlayerFromTeam(b).isSuccess)
    check(teamA.owner == null && teamA.players.isEmpty() && PluginTeam.getTeamById("blue") === teamA)
    check(teamA.tryAddPlayerToTeam(c).isSuccess && teamA.owner === c)
    check(teamA.transferOwnership(c))

    val vetoJoin: (PlayerTeamEvent) -> Unit = { if (it.team === teamB) it.canceled = true }
    TeamEvents.beforeJoin.subscribe(vetoJoin)
    // B's join is cancelled only after A's fully completed leave.
    check(teamB.tryAddPlayerToTeam(c) === PlayerJoinResult.CANCELLED)
    check(c.team == null && teamA.players.isEmpty() && teamB.players.isEmpty())
    TeamEvents.beforeJoin.unsubscribe(vetoJoin)

    check(teamA.changeTeamName(Component.text("renamed")))
    check(PluginTeam.getTeamById("blue") == null && PluginTeam.getTeamById("renamed") === teamA)
    val guardOnRename: (dev.necr0manthre.innotournament.teams.events.TeamRenameEvent) -> Unit = {
        check(runCatching { PluginTeam.createNewTeam(e, "not-allowed", Component.text("X")) }
            .exceptionOrNull() is IllegalStateException)
        check(runCatching { PluginTeam.removeTeamFromCommand(teamB) }
            .exceptionOrNull() is IllegalStateException)
        check(runCatching { teamA.tryKickPlayerFromTeam(c) }
            .exceptionOrNull() is IllegalStateException)
    }
    TeamEvents.beforeRename.subscribe(guardOnRename)
    check(teamA.changeTeamName(Component.text("renamed again")))
    TeamEvents.beforeRename.unsubscribe(guardOnRename)

    // Guard only this team's active rename, including otherwise successful no-ops.
    var beforeRenames = 0
    var afterRenames = 0
    val nestedBeforeRename: (TeamRenameEvent) -> Unit = {
        if (it.team === teamA) {
            beforeRenames++
            check(teamA.id == it.oldId && teamA.displayName == it.oldName)
            check(runCatching { teamA.changeTeamName(teamA.displayName) }
                .exceptionOrNull() is IllegalStateException)
            check(runCatching { teamA.changeTeamName(Component.text("nested before")) }
                .exceptionOrNull() is IllegalStateException)
            check(teamB.changeTeamName(Component.text("other before")))
            teamA.acceptAll = true
        }
    }
    val nestedAfterRename: (TeamRenameChange) -> Unit = {
        if (it.team === teamA) {
            afterRenames++
            check(teamA.id == it.newId && teamA.displayName == it.newName)
            check(runCatching { teamA.changeTeamName(teamA.displayName) }
                .exceptionOrNull() is IllegalStateException)
            check(runCatching { teamA.changeTeamName(Component.text("nested after")) }
                .exceptionOrNull() is IllegalStateException)
            check(teamB.changeTeamName(Component.text("other after")))
        }
    }
    TeamEvents.beforeRename.subscribe(nestedBeforeRename)
    TeamEvents.afterRename.subscribe(nestedAfterRename)
    check(teamA.changeTeamName(Component.text("guarded rename")))
    TeamEvents.beforeRename.unsubscribe(nestedBeforeRename)
    TeamEvents.afterRename.unsubscribe(nestedAfterRename)
    check(beforeRenames == 1 && afterRenames == 1 && teamA.acceptAll)
    check(PluginTeam.getTeamById("guarded rename") === teamA)
    check(PluginTeam.getTeamById("other after") === teamB)
    check(PluginTeam.getTeamById("nested before") == null)
    check(PluginTeam.getTeamById("nested after") == null)

    // A is not being renamed here: B's callback may still rename A.
    var crossTeamRenames = 0
    val renameFromOtherCallback: (TeamRenameChange) -> Unit = {
        if (it.team === teamB) {
            crossTeamRenames++
            check(teamA.changeTeamName(Component.text("rename from B callback")))
        }
    }
    TeamEvents.afterRename.subscribe(renameFromOtherCallback)
    check(teamB.changeTeamName(Component.text("other trigger")))
    TeamEvents.afterRename.unsubscribe(renameFromOtherCallback)
    check(crossTeamRenames == 1 && PluginTeam.getTeamById("rename from B callback") === teamA)
    check(teamB.changeTeamName(Component.text("empty")))

    val vetoRename: (TeamRenameEvent) -> Unit = {
        if (it.team === teamA) {
            check(runCatching { teamA.changeTeamName(teamA.displayName) }
                .exceptionOrNull() is IllegalStateException)
            it.canceled = true
        }
    }
    TeamEvents.beforeRename.subscribe(vetoRename)
    check(!teamA.changeTeamName(Component.text("vetoed rename")))
    TeamEvents.beforeRename.unsubscribe(vetoRename)
    check(PluginTeam.getTeamById("rename from B callback") === teamA)
    check(teamA.changeTeamName(Component.text("after veto")))

    var uncaughtRecursiveRenames = 0
    val uncaughtRecursiveRename: (TeamRenameEvent) -> Unit = {
        if (it.team === teamA) {
            uncaughtRecursiveRenames++
            teamA.changeTeamName(Component.text("recursive exception"))
        }
    }
    TeamEvents.beforeRename.subscribe(uncaughtRecursiveRename)
    check(runCatching { teamA.changeTeamName(Component.text("outer exception")) }
        .exceptionOrNull() is IllegalStateException)
    TeamEvents.beforeRename.unsubscribe(uncaughtRecursiveRename)
    check(uncaughtRecursiveRenames == 1 && PluginTeam.getTeamById("after veto") === teamA)
    check(teamA.changeTeamName(Component.text("after recursive exception")))

    val beforeFailure = IllegalArgumentException("before rename failure")
    val throwBeforeRename: (TeamRenameEvent) -> Unit = {
        if (it.team === teamA) throw beforeFailure
    }
    TeamEvents.beforeRename.subscribe(throwBeforeRename)
    check(runCatching { teamA.changeTeamName(Component.text("uncommitted rename")) }
        .exceptionOrNull() === beforeFailure)
    TeamEvents.beforeRename.unsubscribe(throwBeforeRename)
    check(PluginTeam.getTeamById("after recursive exception") === teamA)
    check(teamA.changeTeamName(Component.text("recovered before")))

    val afterFailure = IllegalArgumentException("after rename failure")
    val throwAfterRename: (TeamRenameChange) -> Unit = {
        if (it.team === teamA) {
            check(runCatching { teamA.changeTeamName(teamA.displayName) }
                .exceptionOrNull() is IllegalStateException)
            throw afterFailure
        }
    }
    TeamEvents.afterRename.subscribe(throwAfterRename)
    check(runCatching { teamA.changeTeamName(Component.text("committed before failure")) }
        .exceptionOrNull() === afterFailure)
    TeamEvents.afterRename.unsubscribe(throwAfterRename)
    check(PluginTeam.getTeamById("committed before failure") === teamA)
    check(teamA.changeTeamName(Component.text("recovered after")))
    check(teamA.changeTeamName(teamA.displayName)) // Independent no-op is still allowed.

    check(teamA.tryAddPlayerToTeam(d).isSuccess)
    check(teamA.tryAddPlayerToTeam(e).isSuccess)
    var beforeTransfers = 0
    var afterTransfers = 0
    val blockNestedTransfer: (TeamOwnerTransferEvent) -> Unit = {
        if (it.team === teamA) {
            beforeTransfers++
            check(it.oldOwner === d && it.newOwner === e)
            check(teamA.owner === d && teamA.players == listOf(d, e))
            check(runCatching { teamA.transferOwnership(d) }.exceptionOrNull() is IllegalStateException)
            check(runCatching { teamA.transferOwnership(e) }.exceptionOrNull() is IllegalStateException)
            check(c.team == null && d.team === teamA && e.team === teamA)
            // An owner-transfer guard must not prohibit an unrelated rename.
            check(teamA.changeTeamName(Component.text("renamed during owner transfer")))
        }
    }
    val observeOwnerTransfer: (TeamOwnerChange) -> Unit = {
        if (it.team === teamA) {
            afterTransfers++
            check(it.oldOwner === d && it.newOwner === e)
            check(teamA.owner === e && teamA.players == listOf(d, e))
            check(runCatching { teamA.transferOwnership(d) }.exceptionOrNull() is IllegalStateException)
        }
    }
    TeamEvents.beforeOwnerTransfer.subscribe(blockNestedTransfer)
    TeamEvents.afterOwnerTransfer.subscribe(observeOwnerTransfer)
    check(teamA.transferOwnership(e) && teamA.owner === e)
    TeamEvents.beforeOwnerTransfer.unsubscribe(blockNestedTransfer)
    TeamEvents.afterOwnerTransfer.unsubscribe(observeOwnerTransfer)
    check(beforeTransfers == 1 && afterTransfers == 1)
    check(PluginTeam.getTeamById("renamed during owner transfer") === teamA)

    var failingTransferCalls = 0
    val uncaughtNestedTransfer: (TeamOwnerTransferEvent) -> Unit = {
        if (it.team === teamA) {
            failingTransferCalls++
            teamA.transferOwnership(d)
        }
    }
    TeamEvents.beforeOwnerTransfer.subscribe(uncaughtNestedTransfer)
    check(runCatching { teamA.transferOwnership(d) }.exceptionOrNull() is IllegalStateException)
    TeamEvents.beforeOwnerTransfer.unsubscribe(uncaughtNestedTransfer)
    check(failingTransferCalls == 1 && teamA.owner === e)
    check(teamA.players == listOf(d, e) && c.team == null && d.team === teamA && e.team === teamA)
    // The exception must release the guard for an independent later transfer.
    check(teamA.transferOwnership(d) && teamA.owner === d)
    check(teamA.transferOwnership(e) && teamA.owner === e)
    var laterListenerCalled = false
    val forbiddenMove: (TeamChange) -> Unit = {
        if (it.player === d && it.team === teamA) {
            // This exception propagates; neither a nested join nor the outer join proceeds.
            teamB.tryAddPlayerToTeam(d)
        }
    }
    val laterListener: (TeamChange) -> Unit = { laterListenerCalled = true }
    var enteredBeforeJoinB = false
    val observeBeforeJoin: (PlayerTeamEvent) -> Unit = { if (it.team === teamB) enteredBeforeJoinB = true }
    TeamEvents.afterLeave.subscribe(forbiddenMove)
    TeamEvents.afterLeave.subscribe(laterListener)
    TeamEvents.beforeJoin.subscribe(observeBeforeJoin)
    check(runCatching { teamB.tryAddPlayerToTeam(d) }.exceptionOrNull() is IllegalStateException)
    check(d.team == null && !teamA.players.contains(d) && !teamB.players.contains(d))
    check(!laterListenerCalled && !enteredBeforeJoinB)
    TeamEvents.afterLeave.unsubscribe(forbiddenMove)
    TeamEvents.afterLeave.unsubscribe(laterListener)
    TeamEvents.beforeJoin.unsubscribe(observeBeforeJoin)
    check(teamB.tryAddPlayerToTeam(d).isSuccess && teamB.owner === d)

    val vetoDelete: (PlayerTeamEvent) -> Unit = { if (it.team === teamB) it.canceled = true }
    TeamEvents.beforeLeave.subscribe(vetoDelete)
    check(!PluginTeam.removeTeam(teamB))
    check(d.team === teamB && teamB.players == listOf(d))
    var afterLeave: TeamChange? = null
    var afterRemove = false
    val observeLeave: (TeamChange) -> Unit = {
        if (it.team === teamB) {
            check(it.player.team == null && it.team.players.isEmpty())
            afterLeave = it
        }
    }
    val observeRemove: (PluginTeam) -> Unit = { if (it === teamB) afterRemove = true }
    TeamEvents.afterLeave.subscribe(observeLeave)
    TeamEvents.afterRemove.subscribe(observeRemove)
    check(PluginTeam.removeTeamFromCommand(teamB))
    check(afterLeave == TeamChange(d, teamB, 1, 0) && afterRemove)
    check(teamB.owner == null && teamB.players.isEmpty() && d.team == null)
    check(PluginTeam.getTeamById("empty") == null)
    check(teamB.tryAddPlayerToTeam(d) === PlayerJoinResult.TEAM_REMOVED)
    TeamEvents.beforeLeave.unsubscribe(vetoDelete)
    TeamEvents.afterLeave.unsubscribe(observeLeave)
    TeamEvents.afterRemove.unsubscribe(observeRemove)
    check(PluginTeam.getAllTeams() == listOf(teamA))
    verifyBulkRemovalFrozenSizes()
    verifyDeletedTeamRanking(teamA, d, e)

    println("TeamModelInvariantCheck: OK")
}

private fun verifyBulkRemovalFrozenSizes() {
    for ((force, size) in listOf(false to 3, true to 4)) {
        val id = if (force) "bulk-force" else "bulk-ordinary"
        val players = (0 until size).map { PluginPlayer.get(UUID(0, 100L + (if (force) 10 else 0) + it)) }
        val team = (PluginTeam.createNewTeam(players.first(), id, Component.text(id))
            as TeamCreateResult.Created).team
        players.drop(1).forEach { check(team.tryAddPlayerToTeam(it).isSuccess) }

        val changes = mutableListOf<TeamChange>()
        val beforeSizes = mutableListOf<Int>()
        var removed = false
        var vetoMiddle = !force
        val beforeRemove: (TeamRemoveEvent) -> Unit = {
            if (it.team === team && force) it.canceled = true
        }
        val beforeLeave: (PlayerTeamEvent) -> Unit = {
            if (it.team === team) {
                check(team.players == players && team.owner === players.first())
                beforeSizes += it.sizeBefore
                if (force || (vetoMiddle && it.player === players[1])) it.canceled = true
            }
        }
        val afterLeave: (TeamChange) -> Unit = {
            if (it.team === team) {
                // Every notification sees the completed whole-delete, not a partial state.
                check(team.players.isEmpty() && team.owner == null)
                check(players.all { member -> member.team == null })
                check(PluginTeam.getTeamById(id) == null)
                changes += it
            }
        }
        val afterRemove: (PluginTeam) -> Unit = {
            if (it === team) {
                check(changes.size == size)
                removed = true
            }
        }
        TeamEvents.beforeRemove.subscribe(beforeRemove)
        TeamEvents.beforeLeave.subscribe(beforeLeave)
        TeamEvents.afterLeave.subscribe(afterLeave)
        TeamEvents.afterRemove.subscribe(afterRemove)
        try {
            if (!force) {
                check(!PluginTeam.removeTeam(team)) // Middle leave vetoes the entire delete.
                check(beforeSizes == listOf(size, size) && changes.isEmpty() && !removed)
                check(team.players == players && team.owner === players.first())
                check(players.all { it.team === team } && PluginTeam.getTeamById(id) === team)
                vetoMiddle = false
                beforeSizes.clear()
            }
            check(if (force) PluginTeam.removeTeamFromCommand(team) else PluginTeam.removeTeam(team))
            check(beforeSizes == List(size) { size })
            check(changes == players.mapIndexed { index, member ->
                TeamChange(member, team, size - index, size - index - 1)
            })
            check(removed && PluginTeam.getTeamById(id) == null)
        } finally {
            TeamEvents.beforeRemove.unsubscribe(beforeRemove)
            TeamEvents.beforeLeave.unsubscribe(beforeLeave)
            TeamEvents.afterLeave.unsubscribe(afterLeave)
            TeamEvents.afterRemove.unsubscribe(afterRemove)
        }
    }
}

private fun verifyDeletedTeamRanking(activeTeam: PluginTeam, player: PluginPlayer, lastMember: PluginPlayer) {
    val origin = Location(null, 0.0, 0.0, 0.0)
    val tournament = Tournament(origin, origin, null, origin, GameMode.SURVIVAL,
        emptyMap(), emptyMap(), doubleArrayOf(1.0), 0f)
    val currentRecord = checkNotNull(tournament.getOrCreateTournamentTeam(activeTeam))
    currentRecord.score = 25.0

    val leadingTeam = (PluginTeam.createNewTeam(player, "leader", Component.text("Leader"))
        as TeamCreateResult.Created).team
    val historicalRecord = checkNotNull(tournament.getOrCreateTournamentTeam(leadingTeam))
    historicalRecord.score = 100.0

    val output = PlainTextComponentSerializer.plainText()
    val standingsMethod = Tournament::class.java.getDeclaredMethod("finalStandingsMessage")
        .apply { isAccessible = true }
    fun standings() = output.serialize(standingsMethod.invoke(tournament) as Component)
    val remainingName = output.serialize(activeTeam.displayName)
    val onlyRemaining = "Tournament ended. Results: 1. $remainingName (25.0)"

    val onRemove: (PluginTeam) -> Unit = tournament::onTeamRemoved
    TeamEvents.afterRemove.subscribe(onRemove)
    try {
        check(tournament.getTopTeams(1) == listOf(historicalRecord))
        check(standings() == "Tournament ended. Results: 1. Leader (100.0), 2. $remainingName (25.0)")
        check(PluginTeam.removeTeamFromCommand(leadingTeam))
        check(tournament.getTeam(leadingTeam) == null)
        check(historicalRecord.score == 100.0)
        val records = Tournament::class.java.getDeclaredField("teams").apply { isAccessible = true }
            .get(tournament) as Set<*>
        check(historicalRecord in records) // Deleted teams are still stored as history.
        // A stale public factory call must not revive the removed team or add a second record.
        check(tournament.getOrCreateTournamentTeam(leadingTeam) == null)
        check(tournament.getTeam(leadingTeam) == null && records.size == 2)
        check(tournament.getTopTeams(10) == listOf(currentRecord))
        check(standings() == onlyRemaining)

        check(activeTeam.tryKickPlayerFromTeam(lastMember).isSuccess)
        check(activeTeam.players.isEmpty())
        check(tournament.getTopTeams(10) == listOf(currentRecord)) // Empty, not deleted.
        check(standings() == onlyRemaining)

        val replacement = (PluginTeam.createNewTeam(player, "leader", Component.text("Replacement"))
            as TeamCreateResult.Created).team
        val replacementRecord = checkNotNull(tournament.getOrCreateTournamentTeam(replacement))
        replacementRecord.score = 10.0
        check(replacement !== leadingTeam && replacementRecord !== historicalRecord)
        check(PluginTeam.getTeamById("leader") === replacement)
        check(tournament.getOrCreateTournamentTeam(leadingTeam) == null)
        check(tournament.getTeam(leadingTeam) == null && records.size == 3)
        check(tournament.getTopTeams(10) == listOf(currentRecord, replacementRecord))
        check(standings() == "$onlyRemaining, 2. Replacement (10.0)")

        check(PluginTeam.removeTeamFromCommand(activeTeam))
        check(PluginTeam.removeTeamFromCommand(replacement))
        check(tournament.getTopTeams(10).isEmpty())
        check(standings() == "Tournament ended") // History exists, no active places.
        check(records.size == 3 && historicalRecord in records)
    } finally {
        TeamEvents.afterRemove.unsubscribe(onRemove)
    }
}

class TeamModelInvariantTest {
    @Test
    fun invariants() = main()
}
