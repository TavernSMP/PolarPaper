package live.minehub.polarpaper.core.generator;

import ca.spottedleaf.concurrentutil.lock.ReentrantAreaLock;
import io.papermc.paper.util.WorldUtil;
import io.papermc.paper.world.ChunkEntitySlices;
import io.papermc.paper.chunk.system.scheduling.ChunkHolderManager;
import io.papermc.paper.chunk.system.scheduling.ChunkTaskScheduler;
import io.papermc.paper.chunk.system.scheduling.NewChunkHolder;
import ca.spottedleaf.starlight.common.light.SWMRNibbleArray;
import ca.spottedleaf.starlight.common.light.StarLightEngine;
import ca.spottedleaf.starlight.common.light.StarLightInterface;
import live.minehub.polarpaper.core.util.CoordConversion;
import live.minehub.polarpaper.core.util.TaskFutures;
import live.minehub.polarpaper.core.world.NoUnloadLevelChunk;
import live.minehub.polarpaper.core.world.PolarChunk;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ChunkUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkUtils.class);

    private static final MethodHandle GET_OR_CREATE_CHUNK_HOLDER_HANDLE;
    private static final MethodHandle GET_ENTITIES_HANDLE;
    private static final VarHandle CURRENT_CHUNK_HANDLE;
    private static final VarHandle CURRENT_GEN_STATUS_HANDLE;
    private static final VarHandle LAST_CHUNK_COMPLETION_HANDLE;
    private static final VarHandle ENTITY_CHUNK_HANDLE;
    private static final VarHandle SCHEDULING_LOCK_HANDLE;

    static {
        try {
            GET_ENTITIES_HANDLE = MethodHandles.privateLookupIn(ChunkEntitySlices.class, MethodHandles.lookup()).findVirtual(ChunkEntitySlices.class, "getAllEntities", MethodType.methodType(List.class));
            GET_OR_CREATE_CHUNK_HOLDER_HANDLE = MethodHandles
                    .privateLookupIn(ChunkHolderManager.class, MethodHandles.lookup())
                    .findVirtual(ChunkHolderManager.class, "getOrCreateChunkHolder", MethodType.methodType(NewChunkHolder.class, int.class, int.class));

            CURRENT_CHUNK_HANDLE = MethodHandles
                    .privateLookupIn(NewChunkHolder.class, MethodHandles.lookup())
                    .findVarHandle(NewChunkHolder.class, "currentChunk", ChunkAccess.class);
            CURRENT_GEN_STATUS_HANDLE = MethodHandles
                    .privateLookupIn(NewChunkHolder.class, MethodHandles.lookup())
                    .findVarHandle(NewChunkHolder.class, "currentGenStatus", ChunkStatus.class);
            LAST_CHUNK_COMPLETION_HANDLE = MethodHandles
                    .privateLookupIn(NewChunkHolder.class, MethodHandles.lookup())
                    .findVarHandle(NewChunkHolder.class, "lastChunkCompletion", NewChunkHolder.ChunkCompletion.class);
            SCHEDULING_LOCK_HANDLE = MethodHandles.privateLookupIn(ChunkTaskScheduler.class, MethodHandles.lookup()).findVarHandle(ChunkTaskScheduler.class, "schedulingLockArea", ReentrantAreaLock.class);
            ENTITY_CHUNK_HANDLE = MethodHandles
                    .privateLookupIn(NewChunkHolder.class, MethodHandles.lookup())
                    .findVarHandle(NewChunkHolder.class, "entityChunk", ChunkEntitySlices.class);
        } catch (NoSuchFieldException | IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<net.minecraft.world.entity.Entity> getEntities(ChunkEntitySlices slices) {
        try {
            return (List<net.minecraft.world.entity.Entity>) GET_ENTITIES_HANDLE.invoke(slices);
        } catch (Throwable failure) {
            throw new IllegalStateException("Cannot read Paper 1.20.1 chunk entities", failure);
        }
    }

    public static void insertChunk(ServerLevel serverLevel, NoUnloadLevelChunk newLevelChunk) {
        newLevelChunk.setLightCorrect(true);
        newLevelChunk.setUnsaved(false);

        int chunkX = newLevelChunk.locX;
        int chunkZ = newLevelChunk.locZ;
        ChunkTaskScheduler chunkTaskScheduler = serverLevel.chunkTaskScheduler;
        ChunkHolderManager chunkHolderManager = chunkTaskScheduler.chunkHolderManager;

        // Begin reflection hell :D
        ReentrantAreaLock.Node lock = chunkHolderManager.ticketLockArea.lock(chunkX, chunkZ);
        ReentrantAreaLock.Node lock1 = ((ReentrantAreaLock) SCHEDULING_LOCK_HANDLE.get(chunkTaskScheduler)).lock(chunkX, chunkZ);
        NewChunkHolder newChunkHolder;
        try {
            newChunkHolder = (NewChunkHolder) GET_OR_CREATE_CHUNK_HOLDER_HANDLE.invokeExact(chunkHolderManager, chunkX, chunkZ);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        } finally {
            ((ReentrantAreaLock) SCHEDULING_LOCK_HANDLE.get(chunkTaskScheduler)).unlock(lock1);
            chunkHolderManager.ticketLockArea.unlock(lock);
        }

        newLevelChunk.needsDecoration = false;
        newLevelChunk.mustNotSave = true;
        CURRENT_CHUNK_HANDLE.set(newChunkHolder, newLevelChunk);
        CURRENT_GEN_STATUS_HANDLE.set(newChunkHolder, ChunkStatus.FULL);
        newLevelChunk.setChunkHolder(newChunkHolder);

        LAST_CHUNK_COMPLETION_HANDLE.set(newChunkHolder, new NewChunkHolder.ChunkCompletion(newLevelChunk, ChunkStatus.FULL));

        CompletableFuture.runAsync(() -> Heightmap.primeHeightmaps(newLevelChunk, ChunkStatus.FULL.heightmapsAfter()))
                .exceptionally(e -> {
                    LOGGER.error("Heightmaps failed to prime", e);
                    return null;
                });

        newLevelChunk.setFullStatus(() -> FullChunkStatus.ENTITY_TICKING);
        newLevelChunk.runPostLoad();
        newLevelChunk.setLoaded(true);
        newLevelChunk.registerAllBlockEntitiesAfterLevelLoad();
        newLevelChunk.registerTickContainerInLevel(serverLevel);

        initializeEntityChunk(newChunkHolder);
    }

    public static SWMRNibbleArray[] createNibbleArray(int sectionCount) {
        SWMRNibbleArray[] array = new SWMRNibbleArray[sectionCount];
        array[0] = new SWMRNibbleArray();
        array[array.length - 1] = new SWMRNibbleArray();
        return array;
    }

    public static void lightChunk(ServerLevel level, LevelChunk chunk) {
        ThreadedLevelLightEngine threadedEngine = (ThreadedLevelLightEngine) level.getLightEngine();
        StarLightInterface starlight = threadedEngine.theLightEngine;
        starlight.lightChunk(chunk, StarLightEngine.getEmptySectionsForChunk(chunk));
    }

    private static ChunkEntitySlices initializeEntityChunk(NewChunkHolder holder) {
        ChunkEntitySlices slices = new ChunkEntitySlices(
                holder.world, holder.chunkX, holder.chunkZ, holder.getChunkStatus(),
                WorldUtil.getMinSection(holder.world), WorldUtil.getMaxSection(holder.world)
        );
        slices.setTransient(false);

        ENTITY_CHUNK_HANDLE.set(holder, slices);

        holder.world.getEntityLookup().entitySectionLoad(holder.chunkX, holder.chunkZ, slices);

        return slices;
    }

    public static void addBlockEntity(PolarChunk.BlockEntity polarBlockEntity, ChunkAccess chunk) {
        int posIndex = polarBlockEntity.index();
        CompoundTag nbt = polarBlockEntity.data();

        int x = CoordConversion.chunkBlockIndexGetX(posIndex);
        int y = CoordConversion.chunkBlockIndexGetY(posIndex);
        int z = CoordConversion.chunkBlockIndexGetZ(posIndex);

        addBlockEntity(x, y, z, nbt, chunk);
    }

    public static void addBlockEntity(CompoundTag tag, ChunkAccess chunk) {
        int x = tag.getInt("x");
        int y = tag.getInt("y");
        int z = tag.getInt("z");
        addBlockEntity(x, y, z, tag, chunk);
    }

    public static void addBlockEntity(int x, int y, int z, @Nullable CompoundTag nbt, ChunkAccess chunk) {
        BlockState blockState = chunk.getBlockState(x, y, z);
        BlockPos blockPos = new BlockPos(chunk.locX * 16 + x, y, chunk.locZ * 16 + z);

        if (!(blockState.getBlock() instanceof EntityBlock entityBlock)) {
//            PolarPaper.logger().warning("Block " + blockState + " does not have a block entity");
//            throw new IllegalArgumentException("Block " + blockState + " does not have a block entity");
            return;
        }

        BlockEntity blockEntity = entityBlock.newBlockEntity(blockPos, blockState);
        if (blockEntity == null) {
//            PolarPaper.logger().warning("Block " + blockState + " returned null block entity");
//            throw new IllegalArgumentException("Block " + blockState + " returned null block entity");
            return;
        }

        if (nbt != null) {
            blockEntity.load(nbt);
        }
        if (chunk instanceof LevelChunk levelChunk) {
            blockEntity.setLevel(levelChunk.getLevel());
            levelChunk.addAndRegisterBlockEntity(blockEntity);
        } else {
            chunk.setBlockEntityNbt(blockEntity.saveWithFullMetadata());
        }
    }

    public static CompletableFuture<Void> insertEmptyChunks(Plugin plugin, ServerLevel level) {
        int paddedViewDist = level.getWorld().getViewDistance() + 2;

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int x = -paddedViewDist; x <= paddedViewDist; x++) {
            for (int z = -paddedViewDist; z <= paddedViewDist; z++) {
                int finalX = x;
                int finalZ = z;
                CompletableFuture<Void> future = TaskFutures.runRegion(plugin, level.getWorld(), x, z, () -> {
                    NoUnloadLevelChunk emptyChunk = createEmptyChunk(level, finalX, finalZ);
                    insertChunk(level, emptyChunk);
                    return null;
                });

                futures.add(future);
            }
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private static NoUnloadLevelChunk createEmptyChunk(ServerLevel level, int x, int z) {
        NoUnloadLevelChunk emptyChunk = new NoUnloadLevelChunk(level, new ChunkPos(x, z));
        boolean[] emptySections = new boolean[emptyChunk.getSectionsCount()];
        Arrays.fill(emptySections, true);
        emptyChunk.setBlockEmptinessMap(emptySections.clone());
        emptyChunk.setSkyEmptinessMap(emptySections);
        emptyChunk.setLightCorrect(true);
        return emptyChunk;
    }

}
