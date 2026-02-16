package dev.hytalemodding.hytalefarming.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import dev.hytalemodding.hytalefarming.HytaleFarmingPlugin;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class FarmingReloadCommand extends AbstractAsyncCommand {

    private final HytaleFarmingPlugin plugin;

    public FarmingReloadCommand(HytaleFarmingPlugin plugin) {
        super("reload", "Reload HytaleFarming JSON configs from disk");
        this.plugin = plugin;
        requirePermission("hytalefarming.farming.reload");
    }

    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext context) {
        String caller = context.sender() == null ? "<unknown>" : context.sender().getDisplayName();
        String report = plugin.reloadAllConfigs(caller);
        context.sendMessage(Message.raw(report));
        return CompletableFuture.completedFuture(null);
    }
}
