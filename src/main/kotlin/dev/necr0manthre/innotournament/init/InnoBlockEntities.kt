package dev.necr0manthre.innotournament.init

import dev.architectury.injectables.annotations.ExpectPlatform

object InnoBlockEntities {
    fun initialize() {
    }

    fun <T : BlockEntity?> register(
        constructor: BlockEntitySupplier<T?>?,
        block: net.minecraft.world.level.block.Block?,
        name: kotlin.String?
    ): BlockEntityType<T?> {
        return net.minecraft.core.Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(
                dev.necr0manthre.innotournament.Innotournament.Companion.MOD_ID,
                name
            ), < B > createBlockEntityType < B ? > (constructor, block))
    }

    @ExpectPlatform
    fun <B : BlockEntity?> createBlockEntityType(
        constructor: BlockEntitySupplier<B?>?,
        vararg validBlocks: net.minecraft.world.level.block.Block?
    ): BlockEntityType<B?>? {
        throw java.lang.UnsupportedOperationException()
    }

    var ITEM_GENERATOR_BLOCK_ENTITY_TYPE: BlockEntityType<ItemGeneratorBlockEntity?>? =
        ItemGeneratorBlockEntity.Companion.register()
}
