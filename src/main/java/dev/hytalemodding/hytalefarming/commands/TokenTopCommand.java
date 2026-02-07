package dev.hytalemodding.hytalefarming.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import dev.hytalemodding.hytalefarming.HytaleFarmingPlugin;
import dev.hytalemodding.hytalefarming.service.TokenService;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TokenTopCommand extends AbstractCommand {

    private final HytaleFarmingPlugin plugin;

    public TokenTopCommand(HytaleFarmingPlugin plugin) {
        super("tokenstop", "Show top token balances");
        this.plugin = plugin;
    }

    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        List<TokenService.LedgerEntry> top = plugin.getTokenService().top(10);
        context.sendMessage(Message.raw("Top " + plugin.getTokensConfig().getCurrencyName() + " holders:"));
        for (int i = 0; i < top.size(); i++) {
            TokenService.LedgerEntry e = top.get(i);
            context.sendMessage(Message.raw("#" + (i + 1) + " " + e.name() + " - " + e.balance()));
        }
        return CompletableFuture.completedFuture(null);
    }
}
