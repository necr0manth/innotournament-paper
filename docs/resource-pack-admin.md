# Required InnoTournament resource pack (Minecraft Java 26.1.2)

The plugin build also produces a **separate** client pack and its SHA-1:

- `build/libs/innotournament-resource-pack-26.1.2.zip`
- `build/libs/innotournament-resource-pack-26.1.2.zip.sha1`

Run `./gradlew build` (or `./gradlew resourcePackSha1` to package only the pack). The `.sha1` file contains the 40-hex-character SHA-1 of the ZIP **bytes**, not the hash of a directory or the plugin JAR. Do not unzip/re-zip after calculating it.

1. Publish the exact ZIP at a direct, publicly accessible HTTP(S) URL that Minecraft clients can download. The server does not host it automatically.
2. Set these keys in the server's `server.properties`, replacing the example URL and hash with the hosted ZIP's real values:

   ```properties
   resource-pack=https://example.org/minecraft/innotournament-resource-pack-26.1.2.zip
   resource-pack-sha1=<contents of build/libs/innotournament-resource-pack-26.1.2.zip.sha1>
   require-resource-pack=true
   ```

3. Restart the server and test connection using a Minecraft **Java 26.1.2** client. Declining the required pack prevents joining; a vanilla appearance without the pack is not a supported play mode. When the ZIP changes, upload the new ZIP and update `resource-pack-sha1` to its new SHA-1 (and ensure clients can fetch the new bytes at the configured URL).

The pack changes only four vanilla item definitions. Items with a matching `minecraft:custom_model_data` **string at index 0** select the original InnoTournament model; ordinary items keep their normal vanilla fallback:

| Carrier item | `strings[0]` | Selected model |
| --- | --- | --- |
| `minecraft:lodestone` | `innotournament:item_generator` | `innotournament:block/item_generator` |
| `minecraft:chiseled_stone_bricks` | `innotournament:book_generator` | `innotournament:block/book_generator` |
| `minecraft:polished_blackstone_bricks` | `innotournament:nether_generator` | `innotournament:block/nether_generator` |
| `minecraft:amethyst_shard` | `innotournament:inno_dice` | `innotournament:item/inno_dice` |

This visual marker does **not** establish an item's identity; plugin logic must use its PDC markers. If another resource pack overrides any of these four `assets/minecraft/items/*.json` definitions, combine their select cases manually; stacking packs alone may cause one definition to override the other. The archive deliberately contains no block registration, built-in HTTP server, or JourneyMap integration. The plugin provides generator mechanics; copy the three bundled profiles as described in the repository README before first enable.

Sources: [Minecraft 26.1 pack version 84](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-1), [item definition and select string property](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-4), [pack.mcmeta min/max format](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-9), [Paper server.properties](https://docs.papermc.io/paper/reference/server-properties/).
