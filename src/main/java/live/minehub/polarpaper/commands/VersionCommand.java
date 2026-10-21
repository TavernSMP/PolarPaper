package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import live.minehub.polarpaper.PolarPaper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.commands.CommandSourceStack;

public class VersionCommand extends PolarCmd {
    public VersionCommand() {
        super("version", "Display the plugin version");
    }

    @Override
    protected int executeDefault(CommandContext<CommandSourceStack> context) {
        context.getSource().getBukkitSender().sendMessage(Component.text(
                "PolarPaper " + PolarPaper.getPlugin().getPluginMeta().getVersion() + " (Paper 1.20.1, Java 25)", NamedTextColor.AQUA)
                .clickEvent(ClickEvent.openUrl("https://github.com/TavernSMP/PolarPaper/tree/1_20_1")));
        return Command.SINGLE_SUCCESS;
    }

    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {
    }
}
