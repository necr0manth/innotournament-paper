package dev.necr0manthre.innotournament.tournament.events

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import dev.necr0manthre.innotournament.tournament.events.parsing.TournamentEventHandlerParser
import dev.necr0manthre.innotournament.tournament.events.parsing.TournamentEventParser
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Map
import java.util.Set
import java.util.function.Consumer

object EventLoader {
    fun loadEvents(json: JsonElement): MutableMap<AbstractTournamentEvent<*>?, MutableSet<ITournamentEventListener<*>?>?> {
        val map = HashMap<AbstractTournamentEvent<*>?, MutableSet<ITournamentEventListener<*>?>?>()
        for (line in json.getAsJsonObject().entrySet()) {
            val event = TournamentEventParser.parse(line.key)
            if (line.value.isJsonPrimitive()) map.put(
                event,
                Set.of<ITournamentEventListener<*>?>(TournamentEventHandlerParser.parse(line.value.getAsString()))
            )
            else {
                val handlers: MutableSet<ITournamentEventListener<*>?> = HashSet<ITournamentEventListener<*>?>()
                line.value.getAsJsonArray()
                    .forEach(Consumer { e: JsonElement? -> handlers.add(TournamentEventHandlerParser.parse(e!!.getAsString())) })
                map.put(event, handlers)
            }
        }
        return map
    }

    fun loadEvents(path: Path): MutableMap<AbstractTournamentEvent<*>?, MutableSet<ITournamentEventListener<*>?>?> {
        try {
            return loadEvents(JsonParser.parseReader(Files.newBufferedReader(path)))
        } catch (e: IOException) {
            return Map.of<AbstractTournamentEvent<*>?, MutableSet<ITournamentEventListener<*>?>?>()
        }
    }
}
