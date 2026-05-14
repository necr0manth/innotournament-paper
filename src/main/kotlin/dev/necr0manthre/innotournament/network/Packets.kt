package dev.necr0manthre.innotournament.network

import dev.necr0manthre.innotournament.compat.journeymap.WaypointPacket

object Packets {
    fun registerPackets() {
        WaypointPacket.register()
    }
}
