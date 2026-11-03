package dev.necr0manthre.innotournament.generators

import org.bukkit.Material
import org.bukkit.inventory.ItemFactory
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path

class GeneratorProfileReaderTest {
    @Test
    fun `loads all types with comments, arbitrary levels, and old SNBT counts`(@TempDir dataFolder: Path) {
        val item = write(dataFolder, "itemGen.json", """[
            // A comment from the original file format.
            {"generationTime":0,"maxItemCount":1,"probabilities":{"diamond":1}}
        ]""")
        write(dataFolder, "bookGen.json", """[
            {"generationTime":2,"maxItemCount":10,"probabilities":{"{count:3,id:\"diamond\"}":0.5}},
            {"generationTime":5,"maxItemCount":2,"probabilities":{"diamond":4}}
        ]""")
        write(dataFolder, "netherGen.json", """[
            {"generationTime":9,"maxItemCount":3,"probabilities":{"diamond":1}}
        ]""")

        val profiles = reader().load(dataFolder.toFile())
        assertEquals(item.toString(), profiles[GeneratorType.ITEM_GENERATOR].sourcePath)
        assertEquals(0, profiles[GeneratorType.ITEM_GENERATOR].levels[0].generationTime)
        assertEquals(2, profiles[GeneratorType.BOOK_GENERATOR].levels.size)
        assertEquals(3, profiles[GeneratorType.BOOK_GENERATOR].levels[0].probabilities[0].prototype.amount)
        assertEquals(0.5, profiles[GeneratorType.BOOK_GENERATOR].levels[0].probabilities[0].weight)
        assertEquals(3, profiles[GeneratorType.NETHER_GENERATOR].levels[0].maxItemCount)
    }

    @Test
    fun `missing file fails without creating defaults`(@TempDir dataFolder: Path) {
        write(dataFolder, "itemGen.json", valid())
        val missing = dataFolder.resolve("generators/bookGen.json")
        val error = assertThrows(IllegalArgumentException::class.java) { reader().load(dataFolder.toFile()) }
        assertTrue(error.message!!.contains(missing.toString()))
        assertFalse(Files.exists(missing))
    }

    @Test
    fun `bad probability preserves file level key and resolver cause`(@TempDir dataFolder: Path) {
        write(dataFolder, "itemGen.json", valid())
        val bad = write(dataFolder, "bookGen.json", """[
            {"generationTime":1,"maxItemCount":1,"probabilities":{"diamond":1}},
            {"generationTime":1,"maxItemCount":1,"probabilities":{"minecraft:chainmail_leggins":1}}
        ]""")
        write(dataFolder, "netherGen.json", valid())
        val original = Files.readString(bad)

        val error = assertThrows(IllegalArgumentException::class.java) { reader().load(dataFolder.toFile()) }
        assertTrue(error.message!!.contains("bookGen.json, level 1"), error.message)
        assertTrue(error.message!!.contains("minecraft:chainmail_leggins"), error.message)
        assertTrue(error.cause!!.message!!.contains("Generator item"), error.message)
        assertEquals(original, Files.readString(bad))
    }

    @Test
    fun `rejects numeric strings and duplicate keys`(@TempDir dataFolder: Path) {
        val bad = write(dataFolder, "itemGen.json", """[
            {"generationTime":"1","maxItemCount":1,"probabilities":{"diamond":1}}
        ]""")
        write(dataFolder, "bookGen.json", valid())
        write(dataFolder, "netherGen.json", valid())
        val reader = reader()
        val error = assertThrows(IllegalArgumentException::class.java) { reader.load(dataFolder.toFile()) }
        assertTrue(error.message!!.contains("itemGen.json, level 0"), error.message)
        assertTrue(error.message!!.contains("generationTime"), error.message)

        Files.writeString(bad, """[{"generationTime":1,"maxItemCount":1,"probabilities":{"diamond":1,"diamond":2}}]""")
        val duplicate = assertThrows(IllegalArgumentException::class.java) { reader.load(dataFolder.toFile()) }
        assertTrue(duplicate.message!!.contains("item 'diamond': duplicate probability key"), duplicate.message)
    }

    @Test
    fun `ignores extra level values without changing known fields`(@TempDir dataFolder: Path) {
        val file = write(dataFolder, "itemGen.json", """[
            {
                "note":"пометка",
                "metadata":{"nested":[true, null, {"values":[1, {"deep":false}]}]},
                "generationTime":7,
                "maxItemCount":4,
                "probabilities":{"diamond":2.5},
                "note":["another value", {"ignored":42}]
            }
        ]""")
        write(dataFolder, "bookGen.json", valid())
        write(dataFolder, "netherGen.json", valid())

        val level = reader().load(dataFolder.toFile())[GeneratorType.ITEM_GENERATOR].levels.single()
        assertEquals(7, level.generationTime)
        assertEquals(4, level.maxItemCount)
        assertEquals("diamond", level.probabilities.single().sourceKey)
        assertEquals(2.5, level.probabilities.single().weight)

        Files.writeString(file, """[{
            "generationTime":7,"maxItemCount":4,"probabilities":{"diamond":2.5},"generationTime":8
        }]""")
        val duplicate = assertThrows(IllegalArgumentException::class.java) { reader().load(dataFolder.toFile()) }
        assertTrue(duplicate.message!!.contains("duplicate field 'generationTime'"), duplicate.message)
    }

    private fun valid() = """[{"generationTime":1,"maxItemCount":1,"probabilities":{"diamond":1}}]"""

    private fun write(dataFolder: Path, filename: String, contents: String): Path {
        val directory = Files.createDirectories(dataFolder.resolve("generators"))
        return Files.writeString(directory.resolve(filename), contents)
    }

    private fun reader(): GeneratorProfileReader = GeneratorProfileReader(
        GeneratorItemResolver(Proxy.newProxyInstance(
            ItemFactory::class.java.classLoader, arrayOf(ItemFactory::class.java)
        ) { _, method, args ->
            if (method.name != "createItemStack") error("Unexpected ItemFactory method: ${method.name}")
            if (args!![0] != "minecraft:diamond") throw IllegalArgumentException("Unknown item: ${args[0]}")
            TestStack()
        } as ItemFactory)
    )

    /** Minimal detached ItemStack used by the already-established resolver test approach. */
    private class TestStack(private var count: Int = 1) : ItemStack() {
        override fun getType(): Material = Material.DIAMOND
        override fun getAmount(): Int = count
        override fun setAmount(amount: Int) { count = amount }
        override fun getMaxStackSize(): Int = 64
        override fun clone(): ItemStack = TestStack(count)
    }
}
