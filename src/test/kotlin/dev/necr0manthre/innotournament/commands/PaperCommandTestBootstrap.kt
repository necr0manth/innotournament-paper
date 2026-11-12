package dev.necr0manthre.innotournament.commands

import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap

internal object PaperCommandTestBootstrap {
    private var initialized = false

    fun ensure() = synchronized(this) {
        if (initialized) return@synchronized
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        initialized = true
    }
}
