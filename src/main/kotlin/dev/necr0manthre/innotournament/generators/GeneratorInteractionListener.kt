package dev.necr0manthre.innotournament.generators

import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory

/** Main-thread delivery adapter. Placement discovery and generator state belong to their owners. */
class GeneratorInteractionListener(
    private val items: GeneratorItems,
    private val lifecycle: GeneratorPlacementLifecycle,
    private val runtime: GeneratorRuntime
) : Listener {
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onInteract(event: PlayerInteractEvent) {
        if (!isPermittedGeneratorClick(event)) return
        val key = lifecycle.find(event.clickedBlock ?: return) ?: return

        // The server must not also use a held item or the physical carrier for this click.
        event.setUseInteractedBlock(Event.Result.DENY)
        event.setUseItemInHand(Event.Result.DENY)

        val player = event.player
        if (player.gameMode == GameMode.SPECTATOR) return

        val hand = player.inventory.itemInMainHand
        if (items.identify(hand) == SpecialItemType.INNO_DICE) {
            check(runtime.reroll(key)) { "Discovered generator is not active: $key" }
            consumeMainHandDice(player.inventory, hand)
        } else {
            val stock = runtime.take(key) ?: error("Discovered generator is not active: $key")
            deliverGeneratorStock(player, stock)
        }
    }
}

/** Respect both independent permission results, including denials from other listeners. */
internal fun isPermittedGeneratorClick(event: PlayerInteractEvent): Boolean =
    event.action == Action.RIGHT_CLICK_BLOCK &&
        event.hand == EquipmentSlot.HAND &&
        event.clickedBlock != null &&
        event.useInteractedBlock() != Event.Result.DENY &&
        event.useItemInHand() != Event.Result.DENY

/** A successful reroll always costs one stamped Dice, including on an empty generator. */
internal fun consumeMainHandDice(inventory: PlayerInventory, dice: ItemStack) {
    check(dice.amount > 0) { "Cannot consume an empty Dice stack" }
    inventory.setItemInMainHand(if (dice.amount == 1) null else dice.clone().apply { amount-- })
}

/** The core has already cleared stock; only the inventory's returned overflow drops at the player. */
internal fun deliverGeneratorStock(player: Player, stock: List<ItemStack>) {
    if (stock.isEmpty()) return
    val overflow = player.inventory.addItem(*stock.map { it.clone() }.toTypedArray())
    if (overflow.isEmpty()) return
    val location = player.location
    overflow.values.forEach { player.world.dropItemNaturally(location, it) }
}
