package live.minehub.polarpaper.core.world;

import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;
import live.minehub.polarpaper.core.util.BlockCodec;
import live.minehub.polarpaper.core.util.PaletteUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Representation of the latest version of the section format.
 * <p>
 * Marked as internal because of the use of mutable arrays. These arrays must _not_ be mutated.
 * This class should be considered immutable.
 */
@ApiStatus.Internal
public record PolarSection(boolean empty, String[] blockPalette, long @Nullable [] blockData, String[] biomePalette,
                           long @Nullable [] biomeData, LightContent blockLightContent,
                           @Nullable SWMRNibbleArray blockLight, LightContent skyLightContent,
                           @Nullable SWMRNibbleArray skyLight) {
    private static final Logger LOGGER = LoggerFactory.getLogger(PolarSection.class);

    public enum LightContent {
        MISSING, EMPTY, FULL, PRESENT;

        public static final LightContent[] VALUES = values();
    }

    public PolarSection() {
        this(true, new String[]{"minecraft:air"}, null, new String[]{"minecraft:plains"}, null, LightContent.MISSING, null, LightContent.MISSING, null);
    }

    public PolarSection(
            String[] blockPalette, long @Nullable [] blockData,
            String[] biomePalette, long @Nullable [] biomeData,
            LightContent blockLightContent, @Nullable SWMRNibbleArray blockLight,
            LightContent skyLightContent, @Nullable SWMRNibbleArray skyLight
    ) {
        this(false, blockPalette, blockData, biomePalette, biomeData, blockLightContent, blockLight, skyLightContent, skyLight);
    }

    public PolarSection(
            LightContent blockLightContent, @Nullable SWMRNibbleArray blockLight,
            LightContent skyLightContent, @Nullable SWMRNibbleArray skyLight
    ) {
        this(false, new String[]{"minecraft:air"}, null, new String[]{"minecraft:plains"}, null, blockLightContent, blockLight, skyLightContent, skyLight);
    }

    /**
     * Returns the uncompressed palette data. Each int corresponds to an index in the palette.
     * Always has a length of 4096.
     */
    @Override
    public long[] blockData() {
        assert blockData != null : "must check length of blockPalette() before using blockData()";
        return blockData;
    }

    /**
     * Returns the uncompressed palette data. Each int corresponds to an index in the palette.
     * Always has a length of 256.
     */
    @Override
    public long[] biomeData() {
        assert biomeData != null : "must check length of biomePalette() before using biomeData()";
        return biomeData;
    }

    @Override
    public SWMRNibbleArray blockLight() {
        assert blockLight != null : "must check hasBlockLightData() before calling blockLight()";
        return blockLight;
    }

    @Override
    public SWMRNibbleArray skyLight() {
        assert skyLight != null : "must check hasSkyLightData() before calling skyLight()";
        return skyLight;
    }

    public LevelChunkSection createEmptyLevelChunkSection(RegistryAccess registryAccess) {
        return new LevelChunkSection(registryAccess.registryOrThrow(Registries.BIOME));
    }

    public LevelChunkSection createLevelChunkSection(RegistryAccess registryAccess) {
        Registry<Biome> registry = registryAccess.registryOrThrow(Registries.BIOME);
        Holder<Biome> plains = registry.getHolderOrThrow(Biomes.PLAINS);
        PalettedContainer<BlockState> states = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES);
        PalettedContainer<Holder<Biome>> biomes = new PalettedContainer<>(registry.asHolderIdMap(), plains, PalettedContainer.Strategy.SECTION_BIOMES);
        if (!empty) {
            BlockState[] blocks = new BlockState[blockPalette.length];
            for (int i = 0; i < blocks.length; i++) {
                blocks[i] = BlockCodec.blockFromString(blockPalette[i]);
            }
            Holder<Biome>[] biomeHolders = new Holder[biomePalette.length];
            for (int i = 0; i < biomeHolders.length; i++) {
                ResourceLocation key = ResourceLocation.tryParse(biomePalette[i]);
                biomeHolders[i] = key == null ? plains : registry.getHolder(net.minecraft.resources.ResourceKey.create(Registries.BIOME, key)).orElse((Holder.Reference<Biome>) plains);
            }
            int[] blockIndices = unpackIndices(blockData, 4096);
            int[] biomeIndices = unpackIndices(biomeData, 64);
            for (int i = 0; i < blockIndices.length; i++) {
                states.set(i & 15, i >> 8, (i >> 4) & 15, blocks[blockIndices[i]]);
            }
            for (int i = 0; i < biomeIndices.length; i++) {
                biomes.set(i & 3, i >> 4, (i >> 2) & 3, biomeHolders[biomeIndices[i]]);
            }
        }
        return new LevelChunkSection(states, biomes);
    }

    private static int[] unpackIndices(long[] packed, int count) {
        int[] indices = new int[count];
        if (packed != null && packed.length != 0) {
            PaletteUtil.unpack(indices, packed, PaletteUtil.getBitsForLongLength(packed.length, count));
        }
        return indices;
    }
}
