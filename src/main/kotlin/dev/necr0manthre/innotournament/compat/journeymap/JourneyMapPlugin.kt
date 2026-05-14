package dev.necr0manthre.innotournament.compat.journeymap

import dev.necr0manthre.innotournament.Innotournament
import journeymap.api.v2.client.IClientAPI

@JourneyMapPlugin(apiVersion = "")
class JourneyMapPlugin : IClientPlugin {
    public override fun initialize(iClientAPI: IClientAPI) {
        JourneyMapProxy.api = iClientAPI
    }

    val modId: String
        get() = Innotournament.Companion.MOD_ID
}
