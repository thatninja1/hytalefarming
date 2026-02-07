package dev.hytalemodding.hytalefarming;

import com.hypixel.hytale.server.core.entity.EntityStore;
import com.hypixel.hytale.server.core.entity.component.ComponentType;
import com.hypixel.hytale.server.core.interaction.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.hytalemodding.hytalefarming.commands.TokenTopCommand;
import dev.hytalemodding.hytalefarming.commands.TokensCommandCollection;
import dev.hytalemodding.hytalefarming.components.PlayerTokenData;
import dev.hytalemodding.hytalefarming.config.EnchantsConfig;
import dev.hytalemodding.hytalefarming.config.TokensConfig;
import dev.hytalemodding.hytalefarming.events.TokenFinderBreakBlockSystem;
import dev.hytalemodding.hytalefarming.interaction.HoeUpgradeMenuInteraction;
import dev.hytalemodding.hytalefarming.service.TokenService;

import javax.annotation.Nonnull;
import java.nio.file.Path;

public class HytaleFarmingPlugin extends JavaPlugin {

    private static HytaleFarmingPlugin instance;

    private ComponentType<EntityStore, PlayerTokenData> playerTokenDataComponent;
    private TokensConfig tokensConfig;
    private EnchantsConfig enchantsConfig;
    private TokenService tokenService;

    public HytaleFarmingPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        Path dataFolder = this.getDataFolder().toPath();
        this.tokensConfig = TokensConfig.load(dataFolder.resolve("tokens.json"));
        this.enchantsConfig = EnchantsConfig.load(dataFolder.resolve("enchants.json"));

        this.playerTokenDataComponent = this.getEntityStoreRegistry().registerComponent(
                PlayerTokenData.class,
                "PlayerTokenData",
                PlayerTokenData.CODEC
        );

        this.tokenService = new TokenService(this);

        this.getCodecRegistry(Interaction.CODEC).register(
                "thorium_hoe_upgrade_menu",
                HoeUpgradeMenuInteraction.class,
                HoeUpgradeMenuInteraction.CODEC
        );

        this.getEntityStoreRegistry().registerSystem(new TokenFinderBreakBlockSystem(this));

        this.getCommandRegistry().registerCommand(new TokensCommandCollection(this));
        this.getCommandRegistry().registerCommand(new TokenTopCommand(this));
    }

    public static HytaleFarmingPlugin instance() {
        return instance;
    }

    public ComponentType<EntityStore, PlayerTokenData> getPlayerTokenDataComponent() {
        return playerTokenDataComponent;
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
