package dev.necr0manthre.innotournament.sidebar

import dev.architectury.event.events.common.PlayerEvent

class SidebarManager(
    server: MinecraftServer,
    sidebarFactory: java.util.function.Function<ServerPlayer?, SuppliedSidebarData>
) {
    @Getter
    private val server: MinecraftServer
    private val sidebars: kotlin.collections.MutableMap<java.util.UUID?, SuppliedSidebar> =
        HashMap<java.util.UUID?, SuppliedSidebar>()

    @Setter
    private var sidebarFactory: java.util.function.Function<ServerPlayer?, SuppliedSidebarData>
    var onPlayerJoin: PlayerEvent.PlayerJoin =
        PlayerEvent.PlayerJoin { player: ServerPlayer -> this.onPlayerJoin(player) }

    init {
        this.server = server
        this.sidebarFactory = sidebarFactory
        PlayerEvent.PLAYER_JOIN.register(onPlayerJoin)
        for (player in server.getPlayerList().getPlayers()) onPlayerJoin(player)
    }

    fun updateSidebarFactory(sidebarFactory: java.util.function.Function<ServerPlayer?, SuppliedSidebarData>) {
        this.sidebarFactory = sidebarFactory
        for (sidebar in sidebars.values) {
            for (player in sidebar.getPlayerHandlerSet()) sidebar.removePlayer(player)
        }
        sidebars.replaceAll { p: java.util.UUID?, v: SuppliedSidebar? -> createSidebar(p!!) }
    }

    fun setPlayerSidebarSupplier(
        uuid: java.util.UUID?,
        supplier: java.util.function.Supplier<SidebarInterface.SidebarData?>?
    ) {
        getSidebarFor(uuid).setSupplier(supplier)
    }

    fun setPlayerSidebarSupplier(
        player: ServerPlayer,
        supplier: java.util.function.Supplier<SidebarInterface.SidebarData?>?
    ) {
        setPlayerSidebarSupplier(player.getUUID(), supplier)
    }

    fun updateSidebar(uuid: java.util.UUID?) {
        getSidebarFor(uuid).setDirty(true)
    }

    fun updateSidebar(player: ServerPlayer) {
        updateSidebar(player.getUUID())
    }

    fun setSideBarPriority(uuid: java.util.UUID?, priority: Sidebar.Priority?) {
        getSidebarFor(uuid).setPriority(priority)
    }

    fun setSideBarPriority(player: ServerPlayer, priority: Sidebar.Priority?) {
        setSideBarPriority(player.getUUID(), priority)
    }


    fun setSidebarUpdateRate(uuid: java.util.UUID?, rate: kotlin.Int) {
        getSidebarFor(uuid).setUpdateRate(rate)
    }

    fun setSidebarUpdateRate(player: ServerPlayer, rate: kotlin.Int) {
        setSidebarUpdateRate(player.getUUID(), rate)
    }

    fun getSidebarFor(uuid: java.util.UUID?): SuppliedSidebar {
        return sidebars.computeIfAbsent(uuid) { uuid: java.util.UUID? -> this.createSidebar(uuid!!) }
    }

    fun getSidebarFor(player: ServerPlayer): SuppliedSidebar {
        return getSidebarFor(player.getUUID())
    }

    private fun createSidebar(uuid: java.util.UUID): SuppliedSidebar {
        val sidebar = SuppliedSidebar()
        val player: ServerPlayer? = kotlin.checkNotNull(server.getPlayerList().getPlayer(uuid))
        sidebar.addPlayer(player)
        val data = sidebarFactory.apply(player)
        sidebar.setUpdateRate(data.updateRate)
        sidebar.setPriority(data.priority)
        sidebar.setSupplier(data.supplier)
        return sidebar
    }

    private fun onPlayerJoin(player: ServerPlayer) {
        getSidebarFor(player).addPlayer(player)
        getSidebarFor(player).show()
    }

    fun remove() {
        for (sidebar in sidebars.values) {
            for (player in sidebar.getPlayerHandlerSet()) sidebar.removePlayer(player)
        }
        PlayerEvent.PLAYER_JOIN.unregister(onPlayerJoin)
    }

    class SuppliedSidebarData(
        priority: Sidebar.Priority?, updateRate: kotlin.Int,
        supplier: java.util.function.Supplier<SidebarInterface.SidebarData?>?
    ) {
        val priority: Sidebar.Priority?
        val updateRate: kotlin.Int
        val supplier: java.util.function.Supplier<SidebarInterface.SidebarData?>?

        init {
            this.priority = priority
            this.updateRate = updateRate
            this.supplier = supplier
        }
    }
}
