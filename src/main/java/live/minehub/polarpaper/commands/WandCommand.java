package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import live.minehub.polarpaper.schematic.Schematic;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

public class WandCommand {

    private static final ItemStack ITEM_STACK = new ItemStack(Material.BLAZE_ROD);
    public static final NamespacedKey ITEM_STACK_KEY = new NamespacedKey("polarpaper", "wand");

    static {
        ITEM_STACK.editMeta(ItemMeta.class, meta -> {
            meta.displayName(Component.text("Polar Wand", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            PersistentDataContainer data = meta.getPersistentDataContainer();
            data.set(ITEM_STACK_KEY, PersistentDataType.BYTE, (byte) 1);
        });
    }

    protected static int pos1(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getBukkitSender();
        if (!(sender instanceof Player player)) {
            ctx.getSource().getBukkitSender().sendMessage(
                    Component.text()
                            .append(Component.text("Usage: /polar pos1 (while in a world)", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        PersistentDataContainer data = player.getPersistentDataContainer();

        Vector blockPos = player.getLocation().toVector();
        int[] array = new int[] { blockPos.getBlockX(), blockPos.getBlockY(), blockPos.getBlockZ() };
        data.set(Schematic.POS_1_KEY, PersistentDataType.INTEGER_ARRAY, array);

        String formattedPos = String.format("%s, %s, %s", blockPos.getBlockX(), blockPos.getBlockY(), blockPos.getBlockZ());
        player.sendMessage(Component.text("Set first corner position to " + formattedPos, NamedTextColor.AQUA));

        return Command.SINGLE_SUCCESS;
    }

    protected static int pos2(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getBukkitSender();
        if (!(sender instanceof Player player)) {
            ctx.getSource().getBukkitSender().sendMessage(
                    Component.text()
                            .append(Component.text("Usage: /polar pos2 (while in a world)", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        PersistentDataContainer data = player.getPersistentDataContainer();

        Vector blockPos = player.getLocation().toVector();
        int[] array = new int[] { blockPos.getBlockX(), blockPos.getBlockY(), blockPos.getBlockZ() };
        data.set(Schematic.POS_2_KEY, PersistentDataType.INTEGER_ARRAY, array);

        String formattedPos = String.format("%s, %s, %s", blockPos.getBlockX(), blockPos.getBlockY(), blockPos.getBlockZ());
        player.sendMessage(Component.text("Set second corner position to " + formattedPos, NamedTextColor.AQUA));

        return Command.SINGLE_SUCCESS;
    }

    protected static int wand(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getBukkitSender();
        if (!(sender instanceof Player player)) {
            ctx.getSource().getBukkitSender().sendMessage(
                    Component.text()
                            .append(Component.text("Usage: /polar wand (while in a world)", NamedTextColor.RED))
            );
            return Command.SINGLE_SUCCESS;
        }

        boolean success = player.getInventory().addItem(ITEM_STACK).isEmpty();
        if (!success) {
            player.sendMessage(Component.text("Cannot give you a wand because your inventory is full!", NamedTextColor.RED));
        }
        player.sendMessage(Component.text("You have been given a polar wand", NamedTextColor.AQUA));

        return Command.SINGLE_SUCCESS;
    }

}
