package live.minehub.polarpaper.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

public class GCCommand extends PolarCmd {

    public GCCommand() {
        super("gc", "Run a garbage collection");
    }

    @Override
    protected int executeDefault(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getBukkitSender().sendMessage("Running GC...");
        printMemoryStats(ctx);
        System.gc();
        ctx.getSource().getBukkitSender().sendMessage("Ran GC!");
        printMemoryStats(ctx);

        return Command.SINGLE_SUCCESS;
    }

    private static void printMemoryStats(CommandContext<CommandSourceStack> ctx) {
        Runtime runtime = Runtime.getRuntime();
        long memory = runtime.totalMemory() - runtime.freeMemory();

        ctx.getSource().getBukkitSender().sendMessage("Currently using %sMB".formatted(memory / (1024L * 1024L)));
    }

    @Override
    protected void addToBuilder(LiteralArgumentBuilder<CommandSourceStack> builder) {

    }
}
