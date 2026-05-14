package dev.necr0manthre.innotournament.compat.journeymap

import journeymap.api.v2.client.IClientAPI

object JourneyMapProxy {
    var api: IClientAPI? = null

    fun getApi(): java.util.Optional<IClientAPI?> {
        return java.util.Optional.ofNullable<IClientAPI?>(JourneyMapProxy.api)
    }
}
