package dev.hytalemodding.hytalefarming;

import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.hytalemodding.hytalefarming.commands.TokenTopCommand;
import dev.hytalemodding.hytalefarming.commands.TokensCommandCollection;
import dev.hytalemodding.hytalefarming.config.EnchantsConfig;
import dev.hytalemodding.hytalefarming.config.TokensConfig;
import dev.hytalemodding.hytalefarming.events.TokenFinderBreakBlockSystem;
import dev.hytalemodding.hytalefarming.interaction.HoeUpgradeMenuInteraction;
import dev.hytalemodding.hytalefarming.service.TokenService;

import javax.annotation.Nonnull;
import java.nio.file.Path;

public class HytaleFarmingPlugin extends JavaPlugin {
    private static HytaleFarmingPlugin instance;

    private TokensConfig tokensConfig;
    private EnchantsConfig enchantsConfig;
    private TokenService tokenService;

    public HytaleFarmingPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        Debug.log("setup() start");
        Path dataDirectory = getDataDirectory();

        this.tokensConfig = TokensConfig.load(dataDirectory.resolve("tokens.json"));
        this.enchantsConfig = EnchantsConfig.load(dataDirectory.resolve("enchants.json"));

        Debug.configure(tokensConfig.isDebug(), getLogger());
        Debug.log("config loaded: currencyName=" + tokensConfig.getCurrencyName()
                + ", tokensTimes=" + tokensConfig.getTokensTimes()
                + ", debug=" + tokensConfig.isDebug());

        this.tokenService = new TokenService(dataDirectory, tokensConfig, enchantsConfig);
        Debug.log("token service initialized");

        Debug.log("Registering commands: /tokens, /tokenstop");
        getCommandRegistry().registerCommand(new TokensCommandCollection(this));
        Debug.log("registered command: /tokens");
        getCommandRegistry().registerCommand(new TokenTopCommand(this));
        Debug.log("registered command: /tokenstop");

        getCodecRegistry(Interaction.CODEC).register(
                "thorium_hoe_upgrade_menu",
                HoeUpgradeMenuInteraction.class,
                HoeUpgradeMenuInteraction.CODEC
        );
        Debug.log("registered interaction codec: thorium_hoe_upgrade_menu");

        getEntityStoreRegistry().registerSystem(new TokenFinderBreakBlockSystem(this));
        Debug.log("Registered systems: TokenFinderBreakBlockSystem");

        Debug.log("setup() end");
    }

    @Override
    protected void start() {
        getLogger().atInfo().log("HytaleFarming plugin started.");
        Debug.log("start() called");
    }

    @Override
    protected void shutdown() {
        Debug.log("shutdown() called");
        getLogger().atInfo().log("HytaleFarming plugin shutdown.");
    }

    public static HytaleFarmingPlugin instance() {
        return instance;
    }

    public TokensConfig getTokensConfig() {
        return tokensConfig;
    }

    public EnchantsConfig getEnchantsConfig() {
        return enchantsConfig;
    }

    public TokenService getTokenService() {
        return tokenService;
    }
}
