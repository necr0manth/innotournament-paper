package dev.necr0manthre.innotournament.sidebar

import eu.pb4.sidebars.api.Sidebar
import net.minecraft.network.chat.Component
import java.util.*
import java.util.function.Supplier
import kotlin.math.max

class SuppliedSidebar : SidebarInterface {
    private val players: MutableSet<ServerGamePacketListenerImpl?> = HashSet<ServerGamePacketListenerImpl?>()

    @Getter
    private var priority: Sidebar.Priority? = null

    @Getter
    private var updateRate = 1

    @Getter
    private var isActive = false

    @Setter
    @Getter
    private val isDirty = false

    @Getter
    private var supplier: Supplier<SidebarData?>? = null

    fun setSupplier(supplier: Supplier<SidebarData?>?) {
        this.supplier = supplier
        show()
    }

    fun setPriority(priority: Sidebar.Priority?) {
        this.priority = priority
        if (this.isActive) {
            for (player in this.players) {
                SidebarUtils.updatePriorities(player, this)
            }
        }
    }

    fun setUpdateRate(updateRate: Int) {
        this.updateRate = max(updateRate, 1)
    }

    fun show() {
        if (!isActive) {
            this.isActive = true
            for (player in this.players) {
                SidebarUtils.addSidebar(player, this)
            }
        }
    }

    fun hide() {
        if (this.isActive) {
            this.isActive = false
            for (player in this.players) {
                SidebarUtils.removeSidebar(player, this)
            }
        }
    }

    fun addPlayer(handler: ServerGamePacketListenerImpl?) {
        if (this.players.add(handler)) {
            if (isActive) {
                SidebarUtils.addSidebar(handler, this)
            }
        }
    }

    fun removePlayer(handler: ServerGamePacketListenerImpl) {
        if (this.players.remove(handler)) {
            if (isActive) {
                if (!handler.player.hasDisconnected()) {
                    SidebarUtils.removeSidebar(handler, this)
                }
            }
        }
    }

    fun addPlayer(player: ServerPlayer) {
        this.addPlayer(player.connection)
    }

    fun removePlayer(player: ServerPlayer) {
        this.removePlayer(player.connection)
    }

    val playerHandlerSet: MutableSet<ServerGamePacketListenerImpl>
        get() = Collections.unmodifiableSet<ServerGamePacketListenerImpl?>(this.players)

    public override fun disconnected(handler: ServerGamePacketListenerImpl) {
        this.removePlayer(handler)
    }

    public override fun getDataFor(handler: ServerGamePacketListenerImpl?): SidebarData? {
        return if (supplier == null) SidebarData(Component.empty(), mutableListOf<E?>()) else supplier!!.get()
    }
}
