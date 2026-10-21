package live.minehub.polarpaper.nms;

import live.minehub.polarpaper.Polar;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.core.WorldUnloader;
import live.minehub.polarpaper.core.userdata.EntitySerializer;
import live.minehub.polarpaper.util.EntitiesWorldAccess;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class VersionUtil {
    private VersionUtil() {
    }

    public static CompletableFuture<@Nullable World> createNoSaveLevel(WorldCreator creator, Location spawnPos, Difficulty difficulty, Map<String, Object> gamerules, long time) {
        Polar.setLoading(creator.key(), true);
        Plugin plugin = PolarPaper.getPlugin();
        return new live.minehub.polarpaper.paper_1_20_1.NoSaveLevelCreatorImpl().createLevel(plugin, creator, spawnPos, difficulty, gamerules, time);
    }

    public static EntitiesWorldAccess getPolarFeaturesWorldAccess() {
        return new EntitiesWorldAccess(PolarPaper.getPlugin(), getEntitySerializer());
    }

    public static EntitySerializer getEntitySerializer() {
        return new live.minehub.polarpaper.paper_1_20_1.EntitySerializerImpl();
    }

    public static WorldUnloader getWorldUnloader() {
        return (plugin, world) -> live.minehub.polarpaper.core.util.TaskFutures.runGlobal(plugin, () -> org.bukkit.Bukkit.unloadWorld(world, false));
    }

}
