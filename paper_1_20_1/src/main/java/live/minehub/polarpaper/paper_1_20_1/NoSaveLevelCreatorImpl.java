package live.minehub.polarpaper.paper_1_20_1;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Lifecycle;
import live.minehub.polarpaper.core.NoSaveLevelCreator;
import live.minehub.polarpaper.core.util.TaskFutures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.ProgressListener;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.validation.ContentValidationException;
import org.bukkit.*;
import org.bukkit.craftbukkit.v1_20_R1.CraftServer;
import org.bukkit.craftbukkit.v1_20_R1.generator.CraftWorldInfo;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.server.level.progress.ChunkProgressListener;

public class NoSaveLevelCreatorImpl implements NoSaveLevelCreator {
    @Override
    public CompletableFuture<@Nullable World> createLevel(Plugin plugin, WorldCreator creator, Location spawnPos, Difficulty difficulty, Map<String, Object> gamerules, long time) {
        return TaskFutures.runTickThread(plugin, () -> createWorld(plugin, creator, spawnPos, difficulty, gamerules, time));
    }

    private World createWorld(Plugin plugin, WorldCreator creator, Location spawnPos, Difficulty difficulty, Map<String, Object> gamerules, long time) {
        CraftServer craftServer = (CraftServer) Bukkit.getServer();

        Preconditions.checkState(craftServer.getServer().getAllLevels().iterator().hasNext(), "Cannot create additional worlds on STARTUP");
        Preconditions.checkArgument(creator != null, "WorldCreator cannot be null");

        String name = creator.name();
        ChunkGenerator generator = creator.generator();
        BiomeProvider biomeProvider = creator.biomeProvider();
        File folder = new File(craftServer.getWorldContainer(), name);
        World world = craftServer.getWorld(name);

        World worldByKey = craftServer.getWorld(creator.key());
        if (world != null || worldByKey != null) {
            if (world == worldByKey) {
                return world;
            }
            throw new IllegalArgumentException("Cannot create a world with key " + creator.key() + " and name " + name + " one (or both) already match a world that exists");
        }

        if (folder.exists()) {
            Preconditions.checkArgument(folder.isDirectory(), "File (%s) exists and isn't a folder", name);
        }

        if (generator == null) {
            generator = craftServer.getGenerator(name);
        }

        if (biomeProvider == null) {
            biomeProvider = craftServer.getBiomeProvider(name);
        }

        ResourceKey<LevelStem> actualDimension;
        switch (creator.environment()) {
            case NORMAL:
                actualDimension = LevelStem.OVERWORLD;
                break;
            case NETHER:
                actualDimension = LevelStem.NETHER;
                break;
            case THE_END:
                actualDimension = LevelStem.END;
                break;
            default:
                throw new IllegalArgumentException("Illegal dimension (" + creator.environment() + ")");
        }

        LevelStorageSource.LevelStorageAccess worldSession;
        try {
            worldSession = LevelStorageSource.createDefault(plugin.getDataFolder().toPath().resolve("temp")).validateAndCreateAccess(name, actualDimension);
        } catch (IOException | ContentValidationException ex) {
            throw new RuntimeException(ex);
        }

        boolean hardcore = creator.hardcore();

        PrimaryLevelData worlddata;
        WorldLoader.DataLoadContext worldloader_a = craftServer.getServer().worldLoader;
        net.minecraft.core.Registry<LevelStem> iregistry = worldloader_a.datapackDimensions().registryOrThrow(Registries.LEVEL_STEM);
        DynamicOps<net.minecraft.nbt.Tag> dynamicops = RegistryOps.create(NbtOps.INSTANCE, (HolderLookup.Provider) worldloader_a.datapackWorldgen());
        Pair<WorldData, WorldDimensions.Complete> pair = worldSession.getDataTag(dynamicops, worldloader_a.dataConfiguration(), iregistry, worldloader_a.datapackWorldgen().allRegistriesLifecycle());

        if (pair != null) {
            worlddata = (PrimaryLevelData) pair.getFirst();
            iregistry = pair.getSecond().dimensions();
        } else {
            LevelSettings worldsettings;
            WorldOptions worldoptions = new WorldOptions(creator.seed(), creator.generateStructures(), false);
            WorldDimensions worlddimensions;

            DedicatedServerProperties.WorldDimensionData properties = new DedicatedServerProperties.WorldDimensionData(GsonHelper.parse((creator.generatorSettings().isEmpty()) ? "{\"layers\":[],\"biome\":\"minecraft:plains\"}" : creator.generatorSettings()), creator.type().name().toLowerCase(Locale.ROOT));

            worldsettings = new LevelSettings(name, GameType.byId(craftServer.getDefaultGameMode().getValue()), hardcore, net.minecraft.world.Difficulty.valueOf(difficulty.name()), false, new GameRules(), worldloader_a.dataConfiguration());
            worlddimensions = properties.create(worldloader_a.datapackWorldgen());

            WorldDimensions.Complete worlddimensions_b = worlddimensions.bake(iregistry);
            Lifecycle lifecycle = worlddimensions_b.lifecycle().add(worldloader_a.datapackWorldgen().allRegistriesLifecycle());

            worlddata = new PrimaryLevelData(worldsettings, worldoptions, worlddimensions_b.specialWorldProperty(), lifecycle);
            iregistry = worlddimensions_b.dimensions();
        }
        worlddata.customDimensions = iregistry;
        worlddata.checkName(name);
        worlddata.setModdedInfo(craftServer.getServer().getServerModName(), craftServer.getServer().getModdedStatus().shouldReportAsModified());


        long j = BiomeManager.obfuscateSeed(worlddata.worldGenOptions().seed());
        List<CustomSpawner> list = ImmutableList.of();
        LevelStem worlddimension = iregistry.get(actualDimension);

        WorldInfo worldInfo = new CraftWorldInfo(worlddata, worldSession, creator.environment(), worlddimension.type().value(), worlddimension.generator(), craftServer.getHandle().getServer().registryAccess());
        if (biomeProvider == null && generator != null) {
            biomeProvider = generator.getDefaultBiomeProvider(worldInfo);
        }

        ResourceKey<net.minecraft.world.level.Level> worldKey;
        String levelName = craftServer.getServer().getProperties().levelName;
        if (name.equals(levelName + "_nether")) {
            worldKey = net.minecraft.world.level.Level.NETHER;
        } else if (name.equals(levelName + "_the_end")) {
            worldKey = net.minecraft.world.level.Level.END;
        } else {
            worldKey = ResourceKey.create(Registries.DIMENSION, new net.minecraft.resources.ResourceLocation(creator.key().getNamespace().toLowerCase(java.util.Locale.ENGLISH), creator.key().getKey().toLowerCase(java.util.Locale.ENGLISH)));
        }

        ServerLevel internal = (ServerLevel) new NoSaveLevel(craftServer.getServer(), craftServer.getServer().executor, worldSession, worlddata, worldKey, worlddimension, craftServer.getServer().progressListenerFactory.create(11),
                worlddata.isDebugWorld(), j, creator.environment() == World.Environment.NORMAL ? list : ImmutableList.of(), true, craftServer.getServer().overworld().getRandomSequences(), creator.environment(), generator, biomeProvider);

        craftServer.getServer().addLevel(internal);
        craftServer.getServer().initWorld(internal, worlddata, worlddata, worlddata.worldGenOptions());

        internal.setSpawnSettings(true, true);

        internal.keepSpawnInMemory = false;
        internal.getWorld().setAutoSave(false);
        internal.setDayTime(time);
        internal.setDefaultSpawnPos(new BlockPos(spawnPos.getBlockX(), spawnPos.getBlockY(), spawnPos.getBlockZ()), spawnPos.getYaw());
        for (Map.Entry<String, Object> entry : gamerules.entrySet()) {
            if (GameRule.getByName(entry.getKey()) != null) {
                internal.getWorld().setGameRuleValue(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return internal.getWorld();
    }

    private static class NoSaveLevel extends ServerLevel {
        public NoSaveLevel(MinecraftServer server, Executor executor, LevelStorageSource.LevelStorageAccess storage, PrimaryLevelData data, ResourceKey<Level> dimension, LevelStem stem, ChunkProgressListener listener, boolean debug, long seed, List<CustomSpawner> spawners, boolean tickTime, @Nullable RandomSequences sequences, World.Environment environment, ChunkGenerator generator, BiomeProvider biomes) {
            super(server, executor, storage, data, dimension, stem, listener, debug, seed, spawners, tickTime, sequences, environment, generator, biomes);
        }

        @Override
        public void save(@Nullable ProgressListener listener, boolean flush, boolean savingDisabled) {
        }

        @Override
        public void save(@Nullable ProgressListener listener, boolean flush, boolean savingDisabled, boolean close) {
        }

        @Override
        public void saveIncrementally(boolean full) {
        }
    }
}
