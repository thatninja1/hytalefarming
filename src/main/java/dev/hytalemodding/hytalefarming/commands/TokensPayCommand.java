package dev.hytalemodding.hytalefarming.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.hytalefarming.Debug;
import dev.hytalemodding.hytalefarming.HytaleFarmingPlugin;

import javax.annotation.Nonnull;

public class TokensPayCommand extends AbstractPlayerCommand {

    private final HytaleFarmingPlugin plugin;
    private final RequiredArg<PlayerRef> playerArg;
    private final RequiredArg<Integer> amountArg;

    public TokensPayCommand(HytaleFarmingPlugin plugin) {
        super("pay", "Transfer tokens to another player");
        this.plugin = plugin;
        this.playerArg = withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);
        this.amountArg = withRequiredArg("amount", "Amount", ArgTypes.INTEGER);
        addUsageVariant(new FlagVariant(plugin));
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> senderRef,
                           @Nonnull PlayerRef senderPlayerRef,
                           @Nonnull World world) {
        runTransfer(context, senderPlayerRef, playerArg.get(context), amountArg.get(context), "positional");
    }

    private void runTransfer(CommandContext context, PlayerRef sender, PlayerRef target, int amount, String style) {
        Debug.log("/tokens pay " + style + " sender=" + sender.getUsername() + " target=" + target.getUsername() + " amount=" + amount);
        if (amount <= 0) {
            context.sendMessage(Message.raw("Amount must be > 0"));
            return;
        }

        boolean ok = plugin.getTokenService().pay(
                sender.getUuid(), sender.getUsername(),
                target.getUuid(), target.getUsername(),
                amount
        );
        if (!ok) {
            Debug.log("/tokens pay failed (insufficient funds) sender=" + sender.getUsername());
            context.sendMessage(Message.raw("Not enough " + plugin.getTokensConfig().getCurrencyName() + "."));
            return;
        }

        Debug.log("/tokens pay success sender=" + sender.getUsername() + " target=" + target.getUsername() + " amount=" + amount);
        context.sendMessage(Message.raw("Paid " + amount + " " + plugin.getTokensConfig().getCurrencyName() + " to " + target.getUsername()));
    }

    private class FlagVariant extends AbstractPlayerCommand {
        private final OptionalArg<PlayerRef> playerArg;
        private final OptionalArg<Integer> amountArg;

        private FlagVariant(HytaleFarmingPlugin plugin) {
            super("Transfer tokens using --player and --amount");
            this.playerArg = withOptionalArg("player", "Target player", ArgTypes.PLAYER_REF);
            this.amountArg = withOptionalArg("amount", "Amount", ArgTypes.INTEGER);
        }

        @Override
        protected void execute(@Nonnull CommandContext context,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> senderRef,
                               @Nonnull PlayerRef senderPlayerRef,
                               @Nonnull World world) {
            PlayerRef target = playerArg.get(context);
            Integer amount = amountArg.get(context);
            if (target == null || amount == null) {
                context.sendMessage(Message.raw("Usage: /tokens pay --player <player> --amount <amount>"));
                return;
            }
            TokensPayCommand.this.runTransfer(context, senderPlayerRef, target, amount, "flags");
        }
    }
}
