package dev.necr0manthre.innotournament.tournament.events

import com.google.gson.JsonParser
import dev.necr0manthre.innotournament.init.InnoTournamentEventHandlers
import dev.necr0manthre.innotournament.init.InnoTournamentEvents
import dev.necr0manthre.innotournament.players.PluginPlayer
import dev.necr0manthre.innotournament.teams.PluginTeam.TeamManager
import dev.necr0manthre.innotournament.teams.TeamCreateResult
import dev.necr0manthre.innotournament.tournament.Tournament
import dev.necr0manthre.innotournament.tournament.events.event_data.ISourcePlayerProvider
import dev.necr0manthre.innotournament.tournament.events.event_data.ITeamProvider
import dev.necr0manthre.innotournament.tournament.events.event_data.PlayerHolder
import dev.necr0manthre.innotournament.tournament.events.parsing.parsers.MultiArgumentParser
import dev.necr0manthre.innotournament.tournament.events.parsing.parsers.OneArgumentParser
import dev.necr0manthre.innotournament.tournament.events.parsing.parsers.TwoArgumentParser
import net.kyori.adventure.text.Component
import org.bukkit.GameMode
import org.bukkit.Location
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class EventProtocolTest {
    @TempDir lateinit var temp: Path
    private val teamManager = TeamManager()

    companion object {
        @JvmStatic @BeforeAll fun registerCatalog() {
            InnoTournamentEvents.init()
            InnoTournamentEventHandlers.init()
        }
    }

    private fun passiveTournament(): Tournament = Tournament(
        teamManager, Location(null, 0.0, 0.0, 0.0), Location(null, 0.0, 0.0, 0.0),
        null, Location(null, 0.0, 0.0, 0.0), GameMode.SURVIVAL,
        EventPlan.of(emptyList()), EventPlan.of(emptyList()), doubleArrayOf(), 0f
    )

    private fun load(json: String, stage: EventPlanStage = EventPlanStage.MAIN): EventPlan =
        EventLoader.loadEvents(JsonParser.parseString(json), temp.resolve("events.json"), stage)

    private class Source<T>(override val valueType: ValueType<T>) : TournamentEvent<T> {
        var subscriptions = 0
        var removals = 0
        val emitters = mutableListOf<SimpleEvent<T>>()

        override fun subscribe(tournament: Tournament): TournamentEventInstance<T> {
            subscriptions++
            val backing = SimpleEvent<T>()
            emitters.add(backing)
            return TournamentEventInstance(tournament, backing) { removals++ }
        }

        fun emit(value: T) = emitters.last().invoke(value)
    }

    private fun <T> rule(source: TournamentEvent<T>, vararg actions: ParsedListener<*>): EventRule =
        EventRule(source, EventRuleLocation(Path.of("real-event-file.json"), "test"), actions.mapIndexed { index, listener ->
            RuleAction(EventActionLocation(Path.of("real-event-file.json"), "test", index, "action-$index"), listener)
        })

    @Test fun descriptorsRejectRawGenericAndEnforceDirectionAndNullability() {
        val string = ValueType.of(String::class)
        val text = ValueType.of(CharSequence::class)
        val any = ValueType.of(Any::class)
        assertTrue(text.accepts(string))
        assertFalse(string.accepts(text))
        assertFalse(any.accepts(any.nullable()))
        assertTrue(any.nullable().accepts(string))
        assertTrue(any.nullable().accepts(any.nullable()))
        assertThrows(IllegalArgumentException::class.java) { ValueType.of(List::class) }
        assertTrue(ValueType.of(ITeamProvider::class).accepts(ValueType.of(PlayerHolder::class)))
        assertTrue(ValueType.of(ISourcePlayerProvider::class).accepts(ValueType.of(PlayerHolder::class)))
    }

    @Test fun malformedValueAndUnknownActionAndPhaseAreRejectedBeforeSubscription() {
        assertEquals(1, load("""{"time 0": []}""").entries.size) // parsing does not subscribe
        assertThrows(EventPlanLoadException::class.java) { load("[]") }
        assertThrows(EventPlanLoadException::class.java) { load("""{"undefined": "log"}""") }
        assertThrows(EventPlanLoadException::class.java) { load("""{"time 0": ["log", 42]}""") }
        assertThrows(EventPlanLoadException::class.java) { load("""{"time 0": "unknownAction"}""") }
        assertThrows(EventPlanLoadException::class.java) { load("""{"onStart": "log"}""", EventPlanStage.PRE_START) }
        assertThrows(EventPlanLoadException::class.java) { load("""{"onPrepare": "log"}""", EventPlanStage.MAIN) }
        assertThrows(EventPlanLoadException::class.java) { load("""{"onEnd": "place_start_box"}""") }
        assertThrows(EventPlanLoadException::class.java) { load("""{"onPrepare": "add_score 1"}""", EventPlanStage.PRE_START) }
        assertThrows(EventPlanLoadException::class.java) { load("""{"playerKilled": "add_score_by_lives 1 2 BAD"}""") }
        assertThrows(EventPlanLoadException::class.java) { load("""{"playerTick 0": []}""") }
        assertThrows(EventPlanLoadException::class.java) { load("""{"time -1": []}""") }
    }

    @Test fun argumentParsersMatchOnlyAtTheStartWithoutChangingArgumentHandling() {
        val one = OneArgumentParser("time", { it })
        val two = TwoArgumentParser("world_border") { size, ticks -> size to ticks }
        val multi = MultiArgumentParser("add_score_by_lives") { it.toList() }
        val greedy = OneArgumentParser("execute_command", { it }, greedy = true)

        assertNull(one.tryParse("junk time 0"))
        assertNull(two.tryParse("junk world_border 100 21"))
        assertNull(multi.tryParse("junk add_score_by_lives 1 2"))
        assertNull(greedy.tryParse("not execute_command say hi"))

        assertEquals("0", one.tryParse("  time   0  "))
        assertEquals("100" to "21", two.tryParse("  world_border 100 21  "))
        assertEquals(listOf("1", "2"), multi.tryParse("  add_score_by_lives 1 2  "))
        assertEquals("say hi  there", greedy.tryParse(" execute_command say hi  there "))
        assertEquals("  say hi", greedy.tryParse("execute_command   say hi"))
        assertNull(one.tryParse("time 0 1"))
        assertNull(two.tryParse("world_border 100"))
        assertNull(two.tryParse("world_border 100 21 extra"))
    }

    @Test fun loaderRejectsGarbagePrefixesForEventsAndActionsWithoutExecutingThem() {
        val invalid = listOf(
            """{"junk time 0": "log"}""" to "unknown event expression",
            """{"time 0": "junk world_border 100 21"}""" to "unknown action",
            """{"playerKilled": "junk add_score_by_lives 1 2"}""" to "unknown action",
            """{"time 0": "not execute_command say hi"}""" to "unknown action"
        )
        for ((json, reason) in invalid) {
            val error = assertThrows(EventPlanLoadException::class.java) { load(json) }
            assertTrue(error.message!!.contains("events.json"), json)
            assertTrue(error.message!!.contains(reason), json)
        }
        assertEquals(1, load("""{" time 0 ": " world_border 100 21 "}""").entries.size)
        assertEquals(1, load("""{"time 0": "execute_command say hi there"}""").entries.size)
    }

    @Test fun scoreByLivesPreservesTeamGuardDirectIndexAndZeroLives() {
        val tournament = passiveTournament()
        val pluginPlayer = PluginPlayer.get(UUID.randomUUID())
        val created = teamManager.createNewTeam(pluginPlayer, "score-by-lives-${pluginPlayer.uuid}", Component.text("Test"))
        assertTrue(created is TeamCreateResult.Created)
        val team = (created as TeamCreateResult.Created).team
        try {
            val player = checkNotNull(tournament.getOrCreateTournamentPlayer(pluginPlayer))
            val tournamentTeam = checkNotNull(player.team)
            val listener = checkNotNull(InnoTournamentEventHandlers.SCORE_FROM_LIVES
                .tryParse("add_score_by_lives 7")).listener

            player.lives = 1
            assertThrows(IndexOutOfBoundsException::class.java) {
                listener.listen(tournament, PlayerHolder(player))
            }
            assertEquals(0.0, tournamentTeam.score)

            player.lives = 0
            listener.listen(tournament, PlayerHolder(player))
            assertEquals(7.0, tournamentTeam.score)

            assertTrue(team.tryKickPlayerFromTeam(pluginPlayer).isSuccess)
            assertNull(player.team)
            player.lives = 1
            assertDoesNotThrow { listener.listen(tournament, PlayerHolder(player)) }
            assertEquals(7.0, tournamentTeam.score)
        } finally {
            assertTrue(teamManager.removeTeam(team))
        }
    }

    @Test fun lifecycleStageRulesMatchCanonicalAndWhitespaceEventNames() {
        val placements = listOf(
            Triple("onPrepare", EventPlanStage.PRE_START, EventPlanStage.MAIN),
            Triple("onStart", EventPlanStage.MAIN, EventPlanStage.PRE_START),
            Triple("onEnd", EventPlanStage.MAIN, EventPlanStage.PRE_START)
        )
        for ((name, allowed, rejected) in placements) {
            for (expression in listOf(name, " $name ")) {
                val json = """{"$expression": "log"}"""
                assertEquals(1, load(json, allowed).entries.size)
                val error = assertThrows(EventPlanLoadException::class.java) { load(json, rejected) }
                assertTrue(error.message!!.contains("events.json"))
                assertTrue(error.message!!.contains("event '$expression'"))
            }
        }
    }

    @Test fun lifecycleActionRulesCannotBeBypassedWithWhitespace() {
        for (expression in listOf("onPrepare", " onPrepare ")) {
            assertEquals(1, load("""{"$expression": "place_start_box"}""", EventPlanStage.PRE_START).entries.size)
            val error = assertThrows(EventPlanLoadException::class.java) {
                load("""{"$expression": "add_score 1"}""", EventPlanStage.PRE_START)
            }
            assertTrue(error.message!!.contains("action[0] 'add_score 1'"))
            assertTrue(error.message!!.contains("event '$expression'"))
            assertTrue(error.message!!.contains("PREPARED"))
        }
        for (expression in listOf("onStart", " onStart ")) {
            assertEquals(1, load("""{"$expression": "place_start_box"}""").entries.size)
            assertEquals(1, load("""{"$expression": "log"}""").entries.size)
            val error = assertThrows(EventPlanLoadException::class.java) {
                load("""{"$expression": "add_score 1"}""")
            }
            assertTrue(error.message!!.contains("event '$expression'"))
            assertTrue(error.message!!.contains("listener requires")) // phase allows it; payload does not
        }
        for (expression in listOf("onEnd", " onEnd ")) {
            assertEquals(1, load("""{"$expression": "log"}""").entries.size)
            for (action in listOf("place_start_box", "add_score 1")) {
                val error = assertThrows(EventPlanLoadException::class.java) {
                    load("""{"$expression": "$action"}""")
                }
                assertTrue(error.message!!.contains("events.json"))
                assertTrue(error.message!!.contains("event '$expression'"))
                assertTrue(error.message!!.contains("action[0] '$action'"))
                assertTrue(error.message!!.contains("ENDED"))
            }
        }
    }

    @Test fun incompatibleEventAndActionRejectedWithContextAtLoad() {
        val error = assertThrows(EventPlanLoadException::class.java) {
            load("""{"onStart": ["log", "add_score 5"]}""")
        }
        assertTrue(error.message!!.contains("events.json"))
        assertTrue(error.message!!.contains("onStart"))
        assertTrue(error.message!!.contains("action[1]"))
        assertTrue(error.message!!.contains("add_score 5"))
        assertTrue(error.message!!.contains("ITeamProvider"))
        assertTrue(error.message!!.contains("java.lang.Object"))
        assertEquals(2, load("""{"onStart": ["log", "log hello"], "time 0": []}""").entries.size)
    }

    @Test fun missingFileDoesNotBecomeSuccessfulEmptyPlan() {
        val path = temp.resolve("not-present.json")
        val error = assertThrows(EventPlanLoadException::class.java) {
            EventLoader.loadEvents(path, EventPlanStage.MAIN)
        }
        assertTrue(error.message!!.contains(path.toString()))
        Files.writeString(path, "{" )
        assertThrows(EventPlanLoadException::class.java) { EventLoader.loadEvents(path, EventPlanStage.MAIN) }
    }

    @Test fun checkedBindingsRunInOrderWithDuplicateActionsAndFailFast() {
        val source = Source(ValueType.of(String::class))
        val trace = mutableListOf<String>()
        val action = ParsedListener(ValueType.of(CharSequence::class),
            ITournamentEventListener<CharSequence> { _, value -> trace.add("action:$value") })
        val plan = EventLoader.bindRules(listOf(rule(source, action, action)))
        val tournament = passiveTournament()
        assertEquals(0, source.subscriptions)
        val instance = plan.entries.single().subscribe(tournament)
        assertEquals(1, source.subscriptions)
        source.emit("hello")
        assertEquals(listOf("action:hello", "action:hello"), trace)
        instance.unsubscribe()
        instance.unsubscribe()
        assertEquals(1, source.removals)
        source.emit("late")
        assertEquals(2, trace.size)
        val failure = IllegalStateException("stop here")
        val failed = EventLoader.bindRules(listOf(rule(source,
            action,
            ParsedListener(ValueType.of(Any::class).nullable(), ITournamentEventListener<Any?> { _, _ ->
                throw failure
            }),
            action)))
        failed.entries.single().subscribe(tournament)
        val thrown = assertThrows(EventDispatchException::class.java) { source.emit("again") }
        assertSame(failure, thrown.cause)
        assertTrue(thrown.message!!.contains("action[1]"))
        assertEquals(3, trace.size) // third action was not reached
    }

    @Test fun oneDefinitionHasIndependentInstancesForDifferentTournaments() {
        val source = Source(ValueType.of(String::class))
        val seen = mutableListOf<Tournament>()
        val listener = ParsedListener(ValueType.of(Any::class).nullable(),
            ITournamentEventListener<Any?> { tournament, _ -> seen.add(tournament) })
        val plan = EventLoader.bindRules(listOf(rule(source, listener)))
        val first = passiveTournament()
        val second = passiveTournament()
        val firstInstance = plan.entries.single().subscribe(first)
        val secondInstance = plan.entries.single().subscribe(second)
        source.emitters[0].invoke("first")
        source.emitters[1].invoke("second")
        assertEquals(listOf(first, second), seen)
        firstInstance.unsubscribe()
        source.emitters[0].invoke("late")
        source.emitters[1].invoke("still live")
        assertEquals(listOf(first, second, second), seen)
        secondInstance.unsubscribe()
    }

    @Test fun incompatibleOrNullableSourceIsRejectedWithoutCallingSubscribe() {
        val source = Source(ValueType.of(Any::class).nullable())
        val wrong = ParsedListener(ValueType.of(String::class), ITournamentEventListener<String> { _, _ -> })
        val error = assertThrows(EventPlanLoadException::class.java) {
            EventLoader.bindRules(listOf(rule(source, wrong)))
        }
        assertTrue(error.message!!.contains("listener requires"))
        assertEquals(0, source.subscriptions)
        val nonNullSource = Source(ValueType.of(String::class))
        val nullableAction = ParsedListener(ValueType.of(Any::class).nullable(), ITournamentEventListener<Any?> { _, _ -> })
        assertEquals(1, EventLoader.bindRules(listOf(rule(nonNullSource, nullableAction))).entries.size)
    }

    @Test fun planCopiesOrderAndDoesNotLeakMutableBackingList() {
        val first = Source(ValueType.of(String::class))
        val second = Source(ValueType.of(String::class))
        val actions = mutableListOf(RuleAction(
            EventActionLocation(temp.resolve("file.json"), "first", 0, "log"),
            ParsedListener(ValueType.of(String::class), ITournamentEventListener<String> { _, _ -> })
        ))
        val rule = EventRule(first, EventRuleLocation(temp.resolve("file.json"), "first"), actions)
        val rules = mutableListOf(rule, EventRule(second, EventRuleLocation(temp.resolve("file.json"), "second"), emptyList()))
        val plan = EventLoader.bindRules(rules)
        rules.clear()
        actions.clear()
        assertEquals(2, plan.entries.size)
        assertThrows(UnsupportedOperationException::class.java) {
            (plan.entries as MutableList<EventPlanEntry>).clear()
        }
        val tournament = passiveTournament()
        val instances = plan.entries.map { it.subscribe(tournament) }
        assertEquals(1, first.subscriptions)
        assertEquals(1, second.subscriptions)
        instances.forEach { it.unsubscribe() }
    }

    @Test fun callersRetainEachSuccessfullyReturnedInstanceBeforeLaterSourceFailure() {
        val first = Source(ValueType.of(String::class))
        val broken = object : TournamentEvent<String> {
            override val valueType = ValueType.of(String::class)
            override fun subscribe(tournament: Tournament): TournamentEventInstance<String> =
                throw IllegalArgumentException("source failed before returning an instance")
        }
        val plan = EventLoader.bindRules(listOf(rule(first), rule(broken)))
        val handles = mutableListOf<TournamentEventInstance<*>>()
        val tournament = passiveTournament()
        val thrown = assertThrows(EventSubscriptionException::class.java) {
            for (entry in plan.entries) handles.add(entry.subscribe(tournament))
        }
        assertTrue(thrown.cause is IllegalArgumentException)
        assertTrue(thrown.message!!.contains("real-event-file.json"))
        assertEquals(1, handles.size)
        assertEquals(1, first.subscriptions)
        handles.forEach { it.unsubscribe() }
        assertEquals(1, first.removals)
    }

    @Test fun anUnsubscribedInstanceRejectsNewListeners() {
        val tournament = passiveTournament()
        val source = Source(ValueType.of(String::class))
        val instance = source.subscribe(tournament)
        val events = mutableListOf<String>()
        instance.subscriptions.subscribe { events.add(it) }
        source.emit("before")
        assertEquals(listOf("before"), events)
        instance.unsubscribe()
        source.emit("after")
        assertEquals(listOf("before"), events)
        assertThrows(IllegalStateException::class.java) {
            instance.subscriptions.subscribe { events.add(it) }
        }
        assertThrows(IllegalStateException::class.java) {
            instance.addListener(ITournamentEventListener { _, _ -> })
        }
    }

    @Test fun simpleEventUsesSnapshotAndStopsAtFirstFailure() {
        val e = SimpleEvent<Int>()
        val trace = mutableListOf<Int>()
        val late: (Int) -> Unit = { trace.add(100) }
        e.subscribe {
            trace.add(it)
            e.subscribe(late)
        }
        e.invoke(1)
        assertEquals(listOf(1), trace)
        e.invoke(2)
        assertEquals(listOf(1, 2, 100), trace)

        val broken = SimpleEvent<Int>()
        broken.subscribe { throw IllegalStateException("fail") }
        broken.subscribe { trace.add(-1) }
        assertThrows(IllegalStateException::class.java) { broken.invoke(1) }
        assertFalse(trace.contains(-1))

        val closedDuringDispatch = SimpleEvent<Int>()
        var calledAfterClose = false
        closedDuringDispatch.subscribe { closedDuringDispatch.close() }
        closedDuringDispatch.subscribe { calledAfterClose = true }
        closedDuringDispatch.invoke(1)
        assertFalse(calledAfterClose)
    }
}
