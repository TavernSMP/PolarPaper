# PolarPaper for Paper 1.20.1

This is TavernSMP's `1_20_1` backport of PolarPaper. It requires **Paper 1.20.1 and Java 25**. The world-creation adapter follows the upstream 1.21.11 adapter's no-save design and uses the actual 1.20.1 server signatures. This branch targets Paper; Folia and other Minecraft versions are not supported.

Polar stores an arena, lobby, or other small world in a single `.polar` file. It holds the world in memory and saves only when requested or configured. Worlds may be stored in files or another `PolarSource` implementation.

## Build and install

Use a Java 25 JDK and the included Gradle wrapper:

```powershell
.\gradlew.bat clean build --no-daemon
```

Install the single plugin JAR from `dist/`. Gradle remaps it to the Spigot mappings used by Paper 1.20.1. JARs in `build/libs/` include development and source artifacts; use `dist/` for deployment. The separate acceptance harness is for isolated tests only.

On Linux, use `bash gradlew clean build --no-daemon`. Set `GRADLE_USER_HOME` and `java.io.tmpdir` to a suitable build disk if needed. The first build downloads the Paper development bundle; the first server start resolves the ZSTD library through the plugin loader.

Optional WorldEdit integration targets WorldEdit 7.2.15. The plugin loads without WorldEdit.

## Commands

Use `/polar help` for command syntax. The main flow is `/polar createblank <name>`, build in the world, `/polar save <name>`, `/polar unload <name>`, and `/polar load <name>.polar`. `/polar convert` converts the region around a player in an existing vanilla world. Copy, schematic placement, selection wand, spawn settings, gamerules, and relighting remain available.

Permissions use `polarpaper.<subcommand>`, for example `polarpaper.save`, plus `polarpaper.use` for the root command. The selection wand uses a blaze rod, which exists in 1.20.1.

## World compatibility

The Polar v7 container layout remains unchanged. The branch preserves supported blocks and biomes, block entities and inventories, entity/passenger data, item and chunk persistent data, sign text, and stored lighting. It writes Minecraft 1.20.1's data version, 3465. Loading a world with a newer Minecraft data version is rejected; this backport does not downgrade newer blocks, entities, items, or NBT.

Gamerules use the 1.20.1 names, such as `doMobSpawning`, `doFireTick`, `randomTickSpeed`, and `mobGriefing`. Custom physics rules are configured in `config.yml`. The `/polar version` link points to this backport branch.

## Validation

See [integration-tests/README.md](integration-tests/README.md) for the runnable localhost-only Paper 1.20.1 acceptance harness. It exercises the plugin API and ordinary commands, saves and reloads data, then verifies a second server start. Keep world backups when evaluating a new build.

Upstream project: [MinehubMC/PolarPaper](https://github.com/MinehubMC/PolarPaper). Polar originated in [hollow-cube/polar](https://github.com/hollow-cube/polar). See [LICENSE](LICENSE) for the MIT license.
