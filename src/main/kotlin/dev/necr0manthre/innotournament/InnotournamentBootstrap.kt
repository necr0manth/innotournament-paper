package dev.necr0manthre.innotournament

import dev.necr0manthre.innotournament.commands.TeamsCommand
import dev.necr0manthre.innotournament.commands.TournamentCommand
import dev.necr0manthre.innotournament.commands.WorldBorderCommand
import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents

internal class InnotournamentBootstrap : PluginBootstrap {

    override fun bootstrap(context: BootstrapContext) {
        context.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS){
            registrarEvent ->
            val registrar = registrarEvent.registrar()
            TeamsCommand.register(registrar, context)
            TournamentCommand.register(registrar, context)
            WorldBorderCommand.register(registrar, context)
        }
    }
}
