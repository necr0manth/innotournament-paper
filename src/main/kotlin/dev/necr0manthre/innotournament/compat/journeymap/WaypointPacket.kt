package dev.necr0manthre.innotournament.compat.journeymap

import commonnetwork.api.Network

class WaypointPacket {
    private val name: kotlin.String
    private val pos: BlockPos
    private val dimension: ResourceKey<net.minecraft.world.level.Level?>

    constructor(name: kotlin.String, pos: BlockPos, dimension: ResourceKey<net.minecraft.world.level.Level?>) {
        this.name = name
        this.pos = pos
        this.dimension = dimension
    }

    constructor(buf: RegistryFriendlyByteBuf) {
        name = buf.readUtf()
        pos = buf.readBlockPos()
        dimension =
            buf.readResourceKey<net.minecraft.world.level.Level?>(net.minecraft.core.registries.Registries.DIMENSION)
    }

    fun encode(buf: FriendlyByteBuf) {
        buf.writeUtf(name)
        buf.writeBlockPos(pos)
        buf.writeResourceKey(dimension)
    }

    companion object {
        val CHANNEL: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            dev.necr0manthre.innotournament.Innotournament.Companion.MOD_ID,
            "example_packet_two"
        )

        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf?, WaypointPacket?> =
            StreamCodec.ofMember<RegistryFriendlyByteBuf?, WaypointPacket?>(StreamMemberEncoder { obj: WaypointPacket?, buf: RegistryFriendlyByteBuf? ->
                obj!!.encode(buf)
            }, net.minecraft.network.codec.StreamDecoder { buf: RegistryFriendlyByteBuf? -> WaypointPacket(buf) })

        fun type(): CustomPacketPayload.Type<CustomPacketPayload?> {
            return CustomPacketPayload.Type<T?>(WaypointPacket.Companion.CHANNEL)
        }

        fun handle(ctx: PacketContext<WaypointPacket?>) {
            JourneyMapProxy.getApi().ifPresent(java.util.function.Consumer { api: IClientAPI? ->
                api.addWaypoint(
                    dev.necr0manthre.innotournament.Innotournament.Companion.MOD_ID,
                    WaypointFactory.createClientWaypoint(
                        dev.necr0manthre.innotournament.Innotournament.Companion.MOD_ID,
                        ctx.message().pos,
                        ctx.message().name,
                        ctx.message().dimension,
                        true
                    )
                )
            })
        }

        fun register() {
            Network.registerPacket(
                WaypointPacket.Companion.type(),
                WaypointPacket::class.java,
                WaypointPacket.Companion.STREAM_CODEC,
                { ctx: PacketContext<WaypointPacket?> -> WaypointPacket.Companion.handle(ctx) })
        }
    }
}
