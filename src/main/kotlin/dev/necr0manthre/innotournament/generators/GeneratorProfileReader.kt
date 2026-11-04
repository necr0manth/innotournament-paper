package dev.necr0manthre.innotournament.generators

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Reads the three legacy JSON definitions without creating or modifying any files. */
class GeneratorProfileReader(private val itemResolver: GeneratorItemResolver = GeneratorItemResolver()) {
    /** Call before activating generators, on the server thread. Pass the plugin's `dataFolder`. */
    fun load(dataFolder: File): GeneratorProfiles {
        val directory = dataFolder.toPath().resolve("generators")
        return GeneratorProfiles(GeneratorType.entries.map { type ->
            read(type, directory.resolve(type.profileFile))
        })
    }

    private fun read(type: GeneratorType, path: Path): GeneratorProfile {
        val source = path.toString()
        try {
            JsonReader(Files.newBufferedReader(path, StandardCharsets.UTF_8)).use { json ->
                // The original JSON files contain // comments; never silently repair their data.
                json.isLenient = true
                require(json.peek() == JsonToken.BEGIN_ARRAY) { "$source: expected an array of levels" }
                json.beginArray()
                val levels = ArrayList<GeneratorLevel>()
                while (json.hasNext()) levels.add(readLevel(json, source, levels.size))
                json.endArray()
                require(json.peek() == JsonToken.END_DOCUMENT) { "$source: trailing JSON content" }
                return GeneratorProfile(type, source, levels)
            }
        } catch (cause: IOException) {
            throw IllegalArgumentException("$source: cannot read or parse profile: ${cause.message}", cause)
        }
    }

    private fun readLevel(json: JsonReader, source: String, index: Int): GeneratorLevel {
        val where = "$source, level $index"
        try {
            require(json.peek() == JsonToken.BEGIN_OBJECT) { "$where: expected an object" }
            json.beginObject()
            val fields = HashSet<String>()
            var generationTime: Int? = null
            var maxItemCount: Int? = null
            var probabilities: List<GeneratorWeightedItem>? = null
            while (json.hasNext()) {
                val field = json.nextName()
                when (field) {
                    "generationTime" -> {
                        require(fields.add(field)) { "$where: duplicate field '$field'" }
                        generationTime = integer(json, where, field)
                    }
                    "maxItemCount" -> {
                        require(fields.add(field)) { "$where: duplicate field '$field'" }
                        maxItemCount = integer(json, where, field)
                    }
                    "probabilities" -> {
                        require(fields.add(field)) { "$where: duplicate field '$field'" }
                        probabilities = readProbabilities(json, where)
                    }
                    else -> json.skipValue()
                }
            }
            json.endObject()
            require(generationTime != null) { "$where: missing generationTime" }
            require(maxItemCount != null) { "$where: missing maxItemCount" }
            require(probabilities != null) { "$where: missing probabilities" }
            return GeneratorLevel(generationTime, maxItemCount, probabilities)
        } catch (cause: IOException) {
            throw IllegalArgumentException("$where: malformed JSON: ${cause.message}", cause)
        }
    }

    private fun readProbabilities(json: JsonReader, where: String): List<GeneratorWeightedItem> {
        require(json.peek() == JsonToken.BEGIN_OBJECT) { "$where: probabilities must be an object" }
        json.beginObject()
        val entries = ArrayList<GeneratorWeightedItem>()
        val keys = HashSet<String>()
        while (json.hasNext()) {
            val key = json.nextName()
            val at = "$where, item '$key'"
            require(keys.add(key)) { "$at: duplicate probability key" }
            val weight = number(json, at)
            val entry = try {
                GeneratorWeightedItem(key, itemResolver.resolve(key), weight)
            } catch (cause: RuntimeException) {
                throw IllegalArgumentException("$at: ${cause.message}", cause)
            }
            entries.add(entry)
        }
        json.endObject()
        require(entries.isNotEmpty()) { "$where: probabilities must not be empty" }
        return entries
    }

    private fun integer(json: JsonReader, where: String, field: String): Int {
        require(json.peek() == JsonToken.NUMBER) { "$where: $field must be an integer" }
        val raw = json.nextString()
        require(INTEGER.matches(raw)) { "$where: $field must be an integer" }
        return raw.toIntOrNull()
            ?: throw IllegalArgumentException("$where: $field is outside the integer range")
    }

    private fun number(json: JsonReader, where: String): Double {
        try {
            require(json.peek() == JsonToken.NUMBER) { "$where: weight must be a number" }
            val weight = json.nextString().toDoubleOrNull()
            require(weight != null && weight.isFinite() && weight >= 0.0) {
                "$where: weight must be finite and non-negative"
            }
            return weight
        } catch (cause: IOException) {
            throw IllegalArgumentException("$where: malformed weight: ${cause.message}", cause)
        }
    }

    private companion object {
        val INTEGER = Regex("-?(0|[1-9][0-9]*)")
    }
}
