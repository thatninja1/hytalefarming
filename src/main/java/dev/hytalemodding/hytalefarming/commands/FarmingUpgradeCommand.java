package dev.hytalemodding.hytalefarming.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.hytalefarming.HytaleFarmingPlugin;

import javax.annotation.Nonnull;

public class FarmingUpgradeCommand extends AbstractPlayerCommand {

    private final HytaleFarmingPlugin plugin;

    public FarmingUpgradeCommand(HytaleFarmingPlugin plugin) {
        super("upgrade", "Open the farming tool upgrade UI");
        this.plugin = plugin;
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> senderRef,
                           @Nonnull PlayerRef senderPlayerRef,
                           @Nonnull World world) {
        plugin.openUpgradeUiSafe(senderPlayerRef, world, "CommandUpgrade", "<command>");
        context.sendMessage(Message.raw("Opened farming upgrade UI."));
    }
}
