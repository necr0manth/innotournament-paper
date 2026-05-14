package dev.necr0manthre.innotournament.items

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.TooltipDisplay
import java.util.function.Consumer


class InnoDice(properties: Properties) : Item(properties) {
    override fun appendHoverText(
        itemStack: ItemStack,
        tooltipContext: TooltipContext,
        tooltipDisplay: TooltipDisplay,
        consumer: Consumer<Component?>,
        tooltipFlag: TooltipFlag
    ) {
        consumer.accept(
            Component.literal("Right click on generator with this dice to regenerate items")
                .withStyle(ChatFormatting.GOLD)
        )
    }
}
