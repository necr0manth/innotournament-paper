package dev.necr0manthre.innotournament.teams.persistence

import com.google.gson.JsonParser
import dev.necr0manthre.innotournament.teams.TeamSnapshot
import dev.necr0manthre.innotournament.teams.TeamsSnapshot
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.UUID

class TeamsJsonStoreTest {
    @TempDir lateinit var directory: Path

    private val one = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val two = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val three = UUID.fromString("00000000-0000-0000-0000-000000000003")
    private val four = UUID.fromString("00000000-0000-0000-0000-000000000004")

    private fun path(): Path = directory.resolve("teams.json")

    @Test
    fun missingFileIgnoresAbandonedTemporaryFiles() {
        Files.writeString(directory.resolve(".teams.json.old.tmp"), "not a snapshot")
        val snapshot = TeamsJsonStore.load(path())
        assertEquals(1, snapshot.version)
        assertTrue(snapshot.teams.isEmpty())
        assertFalse(Files.exists(path()))
    }

    @Test
    fun danglingSymlinkIsAReadErrorAndRemainsUnchanged() {
        val target = Path.of("missing-real-data.json")
        try {
            Files.createSymbolicLink(path(), target)
        } catch (e: UnsupportedOperationException) {
            Assumptions.assumeTrue(false, "Symbolic links are unsupported")
        } catch (e: FileSystemException) {
            Assumptions.assumeTrue(false, "Cannot create symbolic link: ${e.message}")
        }

        val error = assertThrows(IOException::class.java) { TeamsJsonStore.load(path()) }
        assertTrue(error.message!!.contains(path().toString()))
        assertTrue(error.cause is NoSuchFileException)
        assertTrue(Files.isSymbolicLink(path()))
        assertEquals(target, Files.readSymbolicLink(path()))
    }

    @Test
    fun roundTripRetainsTeamOrderMembershipUnicodeAndRichComponents() {
        val decoratedName = Component.text("Золотая 猫", NamedTextColor.GOLD)
            .decorate(TextDecoration.BOLD)
            .append(Component.text(" + snow ❄", NamedTextColor.AQUA)
                .decorate(TextDecoration.ITALIC)
                .hoverEvent(HoverEvent.showText(Component.text("Подсказка"))))
            .clickEvent(ClickEvent.openUrl("https://example.org/team"))
        val initial = TeamsSnapshot(teams = listOf(
            TeamSnapshot("α-team", decoratedName, false, listOf(one, two, three), three),
            TeamSnapshot("empty", Component.text("Пустая команда"), true, emptyList(), null)
        ))
        TeamsJsonStore.save(path(), initial)

        val written = JsonParser.parseString(Files.readString(path())).asJsonObject
        assertEquals(1, written.get("version").asInt)
        assertTrue(written.getAsJsonArray("teams")[0].asJsonObject.get("displayName").isJsonObject)
        assertEquals("Пустая команда", written.getAsJsonArray("teams")[1].asJsonObject.get("displayName").asString)
        val loaded = TeamsJsonStore.load(path())
        assertEquals(initial.version, loaded.version)
        assertEquals(initial.teams.map { it.id }, loaded.teams.map { it.id })
        initial.teams.zip(loaded.teams).forEach { (expected, actual) ->
            assertEquals(expected.displayName, actual.displayName)
            assertEquals(expected.acceptAll, actual.acceptAll)
            assertEquals(expected.members, actual.members)
            assertEquals(expected.ownerUuid, actual.ownerUuid)
        }
    }

    @Test
    fun plainComponentJsonStringIsAccepted() {
        Files.writeString(path(), """{"version":1,"teams":[{"id":"plain","displayName":"Просто текст","acceptAll":true,"members":[],"ownerUuid":null}]}""")
        val team = TeamsJsonStore.load(path()).teams.single()
        assertEquals(Component.text("Просто текст"), team.displayName)
    }

    @Test
    fun malformedDocumentsAreRejectedAsWholeSnapshots() {
        val memberA = "00000000-0000-0000-0000-000000000001"
        val memberB = "00000000-0000-0000-0000-000000000002"
        fun team(id: String = "a", members: String = "[\"$memberA\"]", owner: String = "\"$memberA\"") =
            """{"id":"$id","displayName":{"text":"Name","bold":true},"acceptAll":true,"members":$members,"ownerUuid":$owner}"""
        fun doc(vararg teams: String) = """{"version":1,"teams":[${teams.joinToString(",") }]}"""
        val valid = doc(team())
        val invalid = linkedMapOf(
            "empty file" to "",
            "truncated" to valid.dropLast(1),
            "trailing JSON" to "$valid true",
            "comment" to "// note\n$valid",
            "unquoted key" to "{version:1,\"teams\":[]}",
            "version two" to valid.replace("\"version\":1", "\"version\":2"),
            "fractional version" to valid.replace("\"version\":1", "\"version\":1.5"),
            "decimal version" to valid.replace("\"version\":1", "\"version\":1.0"),
            "string version" to valid.replace("\"version\":1", "\"version\":\"1\""),
            "missing version" to """{"teams":[]}""",
            "missing teams" to """{"version":1}""",
            "null teams" to """{"version":1,"teams":null}""",
            "string team" to """{"version":1,"teams":["nope"]}""",
            "missing id" to valid.replace("\"id\":\"a\",", ""),
            "blank id" to doc(team("  ")),
            "numeric id" to valid.replace("\"id\":\"a\"", "\"id\":42"),
            "missing displayName" to valid.replace("\"displayName\":{\"text\":\"Name\",\"bold\":true},", ""),
            "number displayName" to valid.replace("\"displayName\":{\"text\":\"Name\",\"bold\":true}", "\"displayName\":12"),
            "boolean displayName" to valid.replace("\"displayName\":{\"text\":\"Name\",\"bold\":true}", "\"displayName\":false"),
            "invalid displayName" to valid.replace("\"displayName\":{\"text\":\"Name\",\"bold\":true}", "\"displayName\":{\"unsupportedComponentKind\":true}"),
            "empty displayName array" to valid.replace("\"displayName\":{\"text\":\"Name\",\"bold\":true}", "\"displayName\":[]"),
            "missing acceptAll" to valid.replace("\"acceptAll\":true,", ""),
            "string acceptAll" to valid.replace("\"acceptAll\":true", "\"acceptAll\":\"true\""),
            "missing members" to valid.replace("\"members\":[\"$memberA\"],", ""),
            "null members" to valid.replace("\"members\":[\"$memberA\"]", "\"members\":null"),
            "bad UUID" to doc(team(members = "[\"not-a-uuid\"]")),
            "short UUID" to doc(team(members = "[\"1-1-1-1-1\"]")),
            "duplicate id" to doc(team(), team(id = "a", members = "[\"$memberB\"]", owner = "\"$memberB\"")),
            "duplicate member within team" to doc(team(members = "[\"$memberA\",\"$memberA\"]")),
            "duplicate member across teams" to doc(team(), team(id = "b")),
            "owner not member" to doc(team(owner = "\"$memberB\"")),
            "nonempty without owner" to doc(team(owner = "null")),
            "empty with owner" to doc(team(members = "[]")),
            "missing owner" to valid.replace(",\"ownerUuid\":\"$memberA\"", ""),
            "owner wrong type" to doc(team(owner = "123")),
            "invalid later team" to doc(team(), team(id = "b", members = "[\"bad\"]"))
        )
        invalid.forEach { (case, json) ->
            Files.writeString(path(), json)
            val error = assertThrows(Exception::class.java, { TeamsJsonStore.load(path()) }, case)
            assertTrue(error is IllegalArgumentException || error is IOException, case)
            assertTrue(error.message!!.contains(path().toString()), case)
        }
    }

    @Test
    fun secondSaveAtomicallyReplacesWholeExistingFile() {
        TeamsJsonStore.save(path(), TeamsSnapshot(teams = listOf(
            TeamSnapshot("old-long-id", Component.text("old"), true, listOf(one, two), two)
        )))
        TeamsJsonStore.save(path(), TeamsSnapshot(teams = listOf(
            TeamSnapshot("new", Component.text("new"), false, emptyList(), null)
        )))
        val loaded = TeamsJsonStore.load(path())
        assertEquals(listOf("new"), loaded.teams.map { it.id })
        assertEquals(emptyList<UUID>(), loaded.teams.single().members)
        assertFalse(Files.readString(path()).contains("old-long-id"))
    }

    @Test
    fun invalidSnapshotNeverTouchesAnExistingTarget() {
        TeamsJsonStore.save(path(), TeamsSnapshot(teams = listOf(
            TeamSnapshot("original", Component.text("Original"), true, listOf(one), one)
        )))
        val bytes = Files.readAllBytes(path())
        val rejected = listOf(
            TeamsSnapshot(2, emptyList()),
            TeamsSnapshot(teams = listOf(TeamSnapshot(" ", Component.empty(), false, emptyList(), null))),
            TeamsSnapshot(teams = listOf(
                TeamSnapshot("a", Component.empty(), false, listOf(one, two), two),
                TeamSnapshot("b", Component.empty(), false, listOf(two, four), four)
            )),
            TeamsSnapshot(teams = listOf(TeamSnapshot("a", Component.empty(), false, listOf(three), null))),
            TeamsSnapshot(teams = listOf(TeamSnapshot("a", Component.empty(), false, emptyList(), one)))
        )
        rejected.forEach { snapshot ->
            assertThrows(IllegalArgumentException::class.java) { TeamsJsonStore.save(path(), snapshot) }
            assertArrayEquals(bytes, Files.readAllBytes(path()))
        }
    }

    @Test
    fun malformedUtf8AndExistingDirectoryAreNotMissingFiles() {
        Files.write(path(), byteArrayOf(0xc3.toByte(), 0x28))
        assertThrows(IllegalArgumentException::class.java) { TeamsJsonStore.load(path()) }
        Files.delete(path())
        Files.createDirectory(path())
        assertThrows(IOException::class.java) { TeamsJsonStore.load(path()) }
    }
}
