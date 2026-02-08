package dev.hytalemodding.hytalefarming.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
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

public class TokensGiveCommand extends AbstractPlayerCommand {

    private final HytaleFarmingPlugin plugin;
    private final RequiredArg<PlayerRef> playerArg;
    private final RequiredArg<Integer> amountArg;

    public TokensGiveCommand(HytaleFarmingPlugin plugin) {
        super("give", "Give tokens to a player");
        this.plugin = plugin;
        this.playerArg = withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);
        this.amountArg = withRequiredArg("amount", "Amount", ArgTypes.INTEGER);
        requirePermission("hytalefarming.tokens.give");
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> senderRef,
                           @Nonnull PlayerRef senderPlayerRef,
                           @Nonnull World world) {
        PlayerRef target = playerArg.get(context);
        Integer amount = amountArg.get(context);
        if (target == null) {
            context.sendMessage(Message.raw("Target player must be online."));
            return;
        }
        if (amount == null || amount <= 0) {
            context.sendMessage(Message.raw("Amount must be a positive integer."));
            return;
        }

        plugin.getTokenService().addTokens(target.getUuid(), target.getUsername(), amount);
        long newBalance = plugin.getTokenService().balance(target.getUuid(), target.getUsername());

        Debug.log("/tokens give sender=" + senderPlayerRef.getUsername()
                + " target=" + target.getUsername()
                + " amount=" + amount
                + " newBalance=" + newBalance);

        context.sendMessage(Message.raw("Gave " + amount + " " + plugin.getTokensConfig().getCurrencyName()
                + " to " + target.getUsername() + ". New balance: " + newBalance));

        Ref<EntityStore> targetRef = target.getReference();
        if (targetRef != null && targetRef.isValid()) {
            Player targetPlayer = store.getComponent(targetRef, Player.getComponentType());
            if (targetPlayer != null) {
                targetPlayer.sendMessage(Message.raw("You received " + amount + " " + plugin.getTokensConfig().getCurrencyName()
                        + " from " + senderPlayerRef.getUsername() + "."));
            }
        }
    }
}
