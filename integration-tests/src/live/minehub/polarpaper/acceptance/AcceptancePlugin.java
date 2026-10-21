package live.minehub.polarpaper.acceptance;

import live.minehub.polarpaper.Polar;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.core.config.Config;
import live.minehub.polarpaper.core.generator.PolarGenerator;
import live.minehub.polarpaper.core.source.FilePolarSource;
import live.minehub.polarpaper.core.util.TaskFutures;
import live.minehub.polarpaper.core.world.PolarWorld;
import live.minehub.polarpaper.core.world.PolarDataConverter;
import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

public final class AcceptancePlugin extends JavaPlugin {
    private final NamespacedKey marker = new NamespacedKey("acceptance", "marker");
    private int assertions;

    @Override
    public void onEnable() {
        if (!Boolean.getBoolean("polarpaper.acceptance")) {
            return;
        }
        Bukkit.getScheduler().runTask(this, () -> {
            CompletableFuture<Void> tests;
            try {
                if (Files.exists(getDataFolder().toPath().resolve("restart.marker"))) {
                    World world = Bukkit.getWorld(new NamespacedKey("polarpaper", "acceptance"));
                    tests = waitForLoaded(world).thenCompose(ignored -> settle(world)).thenCompose(ignored -> TaskFutures.runTickThread(this, () -> {
                        verify(world);
                        getLogger().info("ACCEPTANCE RESTART PASS: " + assertions + " assertions");
                        return null;
                    }));
                } else {
                    tests = runFirstBoot();
                }
            } catch (Throwable failure) {
                tests = CompletableFuture.failedFuture(failure);
            }
            tests.whenComplete((ignored, failure) -> {
                if (failure != null) {
                    getLogger().log(java.util.logging.Level.SEVERE, "ACCEPTANCE FAIL", failure);
                }
                Bukkit.getScheduler().runTask(this, Bukkit::shutdown);
            });
        });
    }

    private CompletableFuture<Void> runFirstBoot() {
        Config config = Config.Builder.defaults().autoSaveIntervalTicks(-1).saveOnStop(true).loadOnStartup(true).saveLight(true).build();
        return Polar.createWorld(new PolarWorld((byte) -4, (byte) 19), "acceptance", config)
                .thenCompose(world -> TaskFutures.runTickThread(this, () -> {
                    require(world != null, "create a Polar world");
                    require(world.getGameRuleValue(GameRule.DO_MOB_SPAWNING) == Boolean.FALSE, "legacy gamerules applied");
                    world.getBlockAt(1, 64, 1).setType(Material.STONE, false);
                    world.getBlockAt(-16, 65, -17).setType(Material.DIAMOND_BLOCK, false);
                    world.getBlockAt(2, 64, 1).setType(Material.CHEST, false);
                    Chest chest = (Chest) world.getBlockAt(2, 64, 1).getState();
                    ItemStack item = new ItemStack(Material.DIAMOND, 7);
                    item.editMeta(meta -> meta.getPersistentDataContainer().set(marker, PersistentDataType.STRING, "item"));
                    chest.getSnapshotInventory().setItem(0, item);
                    chest.getPersistentDataContainer().set(marker, PersistentDataType.STRING, "tile");
                    chest.update(true, false);
                    world.getBlockAt(3, 64, 1).setType(Material.OAK_SIGN, false);
                    Sign sign = (Sign) world.getBlockAt(3, 64, 1).getState();
                    sign.setLine(0, "Paper 1.20.1");
                    sign.update(true, false);
                    world.getChunkAt(0, 0).getPersistentDataContainer().set(marker, PersistentDataType.STRING, "chunk");
                    ArmorStand stand = world.spawn(new Location(world, 4.5, 65, 1.5), ArmorStand.class);
                    stand.setGravity(false);
                    stand.setPersistent(true);
                    stand.getPersistentDataContainer().set(marker, PersistentDataType.STRING, "entity");
                    stand.setCustomName("Polar 1.20.1");
                    org.bukkit.entity.Entity boat = world.spawnEntity(new Location(world, 6.5, 65, 1.5), EntityType.BOAT);
                    boat.setGravity(false);
                    boat.getPersistentDataContainer().set(marker, PersistentDataType.STRING, "vehicle");
                    require(boat.addPassenger(stand), "fixture passenger attached");
                    ItemDisplay display = world.spawn(new Location(world, 5.5, 65, 1.5), ItemDisplay.class);
                    display.setItemStack(new ItemStack(Material.EMERALD));
                    display.getPersistentDataContainer().set(marker, PersistentDataType.STRING, "display");
                    for (int index = 0; index < 300; index++) {
                        world.getBlockAt(index & 15, 80 + (index >> 8), (index >> 4) & 15).setBlockData(paletteState(index), false);
                    }
                    world.getBlockAt(7, 65, 1).setType(Material.GLOWSTONE, false);
                    world.setTime(7000);
                    PolarGenerator.fromWorld(world).setSource(Polar.getDefaultFolderSource("acceptance"));
                    require(((Chest) world.getBlockAt(2, 64, 1).getState()).getBlockInventory().getItem(0) != null, "fixture container populated");
                    Config.writeToConfig(PolarPaper.getConfigPath(), PolarPaper.getPlugin().getConfig(), "acceptance", config);
                    Polar.updateConfig(world, "acceptance");
                    return world;
                }))
                .thenCompose(this::settle)
                .thenCompose(world -> Polar.saveWorld(world).thenApply(ignored -> world))
                .thenCompose(world -> TaskFutures.runTickThread(this, () -> {
                    require(Files.exists(PolarPaper.getWorldsPath().resolve("acceptance.polar")), "save a Polar file");
                    require(Bukkit.unloadWorld(world, false), "unload the Polar world");
                    return null;
                }))
                .thenCompose(ignored -> Polar.createWorld(Polar.getDefaultFolderSource("acceptance"), "acceptance"))
                .thenCompose(world -> settle(world).thenCompose(ignored -> TaskFutures.runTickThread(this, () -> {
                    verify(world);
                    boolean rejected = false;
                    try {
                        PolarDataConverter.DEFAULT.validateDataVersion(Integer.MAX_VALUE);
                    } catch (IllegalArgumentException expected) {
                        rejected = true;
                    }
                    require(rejected, "reject newer Minecraft data versions");
                    require(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "polar help"), "register the /polar command");
                    require(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "polar list"), "execute /polar list");
                    return null;
                })))
                .thenCompose(ignored -> commandRoundTrip())
                .thenRun(() -> {
                    try {
                        Files.createDirectories(getDataFolder().toPath());
                        Files.writeString(getDataFolder().toPath().resolve("restart.marker"), "restart pending");
                    } catch (java.io.IOException exception) {
                        throw new java.io.UncheckedIOException(exception);
                    }
                    getLogger().info("ACCEPTANCE FIRST BOOT PASS: " + assertions + " assertions");
                });
    }

    private org.bukkit.block.data.BlockData paletteState(int index) {
        int value = index % 162;
        String material = index < 162 ? "cobblestone_wall" : "mossy_cobblestone_wall";
        String up = value % 2 == 0 ? "true" : "false";
        value /= 2;
        String[] height = {"none", "low", "tall"};
        String properties = "up=" + up;
        for (String direction : new String[]{"north", "east", "south", "west"}) {
            properties += "," + direction + "=" + height[value % 3];
            value /= 3;
        }
        return Bukkit.createBlockData("minecraft:" + material + "[" + properties + ",waterlogged=false]");
    }

    private CompletableFuture<Void> commandRoundTrip() {
        NamespacedKey key = new NamespacedKey("polarpaper", "command_fixture");
        require(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "polar createblank command_fixture"), "execute /polar createblank");
        return await(() -> Bukkit.getWorld(key) != null && !Polar.isLoading(key))
                .thenRun(() -> {
                    Bukkit.getWorld(key).getBlockAt(0, 70, 0).setType(Material.GOLD_BLOCK, false);
                    require(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "polar save command_fixture"), "execute /polar save");
                })
                .thenCompose(ignored -> await(() -> Files.exists(PolarPaper.getWorldsPath().resolve("command_fixture.polar"))))
                .thenRun(() -> require(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "polar unload command_fixture false"), "execute /polar unload"))
                .thenCompose(ignored -> await(() -> Bukkit.getWorld(key) == null))
                .thenRun(() -> require(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "polar load command_fixture.polar"), "execute /polar load"))
                .thenCompose(ignored -> await(() -> Bukkit.getWorld(key) != null && !Polar.isLoading(key)))
                .thenRun(() -> require(Bukkit.getWorld(key).getBlockAt(0, 70, 0).getType() == Material.GOLD_BLOCK, "command save and load preserves blocks"));
    }

    private CompletableFuture<Void> await(java.util.function.BooleanSupplier condition) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskTimer(this, task -> {
            if (result.isDone()) {
                task.cancel();
                return;
            }
            try {
                if (condition.getAsBoolean()) {
                    result.complete(null);
                    task.cancel();
                }
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
                task.cancel();
            }
        }, 1L, 1L);
        return result.orTimeout(60, java.util.concurrent.TimeUnit.SECONDS);
    }

    private CompletableFuture<World> settle(World world) {
        world.getChunkAt(0, 0).load();
        CompletableFuture<World> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskLater(this, () -> future.complete(world), 5L);
        return future;
    }

    private CompletableFuture<Void> waitForLoaded(World world) {
        if (world == null) {
            return CompletableFuture.failedFuture(new AssertionError("startup world exists"));
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskTimer(this, task -> {
            if (!Polar.isLoading(world.getKey())) {
                result.complete(null);
                task.cancel();
            }
        }, 1L, 1L);
        return result.orTimeout(60, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void verify(World world) {
        require(world != null, "loaded world exists");
        require(!world.isAutoSave(), "vanilla world saving disabled");
        require(world.getBlockAt(1, 64, 1).getType() == Material.STONE, "block palette and coordinates");
        require(world.getBlockAt(-16, 65, -17).getType() == Material.DIAMOND_BLOCK, "negative chunk coordinates");
        require(java.util.stream.IntStream.range(0, 300).allMatch(index -> world.getBlockAt(index & 15, 80 + (index >> 8), (index >> 4) & 15).getBlockData().equals(paletteState(index))), "global block palette (300 states)");
        require(world.getBlockAt(7, 65, 1).getLightFromBlocks() >= 14, "block lighting");
        require(world.getTime() >= 7000 && world.getTime() < 7500, "world time restored");
        Chest chest = (Chest) world.getBlockAt(2, 64, 1).getState();
        ItemStack item = chest.getBlockInventory().getItem(0);
        require(item != null && item.getType() == Material.DIAMOND && item.getAmount() == 7, "container inventory");
        require("item".equals(item.getItemMeta().getPersistentDataContainer().get(marker, PersistentDataType.STRING)), "item PDC");
        require("tile".equals(chest.getPersistentDataContainer().get(marker, PersistentDataType.STRING)), "block entity PDC");
        require("Paper 1.20.1".equals(((Sign) world.getBlockAt(3, 64, 1).getState()).getLine(0)), "sign NBT");
        require("chunk".equals(world.getChunkAt(0, 0).getPersistentDataContainer().get(marker, PersistentDataType.STRING)), "chunk PDC");
        require(world.getEntities().stream().anyMatch(entity -> entity.getType() == EntityType.ARMOR_STAND && "entity".equals(entity.getPersistentDataContainer().get(marker, PersistentDataType.STRING))), "entity NBT and PDC");
        require(world.getEntities().stream().filter(entity -> "entity".equals(entity.getPersistentDataContainer().get(marker, PersistentDataType.STRING))).count() == 1, "passenger saved exactly once");
        require(world.getEntities().stream().anyMatch(entity -> "entity".equals(entity.getPersistentDataContainer().get(marker, PersistentDataType.STRING)) && entity.getVehicle() != null), "passenger mount restored");
        require(world.getEntities().stream().anyMatch(entity -> entity.getType() == EntityType.ITEM_DISPLAY && "display".equals(entity.getPersistentDataContainer().get(marker, PersistentDataType.STRING))), "display entity");
    }

    private void require(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError(name);
        }
        assertions++;
        getLogger().info("PASS " + name);
    }
}
