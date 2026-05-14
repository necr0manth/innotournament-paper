package dev.necr0manthre.innotournament.init


import net.minecraft.resources.ResourceLocation

object InnoItems {
    val INNO_DICE: net.minecraft.world.item.Item? =
        InnoItems.registerItem<InnoDice?>(java.util.function.Function { properties: net.minecraft.world.item.Item.Properties? ->
            InnoDice(properties)
        }, "inno_dice")

    fun initialize() {
        InnoItems.setItemCreativeTab(
            java.util.function.Supplier { InnoItems.INNO_DICE },
            CreativeModeTabs.FUNCTIONAL_BLOCKS
        )
    }

    fun <T : net.minecraft.world.item.Item?> registerItem(
        constructor: java.util.function.Function<net.minecraft.world.item.Item.Properties?, T?>,
        properties: net.minecraft.world.item.Item.Properties,
        name: kotlin.String?
    ): T? {
        val rl: Unit /* TODO: class org.jetbrains.kotlin.nj2k.types.JKJavaNullPrimitiveType */? =
            ResourceLocation.fromNamespaceAndPath(dev.necr0manthre.innotournament.Innotournament.Companion.MOD_ID, name)
        return net.minecraft.core.Registry.register(
            BuiltInRegistries.ITEM,
            rl,
            constructor.apply(
                properties.setId(
                    ResourceKey.create<net.minecraft.world.item.Item?>(
                        net.minecraft.core.registries.Registries.ITEM,
                        rl
                    )
                )
            )
        )
    }

    fun <T : net.minecraft.world.item.Item?> registerItem(
        constructor: java.util.function.Function<net.minecraft.world.item.Item.Properties?, T?>,
        name: kotlin.String?
    ): T? {
        return InnoItems.registerItem<T?>(constructor, net.minecraft.world.item.Item.Properties(), name)
    }

    fun <T : net.minecraft.world.level.block.Block?> registerBlockItem(
        block: T?,
        name: kotlin.String?,
        settings: net.minecraft.world.item.Item.Properties
    ): BlockItem {
        return net.minecraft.core.Registry.register(
            BuiltInRegistries.ITEM,
            ResourceLocation.fromNamespaceAndPath(
                dev.necr0manthre.innotournament.Innotournament.Companion.MOD_ID,
                name
            ),
            BlockItem(block, settings)
        )
    }

    fun <T : net.minecraft.world.level.block.Block?> registerBlockItem(block: T?, name: kotlin.String?): BlockItem {
        val rl: Unit /* TODO: class org.jetbrains.kotlin.nj2k.types.JKJavaNullPrimitiveType */? =
            ResourceLocation.fromNamespaceAndPath(dev.necr0manthre.innotournament.Innotournament.Companion.MOD_ID, name)
        return net.minecraft.core.Registry.register(
            BuiltInRegistries.ITEM,
            rl,
            BlockItem(
                block,
                net.minecraft.world.item.Item.Properties().setId(
                    ResourceKey.create<net.minecraft.world.item.Item?>(
                        net.minecraft.core.registries.Registries.ITEM,
                        rl
                    )
                )
            )
        )
    }

    fun setItemCreativeTab(
        supplier: java.util.function.Supplier<net.minecraft.world.item.Item?>?,
        tab: ResourceKey<CreativeModeTab?>?
    ) {
//        ItemGroupEvents.modifyEntriesEvent(tab).register((itemGroup) -> itemGroup.accept(supplier.get()));
    }

    val ITEM_GENERATOR_BLOCK_ITEM: BlockItem? = StandartItemGenerator.Companion.registerBlockItem()
    val BOOK_GENERATOR_BLOCK_ITEM: BlockItem? = BookGenerator.Companion.registerBlockItem()
    val NETHER_GENERATOR_BLOCK_ITEM: BlockItem? = NetherItemGenerator.Companion.registerBlockItem()
}
