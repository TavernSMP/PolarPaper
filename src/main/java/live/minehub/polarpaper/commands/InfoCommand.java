package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import live.minehub.polarpaper.PolarPaper;
import live.minehub.polarpaper.core.generator.PolarGenerator;
import live.minehub.polarpaper.util.WorldKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.resources.ResourceLocation;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class InfoCommand extends PolarCmd {

    public InfoCommand() {
        super("info", "Get info for a polar world");
    }

    protected static int printInfo(CommandContext<CommandSourceStack> ctx, ResourceLocation worldId) {
        World bukkitWorld = WorldKey.getWorld(worldId);
        if (bukkitWorld == null) {
            ctx.getSource().getBukkitSender().sendMessage(
                    Component.text()
                            .append(Component.text("'", NamedTextColor.RED))
                            .append(Component.text(worldId.getPath(), NamedTextColor.RED))
                            .append(Component.text("' does not exist", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        PolarGenerator polarGenerator = PolarGenerator.fromWorld(bukkitWorld);
        if (polarGenerator == null) {
            ctx.getSource().getBukkitSender().sendMessage(
                    Component.text()
                            .append(Component.text("'", NamedTextColor.RED))
                            .append(Component.text(worldId.getPath(), NamedTextColor.RED))
                            .append(Component.text("' is not a polar world", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        Bukkit.getGlobalRegionScheduler().execute(PolarPaper.getPlugin(), () -> {
            Component infoComponent = polarGenerator.getInfoComponent(bukkitWorld);
            ctx.getSource().getBukkitSender().sendMessage(infoComponent);
        });

        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected int executeDefault(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getBukkitSender();
        if (!(sender instanceof Player player)) {
            ctx.getSource().getBukkitSender().sendMessage(
                    Component.text()
                            .append(Component.text("Usage: /polar info (while in a polar world)", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        NamespacedKey worldKey = player.getWorld().getKey();
        return printInfo(ctx, new ResourceLocation(worldKey.getNamespace(), worldKey.getKey()));
    }

    private static int executeArgument(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation worldId = ctx.getArgument("world name", ResourceLocation.class);
        return printInfo(ctx, worldId);
    }

    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(createWorldNameArgument(true)
                .executes(InfoCommand::executeArgument));
    }
}
