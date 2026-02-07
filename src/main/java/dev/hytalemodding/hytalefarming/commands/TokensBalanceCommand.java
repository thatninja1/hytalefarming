package dev.hytalemodding.hytalefarming.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.hytalefarming.Debug;
import dev.hytalemodding.hytalefarming.HytaleFarmingPlugin;

import javax.annotation.Nonnull;
import java.util.UUID;

public class TokensBalanceCommand extends AbstractPlayerCommand {

    private final HytaleFarmingPlugin plugin;
    private final OptionalArg<PlayerRef> playerArg;

    public TokensBalanceCommand(HytaleFarmingPlugin plugin) {
        super("bal", "Shows token balance");
        this.plugin = plugin;
        this.playerArg = withOptionalArg("player", "Target player", ArgTypes.PLAYER_REF);
        addUsageVariant(new PositionalTargetVariant(plugin));
        addAliases("tbal");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> senderRef,
                           @Nonnull PlayerRef senderPlayerRef,
                           @Nonnull World world) {
        PlayerRef targetRef = playerArg.get(context);
        if (targetRef != null) {
            UUID uuid = targetRef.getUuid();
            String username = targetRef.getUsername();
            long bal = plugin.getTokenService().balance(uuid, username);
            Debug.log("/tokens bal --player executed sender=" + senderPlayerRef.getUsername() + " target=" + username + " balance=" + bal);
            context.sendMessage(Message.raw(username + " " + plugin.getTokensConfig().getCurrencyName() + ": " + bal));
            return;
        }

        long bal = plugin.getTokenService().balance(senderPlayerRef.getUuid(), senderPlayerRef.getUsername());
        Debug.log("/tokens bal executed sender=" + senderPlayerRef.getUsername() + " balance=" + bal);
        context.sendMessage(Message.raw(plugin.getTokensConfig().getCurrencyName() + ": " + bal));
    }

    private static class PositionalTargetVariant extends AbstractPlayerCommand {
        private final HytaleFarmingPlugin plugin;
        private final RequiredArg<PlayerRef> targetArg;

        private PositionalTargetVariant(HytaleFarmingPlugin plugin) {
            super("Shows target token balance");
            this.plugin = plugin;
            this.targetArg = withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);
        }

        @Override
        protected void execute(@Nonnull CommandContext context,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> senderRef,
                               @Nonnull PlayerRef senderPlayerRef,
                               @Nonnull World world) {
            PlayerRef target = targetArg.get(context);
            long bal = plugin.getTokenService().balance(target.getUuid(), target.getUsername());
            Debug.log("/tokens bal <player> executed sender=" + senderPlayerRef.getUsername() + " target=" + target.getUsername() + " balance=" + bal);
            context.sendMessage(Message.raw(target.getUsername() + " " + plugin.getTokensConfig().getCurrencyName() + ": " + bal));
        }
    }
}
