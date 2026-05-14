package dev.necr0manthre.innotournament.teams.invites

import dev.dominion.ecs.api.Entity

class Invites {
    val invites: MutableSet<Invite?> = HashSet<Invite?>()

    companion object {
        fun getComponent(team: Entity): Invites {
            var component: Unit /* TODO: class org.jetbrains.kotlin.nj2k.types.JKJavaNullPrimitiveType */? =
                team.get(Invites::class.java)
            if (component == null) {
                component = Invites()
                team.add(component)
            }
            return component
        }
    }
}
