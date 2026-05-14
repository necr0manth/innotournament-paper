package dev.necr0manthre.innotournament.init

import net.minecraft.resources.ResourceLocation

object InnoBlocks {
    val ITEM_GENERATOR_BLOCK: ItemGeneratorBlock? = StandartItemGenerator.Companion.register()
    val BOOK_GENERATOR_BLOCK: ItemGeneratorBlock? = BookGenerator.Companion.register()
    val NETHER_GENERATOR_BLOCK: ItemGeneratorBlock? = NetherItemGenerator.Companion.register()

    fun initialize() {
    }

    fun <T : net.minecraft.world.level.block.Block?> registerBlock(
        constructor: java.util.function.Function<BlockBehaviour.Properties?, T?>,
        properties: BlockBehaviour.Properties,
        name: kotlin.String?
    ): T? {
        val rl: Unit /* TODO: class org.jetbrains.kotlin.nj2k.types.JKJavaNullPrimitiveType */? =
            ResourceLocation.fromNamespaceAndPath(dev.necr0manthre.innotournament.Innotournament.Companion.MOD_ID, name)
        return net.minecraft.core.Registry.register(
            BuiltInRegistries.BLOCK,
            rl,
            constructor.apply(
                properties.setId(
                    ResourceKey.create<net.minecraft.world.level.block.Block?>(
                        net.minecraft.core.registries.Registries.BLOCK,
                        rl
                    )
                )
            )
        )
    }

    fun <T : net.minecraft.world.level.block.Block?> registerBlock(
        constructor: java.util.function.Function<BlockBehaviour.Properties?, T?>,
        name: kotlin.String?
    ): T? {
        return InnoBlocks.registerBlock<T?>(constructor, BlockBehaviour.Properties.of(), name)
    }
}
