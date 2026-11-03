package dev.necr0manthre.innotournament.teams

import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.TournamentTeam
import dev.necr0manthre.innotournament.tournament.TournamentPhase
import dev.necr0manthre.innotournament.teams.events.PlayerTeamEvent
import dev.necr0manthre.innotournament.teams.events.TeamChange
import dev.necr0manthre.innotournament.teams.events.TeamCreated
import dev.necr0manthre.innotournament.teams.events.TeamRenameChange
import dev.necr0manthre.innotournament.teams.TeamManager
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
    check(runCatching { TeamManager(invalid) }.exceptionOrNull() is IllegalArgumentException)
    check(TeamManager().getAllTeams().isEmpty())

    val manager = TeamManager(TeamsSnapshot(teams = listOf(blue, TeamSnapshot(
        "empty", Component.text("Empty"), true, emptyList(), null
    ))))
    val teamEvents = manager.events
    val isolatedManager = TeamManager()
    val isolatedPlayer = PluginPlayer.get(UUID(0, 99))
    val isolatedTeam = (isolatedManager.createNewTeam(isolatedPlayer, "blue", Component.text("Other Blue"))
        as TeamCreateResult.Created).team
    check(manager.getTeamById("blue") !== isolatedTeam)
    check(isolatedManager.removeTeam(isolatedTeam))

    val otherManager = TeamManager()
    val crossManagerPlayer = PluginPlayer.get(UUID(0, 98))
    var nestedCreateError: Throwable? = null
    val nestedCreate: (PlayerTeamEvent) -> Unit = {
        if (it.player === crossManagerPlayer) {
            nestedCreateError = runCatching {
                otherManager.createNewTeam(crossManagerPlayer, "nested", Component.text("Nested"))
            }.exceptionOrNull()
        }
    }
    manager.beforeJoin.subscribe(nestedCreate)
    val outerCreate = manager.createNewTeam(crossManagerPlayer, "outer", Component.text("Outer"))
    manager.beforeJoin.unsubscribe(nestedCreate)
    check(outerCreate is TeamCreateResult.Created && nestedCreateError is IllegalStateException)
    check(outerCreate.team.players == listOf(crossManagerPlayer) && otherManager.getTeamById("nested") == null)

    val restorePlayer = PluginPlayer.get(UUID(0, 89))
    var restoreError: Throwable? = null
    val nestedRestore: (PlayerTeamEvent) -> Unit = {
        if (it.player === restorePlayer) {
            restoreError = runCatching {
                TeamManager(TeamsSnapshot(teams = listOf(TeamSnapshot(
                    "restored", Component.text("Restored"), false,
                    listOf(restorePlayer.uuid), restorePlayer.uuid
                ))))
            }.exceptionOrNull()
        }
    }
    manager.beforeJoin.subscribe(nestedRestore)
    val restoredOuter = manager.createNewTeam(restorePlayer, "restore-outer", Component.text("Outer"))
    manager.beforeJoin.unsubscribe(nestedRestore)
    check(restoredOuter is TeamCreateResult.Created && restoreError is IllegalStateException)
    check(restorePlayer.team === restoredOuter.team)
    check(manager.removeTeam(restoredOuter.team) && manager.removeTeam(outerCreate.team))
    val afterUnlock = otherManager.createNewTeam(crossManagerPlayer, "after-unlock", Component.text("After"))
    check(afterUnlock is TeamCreateResult.Created)
    check(otherManager.removeTeam(afterUnlock.team))

    val foreignPlayer = PluginPlayer.get(UUID(0, 97))
    val foreignManager = TeamManager()
    val foreignTeam = (foreignManager.createNewTeam(foreignPlayer, "foreign", Component.text("Foreign"))
        as TeamCreateResult.Created).team
    check(!manager.changeTeamName(foreignTeam, Component.text("Broken")))
    check(!manager.transferOwnership(foreignTeam, foreignPlayer))
    check(manager.tryKickPlayerFromTeam(foreignTeam, foreignPlayer) === PlayerKickResult.TEAM_REMOVED)
    check(manager.tryAddPlayerToTeam(foreignTeam, foreignPlayer) === PlayerJoinResult.TEAM_REMOVED)
    check(!manager.removeTeam(foreignTeam) && !manager.removeTeamFromCommand(foreignTeam))
    check(foreignManager.getTeamById("foreign") === foreignTeam && foreignTeam.id == "foreign")
    check(foreignPlayer.team === foreignTeam && foreignTeam.tryKickPlayerFromTeam(foreignPlayer).isSuccess)

    val teamA = checkNotNull(manager.getTeamById("blue"))
    val teamB = checkNotNull(manager.getTeamById("empty"))
    check(PluginPlayer.get(ids[0]) === a && a.team === teamA)
    check(teamA.players == listOf(a, b) && teamA.owner === a)
    check(runCatching { (teamA.players as MutableList<*>).clear() }
        .exceptionOrNull() is UnsupportedOperationException)
    check(runCatching { (manager.getAllTeams() as MutableList<*>).clear() }
        .exceptionOrNull() is UnsupportedOperationException)
    check(runCatching { (manager.snapshot().teams as MutableList<*>).clear() }
        .exceptionOrNull() is UnsupportedOperationException)
    check(runCatching { (manager.snapshot().teams.first().members as MutableList<*>).clear() }
        .exceptionOrNull() is UnsupportedOperationException)
    check(manager.snapshot().teams.first().members == listOf(ids[0], ids[1]))

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
    teamEvents.afterCreate.subscribe(observedCreate)
    teamEvents.afterJoin.subscribe(observedJoin)
    var renameDelivered = false
    val observeRename: (TeamRenameChange) -> Unit = {
        if (it.team === teamA && it.newId == "claimed") {
            check(manager.getTeamById("claimed") === teamA)
            renameDelivered = true
        }
    }
    val claimId: (PlayerTeamEvent) -> Unit = {
        if (it.player === creator && it.team.id == "claimed") {
            check(teamA.changeTeamName(Component.text("claimed")))
        }
    }
    teamEvents.afterRename.subscribe(observeRename)
    teamEvents.beforeJoin.subscribe(claimId)
    check(manager.createNewTeam(creator, "claimed", Component.text("Claimed")) ===
        TeamCreateResult.ID_ALREADY_EXISTS)
    teamEvents.beforeJoin.unsubscribe(claimId)
    teamEvents.afterRename.unsubscribe(observeRename)
    check(renameDelivered && manager.getTeamById("claimed") === teamA)
    check(manager.getTeamById("blue") == null)
    check(manager.getAllTeams() == listOf(teamA, teamB))
    check(teamA.players == listOf(a, b) && teamA.owner === a && a.team === teamA && b.team === teamA)
    check(manager.snapshot().teams.first().let {
        it.id == "claimed" && it.members == listOf(ids[0], ids[1]) && it.ownerUuid == ids[0]
    })
    check(creator.team == null && creationEvents.isEmpty())
    check(teamA.changeTeamName(Component.text("blue")))

    val cancelCreationJoin: (PlayerTeamEvent) -> Unit = {
        if (it.player === creator && it.team.id == "vetoed") it.canceled = true
    }
    teamEvents.beforeJoin.subscribe(cancelCreationJoin)
    check(manager.createNewTeam(creator, "vetoed", Component.text("Vetoed")) ===
        TeamCreateResult.CANCELLED)
    teamEvents.beforeJoin.unsubscribe(cancelCreationJoin)
    check(manager.getTeamById("vetoed") == null && creator.team == null)
    check(manager.getAllTeams() == listOf(teamA, teamB) && creationEvents.isEmpty())

    val created = manager.createNewTeam(creator, "created", Component.text("Created"))
    check(created is TeamCreateResult.Created)
    check(created.team.owner === creator && created.team.players == listOf(creator))
    check(creator.team === created.team && manager.getTeamById("created") === created.team)
    check(creationEvents == listOf("create", "join"))
    teamEvents.afterCreate.unsubscribe(observedCreate)
    teamEvents.afterJoin.unsubscribe(observedJoin)
    check(manager.removeTeam(created.team))
    check(creator.team == null && manager.getTeamById("created") == null)
    check(manager.getAllTeams() == listOf(teamA, teamB))

    check(teamA.tryKickPlayerFromTeam(a).isSuccess)
    check(a.team == null && teamA.owner === b)
    check(teamA.transferOwnership(a).not())
    check(teamA.tryKickPlayerFromTeam(b).isSuccess)
    check(teamA.owner == null && teamA.players.isEmpty() && manager.getTeamById("blue") === teamA)
    check(teamA.tryAddPlayerToTeam(c).isSuccess && teamA.owner === c)
    check(teamA.transferOwnership(c))

    val vetoJoin: (PlayerTeamEvent) -> Unit = { if (it.team === teamB) it.canceled = true }
    teamEvents.beforeJoin.subscribe(vetoJoin)
    // B's join is cancelled only after A's fully completed leave.
    check(teamB.tryAddPlayerToTeam(c) === PlayerJoinResult.CANCELLED)
    check(c.team == null && teamA.players.isEmpty() && teamB.players.isEmpty())
    teamEvents.beforeJoin.unsubscribe(vetoJoin)

    check(teamA.changeTeamName(Component.text("renamed")))
    check(manager.getTeamById("blue") == null && manager.getTeamById("renamed") === teamA)
    val guardOnRename: (dev.necr0manthre.innotournament.teams.events.TeamRenameEvent) -> Unit = {
        if (it.team === teamA) {
            val unrelated = manager.createNewTeam(e, "unrelated-during-rename", Component.text("Unrelated"))
            check(unrelated is TeamCreateResult.Created)
            check(manager.removeTeam(unrelated.team))
            check(teamB.changeTeamName(Component.text("unrelated team")))
            check(teamB.changeTeamName(Component.text("empty")))
            check(runCatching { teamA.tryKickPlayerFromTeam(c) }
                .exceptionOrNull() is IllegalStateException)
        }
    }
    teamEvents.beforeRename.subscribe(guardOnRename)
    check(teamA.changeTeamName(Component.text("renamed again")))
    teamEvents.beforeRename.unsubscribe(guardOnRename)

    // Guard only this team's active rename, including otherwise successful no-ops.
    var beforeRenames = 0
    var afterRenames = 0
    var acceptAllGuard: Throwable? = null
    var acceptAllChangeGuard: Throwable? = null
    val nestedBeforeRename: (TeamRenameEvent) -> Unit = {
        if (it.team === teamA) {
            beforeRenames++
            check(teamA.id == it.oldId && teamA.displayName == it.oldName)
            check(runCatching { teamA.changeTeamName(teamA.displayName) }
                .exceptionOrNull() is IllegalStateException)
            check(runCatching { teamA.changeTeamName(Component.text("nested before")) }
                .exceptionOrNull() is IllegalStateException)
            check(teamB.changeTeamName(Component.text("other before")))
            acceptAllGuard = runCatching { teamA.acceptAll = false }.exceptionOrNull()
            acceptAllChangeGuard = runCatching { teamA.acceptAll = true }.exceptionOrNull()
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
    teamEvents.beforeRename.subscribe(nestedBeforeRename)
    teamEvents.afterRename.subscribe(nestedAfterRename)
    check(teamA.changeTeamName(Component.text("guarded rename")))
    teamEvents.beforeRename.unsubscribe(nestedBeforeRename)
    teamEvents.afterRename.unsubscribe(nestedAfterRename)
    check(beforeRenames == 1 && afterRenames == 1)
    check(acceptAllGuard is IllegalStateException && acceptAllChangeGuard is IllegalStateException)
    teamA.acceptAll = true
    check(teamA.acceptAll)
    check(manager.getTeamById("guarded rename") === teamA)
    check(manager.getTeamById("other after") === teamB)
    check(manager.getTeamById("nested before") == null)
    check(manager.getTeamById("nested after") == null)

    // A is not being renamed here: B's callback may still rename A.
    var crossTeamRenames = 0
    val renameFromOtherCallback: (TeamRenameChange) -> Unit = {
        if (it.team === teamB) {
            crossTeamRenames++
            check(teamA.changeTeamName(Component.text("rename from B callback")))
        }
    }
    teamEvents.afterRename.subscribe(renameFromOtherCallback)
    check(teamB.changeTeamName(Component.text("other trigger")))
    teamEvents.afterRename.unsubscribe(renameFromOtherCallback)
    check(crossTeamRenames == 1 && manager.getTeamById("rename from B callback") === teamA)
    check(teamB.changeTeamName(Component.text("empty")))

    val vetoRename: (TeamRenameEvent) -> Unit = {
        if (it.team === teamA) {
            check(runCatching { teamA.changeTeamName(teamA.displayName) }
                .exceptionOrNull() is IllegalStateException)
            it.canceled = true
        }
    }
    teamEvents.beforeRename.subscribe(vetoRename)
    check(!teamA.changeTeamName(Component.text("vetoed rename")))
    teamEvents.beforeRename.unsubscribe(vetoRename)
    check(manager.getTeamById("rename from B callback") === teamA)
    check(teamA.changeTeamName(Component.text("after veto")))

    var uncaughtRecursiveRenames = 0
    val uncaughtRecursiveRename: (TeamRenameEvent) -> Unit = {
        if (it.team === teamA) {
            uncaughtRecursiveRenames++
            teamA.changeTeamName(Component.text("recursive exception"))
        }
    }
    teamEvents.beforeRename.subscribe(uncaughtRecursiveRename)
    check(runCatching { teamA.changeTeamName(Component.text("outer exception")) }
        .exceptionOrNull() is IllegalStateException)
    teamEvents.beforeRename.unsubscribe(uncaughtRecursiveRename)
    check(uncaughtRecursiveRenames == 1 && manager.getTeamById("after veto") === teamA)
    check(teamA.changeTeamName(Component.text("after recursive exception")))

    val beforeFailure = IllegalArgumentException("before rename failure")
    val throwBeforeRename: (TeamRenameEvent) -> Unit = {
        if (it.team === teamA) throw beforeFailure
    }
    teamEvents.beforeRename.subscribe(throwBeforeRename)
    check(runCatching { teamA.changeTeamName(Component.text("uncommitted rename")) }
        .exceptionOrNull() === beforeFailure)
    teamEvents.beforeRename.unsubscribe(throwBeforeRename)
    check(manager.getTeamById("after recursive exception") === teamA)
    check(teamA.changeTeamName(Component.text("recovered before")))

    val afterFailure = IllegalArgumentException("after rename failure")
    val throwAfterRename: (TeamRenameChange) -> Unit = {
        if (it.team === teamA) {
            check(runCatching { teamA.changeTeamName(teamA.displayName) }
                .exceptionOrNull() is IllegalStateException)
            throw afterFailure
        }
    }
    teamEvents.afterRename.subscribe(throwAfterRename)
    check(runCatching { teamA.changeTeamName(Component.text("committed before failure")) }
        .exceptionOrNull() === afterFailure)
    teamEvents.afterRename.unsubscribe(throwAfterRename)
    check(manager.getTeamById("committed before failure") === teamA)
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
            check(runCatching { teamA.changeTeamName(Component.text("renamed during owner transfer")) }
                .exceptionOrNull() is IllegalStateException)
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
    teamEvents.beforeOwnerTransfer.subscribe(blockNestedTransfer)
    teamEvents.afterOwnerTransfer.subscribe(observeOwnerTransfer)
    check(teamA.transferOwnership(e) && teamA.owner === e)
    teamEvents.beforeOwnerTransfer.unsubscribe(blockNestedTransfer)
    teamEvents.afterOwnerTransfer.unsubscribe(observeOwnerTransfer)
    check(beforeTransfers == 1 && afterTransfers == 1)
    check(manager.getTeamById("renamed during owner transfer") == null)

    var failingTransferCalls = 0
    val uncaughtNestedTransfer: (TeamOwnerTransferEvent) -> Unit = {
        if (it.team === teamA) {
            failingTransferCalls++
            teamA.transferOwnership(d)
        }
    }
    teamEvents.beforeOwnerTransfer.subscribe(uncaughtNestedTransfer)
    check(runCatching { teamA.transferOwnership(d) }.exceptionOrNull() is IllegalStateException)
    teamEvents.beforeOwnerTransfer.unsubscribe(uncaughtNestedTransfer)
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
    teamEvents.afterLeave.subscribe(forbiddenMove)
    teamEvents.afterLeave.subscribe(laterListener)
    teamEvents.beforeJoin.subscribe(observeBeforeJoin)
    check(runCatching { teamB.tryAddPlayerToTeam(d) }.exceptionOrNull() is IllegalStateException)
    check(d.team == null && !teamA.players.contains(d) && !teamB.players.contains(d))
    check(!laterListenerCalled && !enteredBeforeJoinB)
    teamEvents.afterLeave.unsubscribe(forbiddenMove)
    teamEvents.afterLeave.unsubscribe(laterListener)
    teamEvents.beforeJoin.unsubscribe(observeBeforeJoin)
    check(teamB.tryAddPlayerToTeam(d).isSuccess && teamB.owner === d)

    val sourceManager = TeamManager()
    val destinationManager = TeamManager()
    val unrelatedManager = TeamManager()
    val movingPlayer = PluginPlayer.get(UUID(0, 96))
    val sourceTeammate = PluginPlayer.get(UUID(0, 95))
    val destinationOwner = PluginPlayer.get(UUID(0, 94))
    val thirdTeamOwner = PluginPlayer.get(UUID(0, 93))
    val thirdTeamMember = PluginPlayer.get(UUID(0, 92))
    val sourceTeam = (sourceManager.createNewTeam(movingPlayer, "source", Component.text("Source"))
        as TeamCreateResult.Created).team
    check(sourceTeam.tryAddPlayerToTeam(sourceTeammate).isSuccess)
    val destinationTeam = (destinationManager.createNewTeam(destinationOwner, "destination", Component.text("Destination"))
        as TeamCreateResult.Created).team
    val thirdTeam = (unrelatedManager.createNewTeam(thirdTeamOwner, "third", Component.text("Third"))
        as TeamCreateResult.Created).team
    check(thirdTeam.tryAddPlayerToTeam(thirdTeamMember).isSuccess)
    var sourceVetoCalls = 0
    val vetoSourceLeave: (PlayerTeamEvent) -> Unit = {
        if (it.player === movingPlayer && it.team === sourceTeam) {
            sourceVetoCalls++
            it.canceled = true
        }
    }
    sourceManager.beforeLeave.subscribe(vetoSourceLeave)
    check(destinationTeam.tryAddPlayerToTeam(movingPlayer).kickResult == PlayerKickResult.CANCELLED)
    check(sourceVetoCalls == 1 && movingPlayer.team === sourceTeam)
    sourceManager.beforeLeave.unsubscribe(vetoSourceLeave)

    var unrelatedMutationSucceeded = false
    var nestedThirdManagerCreate: Throwable? = null
    var nestedSourceTeamMove: Throwable? = null
    var nestedTargetTeamKick: Throwable? = null
    val tryDifferentPlayer: (TeamChange) -> Unit = {
        if (it.player === movingPlayer && it.team === sourceTeam) {
            unrelatedMutationSucceeded = thirdTeam.tryAddPlayerToTeam(c).isSuccess
            nestedThirdManagerCreate = runCatching {
                unrelatedManager.createNewTeam(movingPlayer, "reserved", Component.text("Reserved"))
            }.exceptionOrNull()
            nestedSourceTeamMove = runCatching { thirdTeam.tryAddPlayerToTeam(sourceTeammate) }.exceptionOrNull()
            nestedTargetTeamKick = runCatching { destinationTeam.tryKickPlayerFromTeam(destinationOwner) }.exceptionOrNull()
        }
    }
    var sourceAfterLeave = 0
    var targetAfterLeave = 0
    var targetAfterJoin = 0
    val observeSourceLeave: (TeamChange) -> Unit = {
        if (it.player === movingPlayer && it.team === sourceTeam) sourceAfterLeave++
    }
    val observeWrongTargetLeave: (TeamChange) -> Unit = {
        if (it.player === movingPlayer) targetAfterLeave++
    }
    val observeTargetJoin: (TeamChange) -> Unit = {
        if (it.player === movingPlayer && it.team === destinationTeam) targetAfterJoin++
    }
    sourceManager.afterLeave.subscribe(tryDifferentPlayer)
    sourceManager.afterLeave.subscribe(observeSourceLeave)
    destinationManager.afterLeave.subscribe(observeWrongTargetLeave)
    destinationManager.afterJoin.subscribe(observeTargetJoin)
    check(destinationTeam.tryAddPlayerToTeam(movingPlayer).isSuccess)
    sourceManager.afterLeave.unsubscribe(tryDifferentPlayer)
    sourceManager.afterLeave.unsubscribe(observeSourceLeave)
    destinationManager.afterLeave.unsubscribe(observeWrongTargetLeave)
    destinationManager.afterJoin.unsubscribe(observeTargetJoin)
    check(sourceAfterLeave == 1 && movingPlayer.team === destinationTeam)
    check(targetAfterLeave == 0 && targetAfterJoin == 1)
    check(unrelatedMutationSucceeded && nestedThirdManagerCreate is IllegalStateException)
    check(nestedSourceTeamMove is IllegalStateException && nestedTargetTeamKick is IllegalStateException)
    check(unrelatedManager.getTeamById("reserved") == null && sourceTeam.players == listOf(sourceTeammate))

    val throwSourceLeave: (TeamChange) -> Unit = {
        if (it.player === movingPlayer && it.team === destinationTeam) error("after leave failure")
    }
    destinationManager.afterLeave.subscribe(throwSourceLeave)
    check(runCatching { sourceTeam.tryAddPlayerToTeam(movingPlayer) }.isFailure)
    destinationManager.afterLeave.unsubscribe(throwSourceLeave)
    check(movingPlayer.team == null)
    val afterExceptionUnlock = unrelatedManager.createNewTeam(movingPlayer, "after-exception", Component.text("After"))
    check(afterExceptionUnlock is TeamCreateResult.Created)
    check(unrelatedManager.removeTeam(afterExceptionUnlock.team))
    check(sourceManager.removeTeam(sourceTeam))
    check(destinationManager.removeTeam(destinationTeam) && unrelatedManager.removeTeam(thirdTeam))

    val vetoDelete: (PlayerTeamEvent) -> Unit = { if (it.team === teamB) it.canceled = true }
    teamEvents.beforeLeave.subscribe(vetoDelete)
    check(!manager.removeTeam(teamB))
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
    teamEvents.afterLeave.subscribe(observeLeave)
    teamEvents.afterRemove.subscribe(observeRemove)
    check(manager.removeTeamFromCommand(teamB))
    check(afterLeave == TeamChange(d, teamB, 1, 0) && afterRemove)
    check(teamB.owner == null && teamB.players.isEmpty() && d.team == null)
    check(manager.getTeamById("empty") == null)
    check(teamB.tryAddPlayerToTeam(d) === PlayerJoinResult.TEAM_REMOVED)
    teamEvents.beforeLeave.unsubscribe(vetoDelete)
    teamEvents.afterLeave.unsubscribe(observeLeave)
    teamEvents.afterRemove.unsubscribe(observeRemove)
    check(manager.getAllTeams() == listOf(teamA))
    verifyBulkRemovalFrozenSizes(manager)
    verifyDeletedTeamRanking(manager, teamA, d, e)

    println("TeamModelInvariantCheck: OK")
}

private fun verifyBulkRemovalFrozenSizes(manager: TeamManager) {
    val teamEvents = manager.events
    for ((force, size) in listOf(false to 3, true to 4)) {
        val id = if (force) "bulk-force" else "bulk-ordinary"
        val players = (0 until size).map { PluginPlayer.get(UUID(0, 100L + (if (force) 10 else 0) + it)) }
        val team = (manager.createNewTeam(players.first(), id, Component.text(id))
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
                check(manager.getTeamById(id) == null)
                changes += it
            }
        }
        val afterRemove: (PluginTeam) -> Unit = {
            if (it === team) {
                check(changes.size == size)
                removed = true
            }
        }
        teamEvents.beforeRemove.subscribe(beforeRemove)
        teamEvents.beforeLeave.subscribe(beforeLeave)
        teamEvents.afterLeave.subscribe(afterLeave)
        teamEvents.afterRemove.subscribe(afterRemove)
        try {
            if (!force) {
                check(!manager.removeTeam(team)) // Middle leave vetoes the entire delete.
                check(beforeSizes == listOf(size, size) && changes.isEmpty() && !removed)
                check(team.players == players && team.owner === players.first())
                check(players.all { it.team === team } && manager.getTeamById(id) === team)
                vetoMiddle = false
                beforeSizes.clear()
            }
            check(if (force) manager.removeTeamFromCommand(team) else manager.removeTeam(team))
            check(beforeSizes == List(size) { size })
            check(changes == players.mapIndexed { index, member ->
                TeamChange(member, team, size - index, size - index - 1)
            })
            check(removed && manager.getTeamById(id) == null)
        } finally {
            teamEvents.beforeRemove.unsubscribe(beforeRemove)
            teamEvents.beforeLeave.unsubscribe(beforeLeave)
            teamEvents.afterLeave.unsubscribe(afterLeave)
            teamEvents.afterRemove.unsubscribe(afterRemove)
        }
    }
}

private fun verifyDeletedTeamRanking(manager: TeamManager, activeTeam: PluginTeam, player: PluginPlayer, lastMember: PluginPlayer) {
    val teamEvents = manager.events
    val origin = Location(null, 0.0, 0.0, 0.0)
    val tournament = Tournament(manager, origin, origin, null, origin, GameMode.SURVIVAL,
        emptyMap(), emptyMap(), doubleArrayOf(1.0), 0f)
    val currentRecord = checkNotNull(tournament.getOrCreateTournamentTeam(activeTeam))
    activeTeam.acceptAll = true
    tournament.setupTeam(currentRecord)
    check(!activeTeam.acceptAll)
    currentRecord.score = 25.0
    Tournament::class.java.getDeclaredField("phase").apply { isAccessible = true }
        .set(tournament, TournamentPhase.STARTED)

    val initializeDuringJoin = Tournament::class.java.getDeclaredMethod(
        "checkTeam", PluginTeam::class.java, Boolean::class.javaPrimitiveType
    ).apply { isAccessible = true }
    val setupDuringJoin: (TeamChange) -> Unit = {
        initializeDuringJoin.invoke(tournament, it.team, false)
    }
    teamEvents.afterJoin.subscribe(setupDuringJoin)
    val leadingTeam = try {
        (manager.createNewTeam(player, "leader", Component.text("Leader"))
            as TeamCreateResult.Created).team
    } finally {
        teamEvents.afterJoin.unsubscribe(setupDuringJoin)
    }
    check(!leadingTeam.acceptAll && tournament.getTeam(leadingTeam) != null)
    val historicalRecord = checkNotNull(tournament.getOrCreateTournamentTeam(leadingTeam))
    historicalRecord.score = 100.0

    val output = PlainTextComponentSerializer.plainText()
    val standingsMethod = Tournament::class.java.getDeclaredMethod("finalStandingsMessage")
        .apply { isAccessible = true }
    fun standings() = output.serialize(standingsMethod.invoke(tournament) as Component)
    val remainingName = output.serialize(activeTeam.displayName)
    val onlyRemaining = "Tournament ended. Results: 1. $remainingName (25.0)"

    val onRemove: (PluginTeam) -> Unit = tournament::onTeamRemoved
    teamEvents.afterRemove.subscribe(onRemove)
    try {
        check(tournament.getTopTeams(1) == listOf(historicalRecord))
        check(standings() == "Tournament ended. Results: 1. Leader (100.0), 2. $remainingName (25.0)")
        check(manager.removeTeamFromCommand(leadingTeam))
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

        val replacement = (manager.createNewTeam(player, "leader", Component.text("Replacement"))
            as TeamCreateResult.Created).team
        val replacementRecord = checkNotNull(tournament.getOrCreateTournamentTeam(replacement))
        replacementRecord.score = 10.0
        check(replacement !== leadingTeam && replacementRecord !== historicalRecord)
        check(manager.getTeamById("leader") === replacement)
        check(tournament.getOrCreateTournamentTeam(leadingTeam) == null)
        check(tournament.getTeam(leadingTeam) == null && records.size == 3)
        check(tournament.getTopTeams(10) == listOf(currentRecord, replacementRecord))
        check(standings() == "$onlyRemaining, 2. Replacement (10.0)")

        check(manager.removeTeamFromCommand(activeTeam))
        check(manager.removeTeamFromCommand(replacement))
        check(tournament.getTopTeams(10).isEmpty())
        check(standings() == "Tournament ended") // History exists, no active places.
        check(records.size == 3 && historicalRecord in records)
    } finally {
        teamEvents.afterRemove.unsubscribe(onRemove)
    }
}

class TeamModelInvariantTest {
    @Test
    fun invariants() = main()
}
