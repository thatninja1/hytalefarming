package dev.hytalemodding.hytalefarming;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.hytalemodding.hytalefarming.commands.TokenTopCommand;
import dev.hytalemodding.hytalefarming.commands.TokensBalanceCommand;
import dev.hytalemodding.hytalefarming.commands.TokensPayCommand;
import dev.hytalemodding.hytalefarming.config.EnchantsConfig;
import dev.hytalemodding.hytalefarming.config.TokensConfig;
import dev.hytalemodding.hytalefarming.service.TokenService;

import javax.annotation.Nonnull;
import java.nio.file.Path;

public class HytaleFarmingPlugin extends JavaPlugin {
    private TokensConfig tokensConfig;
    private EnchantsConfig enchantsConfig;
    private TokenService tokenService;

    private TokensBalanceCommand balanceCommand;
    private TokensPayCommand payCommand;
    private TokenTopCommand topCommand;

    public HytaleFarmingPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        Path dataDirectory = getDataDirectory();
        this.tokensConfig = TokensConfig.load(dataDirectory.resolve("tokens.json"));
        this.enchantsConfig = EnchantsConfig.load(dataDirectory.resolve("enchants.json"));
        this.tokenService = new TokenService(dataDirectory, tokensConfig, enchantsConfig);

        this.balanceCommand = new TokensBalanceCommand(tokenService, tokensConfig);
        this.payCommand = new TokensPayCommand(tokenService, tokensConfig);
        this.topCommand = new TokenTopCommand(tokenService, tokensConfig);
    }

    @Override
    protected void start() {
        getLogger().atInfo().log("HytaleFarming plugin started.");
    }

    @Override
    protected void shutdown() {
        getLogger().atInfo().log("HytaleFarming plugin shutdown.");
    }

    public TokensBalanceCommand balanceCommand() { return balanceCommand; }
    public TokensPayCommand payCommand() { return payCommand; }
    public TokenTopCommand topCommand() { return topCommand; }
    public TokenService tokenService() { return tokenService; }
}
