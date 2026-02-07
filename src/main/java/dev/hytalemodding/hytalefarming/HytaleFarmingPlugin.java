package dev.hytalemodding.hytalefarming;

import dev.hytalemodding.hytalefarming.commands.TokenTopCommand;
import dev.hytalemodding.hytalefarming.commands.TokensBalanceCommand;
import dev.hytalemodding.hytalefarming.commands.TokensPayCommand;
import dev.hytalemodding.hytalefarming.config.EnchantsConfig;
import dev.hytalemodding.hytalefarming.config.TokensConfig;
import dev.hytalemodding.hytalefarming.service.TokenService;

import java.nio.file.Path;

/**
 * SDK-agnostic plugin core so the project compiles with available dependencies.
 * Integrate these services into the active Hytale API layer in a follow-up adapter.
 */
public class HytaleFarmingPlugin {
    private final TokensConfig tokensConfig;
    private final EnchantsConfig enchantsConfig;
    private final TokenService tokenService;

    private final TokensBalanceCommand balanceCommand;
    private final TokensPayCommand payCommand;
    private final TokenTopCommand topCommand;

    public HytaleFarmingPlugin(Path dataDirectory) {
        this.tokensConfig = TokensConfig.load(dataDirectory.resolve("tokens.json"));
        this.enchantsConfig = EnchantsConfig.load(dataDirectory.resolve("enchants.json"));
        this.tokenService = new TokenService(dataDirectory, tokensConfig, enchantsConfig);
        this.balanceCommand = new TokensBalanceCommand(tokenService, tokensConfig);
        this.payCommand = new TokensPayCommand(tokenService, tokensConfig);
        this.topCommand = new TokenTopCommand(tokenService, tokensConfig);
    }

    public TokensBalanceCommand balanceCommand() { return balanceCommand; }
    public TokensPayCommand payCommand() { return payCommand; }
    public TokenTopCommand topCommand() { return topCommand; }
    public TokenService tokenService() { return tokenService; }
}
