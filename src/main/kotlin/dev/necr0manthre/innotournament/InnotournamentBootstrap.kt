package dev.necr0manthre.innotournament

import dev.necr0manthre.innotournament.commands.TeamsCommand
import dev.necr0manthre.innotournament.commands.TournamentCommand
import dev.necr0manthre.innotournament.commands.WorldBorderCommand
import dev.necr0manthre.innotournament.teams.PluginTeam.TeamManager
import dev.necr0manthre.innotournament.teams.invites.InviteSystem
import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents

@Suppress("UnstableApiUsage")
internal class InnotournamentBootstrap : PluginBootstrap {
    private val teamManager = TeamManager()
    private val invites = InviteSystem(teamManager)

    override fun bootstrap(context: BootstrapContext) {
        context.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS){
            registrarEvent ->
            val registrar = registrarEvent.registrar()
            TeamsCommand(teamManager, invites).register(registrar)
            TournamentCommand(teamManager).register(registrar)
            WorldBorderCommand.register(registrar)
        }
    }
}
