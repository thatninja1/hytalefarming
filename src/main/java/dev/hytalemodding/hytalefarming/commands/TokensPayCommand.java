package dev.hytalemodding.hytalefarming.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.command.system.ArgTypes;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.RequiredArg;
import com.hypixel.hytale.server.core.entity.EntityStore;
import com.hypixel.hytale.server.core.entity.component.store.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.ref.PlayerRef;
import com.hypixel.hytale.server.core.entity.ref.Ref;
import com.hypixel.hytale.server.core.world.World;
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
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> senderRef, @Nonnull PlayerRef senderPlayerRef, @Nonnull World world) {
        int amount = amountArg.get(context);
        if (amount <= 0) {
            context.sendMessage(Message.raw("Amount must be > 0"));
            return;
        }

        Ref<EntityStore> targetRef = playerArg.get(context).ref();
        Player sender = store.getComponent(senderRef, Player.getComponentType());
        Player target = targetRef == null ? null : store.getComponent(targetRef, Player.getComponentType());

        if (sender == null || target == null) {
            context.sendMessage(Message.raw("Could not resolve players."));
            return;
        }

        if (!plugin.getTokenService().transfer(store, senderRef, sender, targetRef, target, amount)) {
            context.sendMessage(Message.raw("Not enough " + plugin.getTokensConfig().getCurrencyName() + "."));
            return;
        }

        context.sendMessage(Message.raw("Paid " + amount + " " + plugin.getTokensConfig().getCurrencyName() + " to " + target.getDisplayName()));
        target.sendMessage(Message.raw("You received " + amount + " " + plugin.getTokensConfig().getCurrencyName() + " from " + sender.getDisplayName()));
    }
}
