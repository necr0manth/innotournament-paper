package dev.necr0manthre.innotournament.teams.persistence

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import dev.necr0manthre.innotournament.teams.TeamSnapshot
import dev.necr0manthre.innotournament.teams.TeamsSnapshot
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import java.io.IOException
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Only the persistent team roster. The domain owner decides when to restore or snapshot. */
object TeamsJsonStore {
    private val components = GsonComponentSerializer.gson()
    private val uuidPattern = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    /** A missing target means a first run. A stale temporary file is never considered. */
    fun load(path: Path): TeamsSnapshot {
        val bytes = try {
            Files.readAllBytes(path)
        } catch (e: NoSuchFileException) {
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                throw IOException("Cannot read teams from $path", e)
            }
            return TeamsSnapshot(teams = emptyList())
        } catch (e: IOException) {
            throw IOException("Cannot read teams from $path", e)
        }

        try {
            val text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
            val reader = JsonReader(StringReader(text)).apply { strictness = Strictness.STRICT }
            val document = JsonParser.parseReader(reader)
            require(reader.peek() == JsonToken.END_DOCUMENT) { "trailing content after JSON" }
            return decode(document)
        } catch (e: RuntimeException) {
            throw IllegalArgumentException("Invalid teams JSON at $path: ${e.message}", e)
        } catch (e: java.nio.charset.CharacterCodingException) {
            throw IllegalArgumentException("Invalid UTF-8 in $path", e)
        } catch (e: IOException) {
            throw IOException("Cannot parse teams JSON at $path", e)
        }
    }

    /** Validate and encode before writing anything; replace only with a same-directory atomic move. */
    fun save(path: Path, snapshot: TeamsSnapshot) {
        val bytes = try {
            validate(snapshot)
            val root = JsonObject().apply {
                addProperty("version", snapshot.version)
                add("teams", JsonArray().apply {
                    snapshot.teams.forEach { team ->
                        add(JsonObject().apply {
                            addProperty("id", team.id)
                            val nameTree = components.serializeToTree(team.displayName)
                            require(nameTree.isJsonObject || nameTree.isJsonArray ||
                                (nameTree.isJsonPrimitive && nameTree.asJsonPrimitive.isString)) {
                                "team '${team.id}' displayName cannot be serialized as Component JSON"
                            }
                            add("displayName", nameTree)
                            addProperty("acceptAll", team.acceptAll)
                            add("members", JsonArray().apply {
                                team.members.forEach { add(it.toString()) }
                            })
                            if (team.ownerUuid == null) add("ownerUuid", com.google.gson.JsonNull.INSTANCE)
                            else addProperty("ownerUuid", team.ownerUuid.toString())
                        })
                    }
                })
            }
            StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(root.toString())).let { buffer ->
                    ByteArray(buffer.remaining()).also { buffer.get(it) }
                }
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid teams snapshot for $path: ${e.message}", e)
        }

        // Create the parent only after successful validation/serialization.
        val parent = path.toAbsolutePath().parent
        var temp: Path? = null
        try {
            Files.createDirectories(parent)
            val tempFile = Files.createTempFile(parent, ".${path.fileName}.", ".tmp")
            temp = tempFile
            Files.write(tempFile, bytes)
            Files.move(tempFile, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            throw IOException("Cannot atomically save teams to $path", e)
        } finally {
            // A failed cleanup must not mask a failed replacement; leftovers are ignored by load.
            temp?.let { runCatching { Files.deleteIfExists(it) } }
        }
    }

    private fun decode(root: JsonElement): TeamsSnapshot {
        val obj = requiredObject(root, "root")
        val version = field(obj, "version", "root")
        require(version.isJsonPrimitive && version.asJsonPrimitive.isNumber && version.asString == "1") {
            "root.version must be integer 1"
        }
        val rows = requiredArray(field(obj, "teams", "root"), "root.teams")
        val teams = rows.mapIndexed { index, element ->
            val where = "teams[$index]"
            val row = requiredObject(element, where)
            val nameTree = field(row, "displayName", where)
            require(nameTree.isJsonObject || nameTree.isJsonArray ||
                (nameTree.isJsonPrimitive && nameTree.asJsonPrimitive.isString)) {
                "$where.displayName must be an Adventure Component JSON object, array or string"
            }
            require(!nameTree.isJsonArray || nameTree.asJsonArray.size() > 0) {
                "$where.displayName must not be an empty Component array"
            }
            val name: Component = try {
                components.deserializeFromTree(nameTree)
            } catch (e: RuntimeException) {
                throw IllegalArgumentException("$where.displayName: invalid Adventure Component", e)
            }
            val members = requiredArray(field(row, "members", where), "$where.members")
                .mapIndexed { memberIndex, value ->
                    val location = "$where.members[$memberIndex]"
                    uuid(requiredString(value, location), location)
                }
            val ownerValue = field(row, "ownerUuid", where)
            val owner = if (ownerValue.isJsonNull) null
                else uuid(requiredString(ownerValue, "$where.ownerUuid"), "$where.ownerUuid")
            TeamSnapshot(
                id = requiredString(field(row, "id", where), "$where.id"),
                displayName = name,
                acceptAll = requiredBoolean(field(row, "acceptAll", where), "$where.acceptAll"),
                members = members,
                ownerUuid = owner
            )
        }
        return TeamsSnapshot(teams = teams).also(::validate)
    }

    private fun validate(snapshot: TeamsSnapshot) {
        require(snapshot.version == 1) { "version must be 1" }
        val ids = HashSet<String>()
        val allMembers = HashSet<UUID>()
        snapshot.teams.forEachIndexed { index, team ->
            val where = "teams[$index] (${team.id})"
            require(team.id.isNotBlank()) { "$where.id must not be blank" }
            require(ids.add(team.id)) { "$where.id is duplicated" }
            team.members.forEach { member ->
                require(allMembers.add(member)) { "$where.members repeats UUID $member" }
            }
            if (team.members.isEmpty()) {
                require(team.ownerUuid == null) { "$where.ownerUuid must be null for an empty team" }
            } else {
                require(team.ownerUuid != null && team.ownerUuid in team.members) {
                    "$where.ownerUuid must identify a member of this nonempty team"
                }
            }
        }
    }

    private fun field(obj: JsonObject, name: String, where: String): JsonElement =
        obj.get(name) ?: throw IllegalArgumentException("$where.$name is required")

    private fun requiredObject(value: JsonElement, where: String): JsonObject {
        require(value.isJsonObject) { "$where must be a JSON object" }
        return value.asJsonObject
    }

    private fun requiredArray(value: JsonElement, where: String): JsonArray {
        require(value.isJsonArray) { "$where must be a JSON array" }
        return value.asJsonArray
    }

    private fun requiredString(value: JsonElement, where: String): String {
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "$where must be a string" }
        return value.asString
    }

    private fun requiredBoolean(value: JsonElement, where: String): Boolean {
        require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) { "$where must be a boolean" }
        return value.asBoolean
    }

    private fun uuid(value: String, where: String): UUID {
        require(uuidPattern.matches(value)) { "$where is not a standard UUID: $value" }
        return UUID.fromString(value)
    }
}
