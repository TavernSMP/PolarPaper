package live.minehub.polarpaper.core.world;

import io.papermc.paper.chunk.system.entity.EntityLookup;
import live.minehub.polarpaper.core.userdata.EntitySerializer;
import live.minehub.polarpaper.core.util.MemorySegmentReader;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.entity.Entity;
import org.bukkit.*;
import org.bukkit.craftbukkit.v1_20_R1.CraftWorld;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.Locale;
import java.util.UUID;

public record PolarEntity(double x, double y, double z, float yaw, float pitch, byte[] bytes) {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolarEntity.class);

    public @Nullable Entity toNMSEntity(EntitySerializer entitySerializer, World world, Location spawnLocation) {
        MemorySegmentReader reader = new MemorySegmentReader(MemorySegment.ofArray(bytes));
        CompoundTag compound;
        try {
            compound = NbtIo.read(reader, NbtAccounter.UNLIMITED);
        } catch (IOException _) {
//                ExceptionUtil.log(e);
            return null;
        }
        return toNMSEntity(entitySerializer, world, spawnLocation, compound);
    }

    public static @Nullable Entity toNMSEntity(EntitySerializer entitySerializer, World world, Location spawnLocation, CompoundTag compound) {
        if (!compound.contains("DataVersion", net.minecraft.nbt.Tag.TAG_ANY_NUMERIC)) {
            return null;
        }
        int dataVersion = compound.getInt("DataVersion");
        compound = (CompoundTag) MinecraftServer.getServer().getFixerUpper().update(References.ENTITY, new com.mojang.serialization.Dynamic<>(NbtOps.INSTANCE, compound), dataVersion, SharedConstants.getCurrentVersion().getDataVersion().getVersion()).getValue();

        ServerLevel level = ((CraftWorld) world).getHandle();
        EntityLookup entityLookup = level.getEntityLookup();
        UUID entityUUID = getUUID(compound);
        if (entityUUID != null) {
            boolean hasEntity = entityLookup.hasEntity(entityUUID);
            if (hasEntity) {
                LOGGER.warn("Entity already exists with UUID {}, using random UUID", entityUUID);
                compound.remove("UUID"); // do not read UUID from bytes, instead use the default random uuid
            }
        }

        ListTag posTag = new ListTag();
        posTag.add(DoubleTag.valueOf(spawnLocation.x()));
        posTag.add(DoubleTag.valueOf(spawnLocation.y()));
        posTag.add(DoubleTag.valueOf(spawnLocation.z()));
        compound.put("Pos", posTag);
        ListTag rotationTag = new ListTag();
        rotationTag.add(FloatTag.valueOf(spawnLocation.getYaw()));
        rotationTag.add(FloatTag.valueOf(spawnLocation.getPitch()));
        compound.put("Rotation", rotationTag);

        String entityId = compound.getString("id").replace("minecraft:", "");

        // Rotate painting
        String paintingVariant = compound.getString("variant");
        if (entityId.equals("painting") && !paintingVariant.isEmpty()) {
            int facingIndex = Math.floorMod((int) Math.floor(spawnLocation.getYaw() / 90), 4);
            Direction[] directions = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
            compound.putByte("Facing", (byte) facingIndex);

            Direction paintingDir = directions[facingIndex];
            Direction counterClockWise = paintingDir.getCounterClockWise();
            Vec3i unitVec3i = counterClockWise.getNormal();

            Art art = org.bukkit.Registry.ART
                    .get(NamespacedKey.fromString(paintingVariant.toLowerCase(Locale.ROOT)));
            if (art != null && art.getBlockHeight() % 2 == 0) spawnLocation.subtract(0, 1, 0);
            if (art != null && art.getBlockWidth() % 2 == 0) {
                spawnLocation.subtract(unitVec3i.getX() * 0.5, 0, unitVec3i.getZ() * 0.5);
            }

            compound.putInt("TileX", spawnLocation.getBlockX());
            compound.putInt("TileY", spawnLocation.getBlockY());
            compound.putInt("TileZ", spawnLocation.getBlockZ());
        }

        // Rotate item frame
        // 3-south, 4-west, 2-north, 5-east, 1-top, and 0-bottom
        int facingValue = compound.getInt("Facing");
        if ((entityId.equals("item_frame") || entityId.equals("glow_item_frame")) && facingValue != 1 && facingValue != 0) {
            int facingIndex = Math.floorMod((int) Math.floor(spawnLocation.getYaw() / 90), 4);
            Direction[] directions = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};
            int newFacingValue = directions[facingIndex].get3DDataValue();
            compound.put("Facing", IntTag.valueOf(newFacingValue));

            compound.putInt("TileX", spawnLocation.getBlockX());
            compound.putInt("TileY", spawnLocation.getBlockY());
            compound.putInt("TileZ", spawnLocation.getBlockZ());
        }

        Entity nmsEntity = entitySerializer.compoundToEntity(world, compound);
        if (nmsEntity == null) return null;

        return nmsEntity;
    }

    public Location getLocation(Chunk chunk) {
        return getLocation(chunk.getWorld(), chunk.getX(), chunk.getZ());
    }

    public Location getLocation(@Nullable World world, int chunkX, int chunkZ) {
        // fix for previous version and also sanity check :)
        double realX = x;
        double realZ = z;
        if (x < 0) realX += 16;
        if (z < 0) realZ += 16;

        return new Location(world, realX + chunkX * 16, y, realZ + chunkZ * 16, yaw, pitch);
    }

    private static @Nullable UUID getUUID(CompoundTag compound) {
        int[] ints = compound.getIntArray("UUID");
        if (ints == null || ints.length != 4) return null;

        long msb = ((long) ints[0] << 32) | (ints[1] & 0xFFFFFFFFL);
        long lsb = ((long) ints[2] << 32) | (ints[3] & 0xFFFFFFFFL);

        return new UUID(msb, lsb);
    }

}
