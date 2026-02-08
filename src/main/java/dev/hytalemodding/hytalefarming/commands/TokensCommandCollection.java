package dev.hytalemodding.hytalefarming.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import dev.hytalemodding.hytalefarming.HytaleFarmingPlugin;

public class TokensCommandCollection extends AbstractCommandCollection {

    public TokensCommandCollection(HytaleFarmingPlugin plugin) {
        super("tokens", "Token currency commands");
        addSubCommand(new TokensBalanceCommand(plugin));
        addSubCommand(new TokensPayCommand(plugin));
        addSubCommand(new TokensGiveCommand(plugin));
    }
}
