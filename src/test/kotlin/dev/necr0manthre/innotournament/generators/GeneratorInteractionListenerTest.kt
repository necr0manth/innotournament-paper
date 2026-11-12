package dev.necr0manthre.innotournament.generators

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

/** Focused Paper-interface checks; server event delivery is tested separately on the host. */
class GeneratorInteractionListenerTest {
    @Test
    fun `only permitted main-hand block clicks are eligible`() {
        val player = proxy(Player::class.java) { _, _ -> error("Player must not be queried") }
        val block = proxy(Block::class.java) { _, _ -> error("Block must not be queried") }
        fun click(action: Action = Action.RIGHT_CLICK_BLOCK, hand: EquipmentSlot = EquipmentSlot.HAND,
                  clicked: Block? = block) =
            PlayerInteractEvent(player, action, null, clicked, BlockFace.UP, hand)

        assertTrue(isPermittedGeneratorClick(click()))
        assertFalse(isPermittedGeneratorClick(click(hand = EquipmentSlot.OFF_HAND)))
        assertFalse(isPermittedGeneratorClick(click(action = Action.LEFT_CLICK_BLOCK)))
        assertFalse(isPermittedGeneratorClick(click(clicked = null)))
        val blockDenied = click().apply { setUseInteractedBlock(Event.Result.DENY) }
        assertFalse(isPermittedGeneratorClick(blockDenied))
        val itemDenied = click().apply { setUseItemInHand(Event.Result.DENY) }
        assertFalse(isPermittedGeneratorClick(itemDenied))
    }

    @Test
    fun `dice subtracts one from main hand with no offhand or original stack mutation`() {
        var mainHand: ItemStack? = null
        var changes = 0
        val inventory = proxy(PlayerInventory::class.java) { name, args ->
            if (name == "setItemInMainHand") {
                mainHand = args!![0] as ItemStack?
                changes++
                null
            } else error("Unexpected inventory method $name")
        }
        val single = Stack(Material.AMETHYST_SHARD, 1)
        consumeMainHandDice(inventory, single)
        assertNull(mainHand)
        val multiple = Stack(Material.AMETHYST_SHARD, 3)
        consumeMainHandDice(inventory, multiple)
        assertEquals(2, mainHand!!.amount)
        assertEquals(3, multiple.amount)
        assertNotSame(multiple, mainHand)
        assertEquals(2, changes)
        assertThrows(IllegalStateException::class.java) { consumeMainHandDice(inventory, Stack(Material.AMETHYST_SHARD, 0)) }
        assertEquals(2, changes)
    }

    @Test
    fun `empty stock does nothing and inventory overflow is dropped only at player`() {
        var added = 0
        var supplied: Array<*>? = null
        val leftover = Stack(Material.GOLD_INGOT, 5)
        val inventory = proxy(PlayerInventory::class.java) { name, args ->
            if (name == "addItem") {
                added++
                supplied = args!![0] as Array<*>
                hashMapOf(0 to leftover)
            } else error("Unexpected inventory method $name")
        }
        val drops = ArrayList<Pair<Location, ItemStack>>()
        val world = proxy(World::class.java) { name, args ->
            if (name == "dropItemNaturally") {
                drops.add((args!![0] as Location) to (args[1] as ItemStack))
                null // Return value is unused by the listener.
            } else error("Unexpected world method $name")
        }
        val position = Location(world, 5.5, 70.0, -2.5)
        val player = proxy(Player::class.java) { name, _ ->
            when (name) {
                "getInventory" -> inventory
                "getLocation" -> position
                "getWorld" -> world
                else -> error("Unexpected player method $name")
            }
        }

        deliverGeneratorStock(player, emptyList())
        assertEquals(0, added)
        val original = Stack(Material.DIAMOND, 8)
        deliverGeneratorStock(player, listOf(original))
        assertEquals(1, added)
        assertEquals(1, supplied!!.size)
        assertNotSame(original, supplied!![0], "inventory must receive a detached copy")
        assertEquals(8, original.amount)
        assertEquals(1, drops.size)
        assertSame(position, drops[0].first)
        assertSame(leftover, drops[0].second)
    }

    private class Stack(private val material: Material, initialAmount: Int) : ItemStack() {
        private var count: Int = initialAmount
        override fun getType(): Material = material
        override fun getAmount(): Int = count
        override fun setAmount(amount: Int) { count = amount }
        override fun clone(): ItemStack = Stack(material, count)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> proxy(type: Class<T>, handler: (String, Array<out Any?>?) -> Any?): T =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
            handler(method.name, args)
        } as T
}
