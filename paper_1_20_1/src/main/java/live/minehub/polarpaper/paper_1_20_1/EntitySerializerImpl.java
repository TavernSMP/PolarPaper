package live.minehub.polarpaper.paper_1_20_1;

import live.minehub.polarpaper.core.userdata.EntitySerializer;
import live.minehub.polarpaper.core.util.MemorySegmentWriter;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_20_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import java.io.IOException;

public class EntitySerializerImpl implements EntitySerializer {
    @Override
    public net.minecraft.world.entity.@Nullable Entity compoundToEntity(World world, CompoundTag compound) {
        return net.minecraft.world.entity.EntityType.loadEntityRecursive(compound, ((CraftWorld) world).getHandle(), entity -> entity);
    }

    @Override
    public byte @Nullable [] entityToBytes(Entity entity, Plugin plugin) {
        CompoundTag compound = new CompoundTag();
        if (!((CraftEntity) entity).getHandle().saveAsPassenger(compound) || compound.getString("id").isEmpty()) {
            return null;
        }
        compound.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        try (MemorySegmentWriter writer = new MemorySegmentWriter(256)) {
            NbtIo.write(compound, writer);
            return writer.getWrittenBytes();
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }
}
