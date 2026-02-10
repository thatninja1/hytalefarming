package dev.hytalemodding.hytalefarming.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import dev.hytalemodding.hytalefarming.HytaleFarmingPlugin;

public class FarmingCommandCollection extends AbstractCommandCollection {

    public FarmingCommandCollection(HytaleFarmingPlugin plugin) {
        super("farming", "HytaleFarming plugin commands");
        addSubCommand(new FarmingReloadCommand(plugin));
    }
}
