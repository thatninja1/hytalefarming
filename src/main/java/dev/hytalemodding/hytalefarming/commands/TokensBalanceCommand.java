package dev.hytalemodding.hytalefarming.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.AbstractTargetPlayerCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.EntityStore;
import com.hypixel.hytale.server.core.entity.component.store.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.ref.PlayerRef;
import com.hypixel.hytale.server.core.entity.ref.Ref;
import com.hypixel.hytale.server.core.world.World;
import dev.hytalemodding.hytalefarming.HytaleFarmingPlugin;

import javax.annotation.Nonnull;

public class TokensBalanceCommand extends AbstractPlayerCommand {

    private final HytaleFarmingPlugin plugin;

    public TokensBalanceCommand(HytaleFarmingPlugin plugin) {
        super("bal", "Show token balance");
        this.plugin = plugin;
        addUsageVariant(new Target(plugin));
    }

    @Override
    protected void execute(@Nonnull CommandContext commandContext, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        long balance = plugin.getTokenService().balance(store, ref);
        commandContext.sendMessage(Message.raw(plugin.getTokensConfig().getCurrencyName() + ": " + balance));
    }

    private static class Target extends AbstractTargetPlayerCommand {

        private final HytaleFarmingPlugin plugin;

        private Target(HytaleFarmingPlugin plugin) {
            super("Show target balance");
            this.plugin = plugin;
        }

        @Override
        protected void execute(@Nonnull CommandContext context, Ref<EntityStore> targetRef, @Nonnull Ref<EntityStore> senderRef, @Nonnull PlayerRef senderPlayerRef, @Nonnull World world, @Nonnull Store<EntityStore> store) {
            if (targetRef == null) {
                context.sendMessage(Message.raw("Target not found."));
                return;
            }
            Player target = store.getComponent(targetRef, Player.getComponentType());
            if (target == null) {
                context.sendMessage(Message.raw("Target not found."));
                return;
            }
            long balance = plugin.getTokenService().balance(store, targetRef);
            context.sendMessage(Message.raw(target.getDisplayName() + " " + plugin.getTokensConfig().getCurrencyName() + ": " + balance));
        }
    }
}
