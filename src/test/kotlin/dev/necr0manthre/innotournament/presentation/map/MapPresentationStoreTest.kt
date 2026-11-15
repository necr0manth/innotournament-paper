package dev.necr0manthre.innotournament.presentation.map

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class MapPresentationStoreTest {
    @TempDir
    lateinit var tempDir: Path

    private val area = MapAreaKey(UUID.fromString("00000000-0000-0000-0000-000000000001"), -17, 23, 4)

    @Test
    fun `records and published names round trip in insertion order`() {
        val path = tempDir.resolve(MapPresentationStore.FILE_NAME)
        val store = MapPresentationStore(path)

        assertTrue(store.addRecord(MapPresentationRecord(7, area)))
        assertTrue(store.addRecord(MapPresentationRecord(8, area.copy(centerX = 4))))
        assertTrue(store.rememberWaypointNames(listOf("generator_one", "generator_two")))
        assertFalse(store.addRecord(MapPresentationRecord(7, area)))
        assertFalse(store.rememberWaypointNames(listOf("generator_one")))

        val reloaded = MapPresentationStore(path)
        assertEquals(listOf(MapPresentationRecord(7, area), MapPresentationRecord(8, area.copy(centerX = 4))), reloaded.records)
        assertEquals(MapPresentationRecord(7, area), reloaded.recordForArea(area))
        assertEquals(linkedSetOf("generator_one", "generator_two"), reloaded.publishedWaypointNames)
        assertThrows(UnsupportedOperationException::class.java) { (reloaded.records as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (reloaded.publishedWaypointNames as MutableSet).clear() }
    }

    @Test
    fun `missing file starts with empty version one metadata`() {
        val store = MapPresentationStore(tempDir.resolve(MapPresentationStore.FILE_NAME))
        assertTrue(store.records.isEmpty())
        assertTrue(store.publishedWaypointNames.isEmpty())
    }

    @Test
    fun `malformed version and conflicting IDs fail without resetting metadata`() {
        val path = tempDir.resolve(MapPresentationStore.FILE_NAME)
        Files.writeString(path, """{"version":2,"maps":[],"publishedWaypointNames":[]}""")
        assertThrows(IOException::class.java) { MapPresentationStore(path) }

        Files.writeString(
            path,
            """{"version":1,"maps":[{"id":4,"worldUUID":"${area.worldId}","centerX":0,"centerZ":0,"scale":1},{"id":4,"worldUUID":"${area.worldId}","centerX":1,"centerZ":0,"scale":1}],"publishedWaypointNames":[]}"""
        )
        assertThrows(IOException::class.java) { MapPresentationStore(path) }
    }

    @Test
    fun `failed save does not publish the new record or name in memory`() {
        val blocker = tempDir.resolve("not-a-directory")
        Files.writeString(blocker, "file")
        val store = MapPresentationStore(blocker.resolve(MapPresentationStore.FILE_NAME))

        assertThrows(IOException::class.java) { store.addRecord(MapPresentationRecord(3, area)) }
        assertThrows(IOException::class.java) { store.rememberWaypointNames(listOf("not-published")) }
        assertTrue(store.records.isEmpty())
        assertTrue(store.publishedWaypointNames.isEmpty())
    }

    @Test
    fun `batch waypoint names are persisted together and remain idempotent`() {
        val path = tempDir.resolve(MapPresentationStore.FILE_NAME)
        val store = MapPresentationStore(path)

        assertTrue(store.rememberWaypointNames(listOf("generator_one", "generator_two", "generator_one")))
        assertFalse(store.rememberWaypointNames(listOf("generator_two", "generator_one")))
        assertEquals(linkedSetOf("generator_one", "generator_two"), MapPresentationStore(path).publishedWaypointNames)
        assertThrows(IllegalArgumentException::class.java) { store.rememberWaypointNames(listOf("generator_three", " ")) }
        assertEquals(linkedSetOf("generator_one", "generator_two"), store.publishedWaypointNames)
    }
}
