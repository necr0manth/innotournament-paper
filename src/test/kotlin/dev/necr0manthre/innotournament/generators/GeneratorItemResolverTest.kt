package dev.necr0manthre.innotournament.generators

import net.kyori.adventure.nbt.TagStringIO
import org.bukkit.Material
import org.bukkit.inventory.ItemFactory
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

/** Codec boundary checks with a recording ItemFactory; the real Paper parser runs on the host. */
class GeneratorItemResolverTest {
    @Test
    fun `normalizes IDs and applies envelope counts outside the item argument`() {
        val arguments = mutableListOf<String>()
        val resolver = GeneratorItemResolver(factory { argument ->
            arguments += argument
            Stack(Material.DIAMOND)
        })

        assertEquals(1, resolver.resolve("diamond").amount)
        assertEquals(3, resolver.resolve("{id:\"diamond\",count:3,components:{\"minecraft:repair_cost\":1}}").amount)
        assertEquals(listOf("minecraft:diamond", "minecraft:diamond[minecraft:repair_cost=1]"), arguments)
    }

    @Test
    fun `preserves heterogeneous SNBT lore when composing an item argument`() {
        var argument = ""
        val resolver = GeneratorItemResolver(factory { input ->
            argument = input
            Stack(Material.DIAMOND_SWORD)
        })
        resolver.resolve("""{id:"minecraft:diamond_sword",components:{"minecraft:lore":["",{text:"hello",italic:0b}]}}""")
        val prefix = "minecraft:diamond_sword[minecraft:lore="
        assertTrue(argument.startsWith(prefix))
        assertTrue(argument.contains("hello"))
        assertTrue(argument.contains("italic"))
        assertTrue(argument.endsWith("]"))

        val snbt = TagStringIO.builder().acceptHeterogeneousLists(true).build()
        val expectedLore = snbt.asCompound("""{lore:["",{text:"hello",italic:0b}]}""").get("lore")
        val serializedLore = argument.removePrefix(prefix).removeSuffix("]")
        val actualLore = snbt.asCompound("{lore:$serializedLore}").get("lore")
        assertEquals(expectedLore, actualLore)
    }

    @Test
    fun `rejects malformed envelope and component data before querying Paper`() {
        var calls = 0
        val resolver = GeneratorItemResolver(factory {
            calls++
            Stack(Material.DIAMOND)
        })
        val invalid = listOf(
            "", "{}", "{id:1}", "{id:\"diamond\",unused:1}",
            "{id:\"diamond\",count:0}", "{id:\"diamond\",count:1.5d}",
            "{id:\"diamond\",components:[]}",
            "{id:\"diamond\",components:{\"not valid\":1}}",
            "{id:\"diamond\",components:{lore:[],\"minecraft:lore\":[]}}",
            "Minecraft:Diamond", "diamond[something=1]", "innotournament:missing_special"
        )
        invalid.forEach { input ->
            val error = assertThrows(IllegalArgumentException::class.java) { resolver.resolve(input) }
            assertTrue(error.message!!.contains("Generator item"), "Missing context: $input")
        }
        assertEquals(0, calls)
    }

    @Test
    fun `rejects factory exceptions, AIR and excessive count without fallback`() {
        val resolver = GeneratorItemResolver(factory { input ->
            when (input) {
                "minecraft:unknown_material" -> throw IllegalArgumentException("unknown material")
                "minecraft:air" -> Stack(Material.AIR)
                else -> Stack(Material.DIAMOND)
            }
        })
        for (source in listOf("unknown_material", "air", "{id:\"diamond\",count:65}")) {
            val error = assertThrows(IllegalArgumentException::class.java) { resolver.resolve(source) }
            assertTrue(error.message!!.contains(source), "Missing source key: $source")
        }
    }

    /** A detached Bukkit API stack double that does not initialize live Paper registries. */
    private class Stack(private val material: Material, initialAmount: Int = 1) : ItemStack() {
        private var count: Int = initialAmount
        override fun getType(): Material = material
        override fun getAmount(): Int = count
        override fun setAmount(amount: Int) { count = amount }
        override fun getMaxStackSize(): Int = 64
        override fun clone(): ItemStack = Stack(material, count)
    }

    private fun factory(create: (String) -> ItemStack): ItemFactory =
        Proxy.newProxyInstance(ItemFactory::class.java.classLoader, arrayOf(ItemFactory::class.java)) { _, method, args ->
            if (method.name == "createItemStack") create(args!![0] as String)
            else error("Unexpected ItemFactory method ${method.name}")
        } as ItemFactory
}
