package dev.necr0manthre.innotournament.tournament.events

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import dev.necr0manthre.innotournament.tournament.events.parsing.TournamentEventHandlerParser
import dev.necr0manthre.innotournament.tournament.events.parsing.TournamentEventParser
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object EventLoader {
    fun loadEvents(json: JsonElement): Map<TournamentEvent<*>, List<ITournamentEventListener<*>>> {
        val map = mutableMapOf<TournamentEvent<*>, List<ITournamentEventListener<*>>>()
        for (line in json.getAsJsonObject().entrySet()) {
            val event = TournamentEventParser.parse(line.key) ?: error("")
            if (line.value.isJsonPrimitive) map[event] =
                listOf(TournamentEventHandlerParser.parse(line.value.asString) ?: error(""))
            else {
                map[event] = buildList {
                    line.value.getAsJsonArray()
                        .forEach { add(TournamentEventHandlerParser.parse(it.asString) ?: error("")) }
                }
            }
        }
        return map
    }

    fun loadEvents(path: Path): Map<TournamentEvent<*>, List<ITournamentEventListener<*>>> {
        return try {
            loadEvents(JsonParser.parseReader(Files.newBufferedReader(path)))
        } catch (e: IOException) {
            mapOf()
        }
    }
}
