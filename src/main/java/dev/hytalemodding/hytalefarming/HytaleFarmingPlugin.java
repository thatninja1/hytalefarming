package dev.hytalemodding.hytalefarming;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.hytalefarming.commands.TokenTopCommand;
import dev.hytalemodding.hytalefarming.commands.TokensCommandCollection;
import dev.hytalemodding.hytalefarming.config.EnchantsConfig;
import dev.hytalemodding.hytalefarming.config.TokensConfig;
import dev.hytalemodding.hytalefarming.events.TokenFinderBreakBlockSystem;
import dev.hytalemodding.hytalefarming.interaction.HoeUpgradeMenuInteraction;
import dev.hytalemodding.hytalefarming.service.TokenService;
import dev.hytalemodding.hytalefarming.ui.ThoriumHoeUpgradePage;

import javax.annotation.Nonnull;
import java.nio.file.Path;

public class HytaleFarmingPlugin extends JavaPlugin {
    private static HytaleFarmingPlugin instance;

    private TokensConfig tokensConfig;
    private EnchantsConfig enchantsConfig;
    private TokenService tokenService;
    private InputPacketHook inputPacketHook;

    public HytaleFarmingPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        Path dataDirectory = getDataDirectory();

        this.tokensConfig = TokensConfig.load(dataDirectory.resolve("tokens.json"));
        this.enchantsConfig = EnchantsConfig.load(dataDirectory.resolve("enchants.json"));

        Debug.configure(tokensConfig.isDebug(), getLogger());
        Debug.log("setup() start");
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

        this.inputPacketHook = new InputPacketHook(this);
        this.inputPacketHook.register();

        Debug.log("setup() end");
    }

    @Override
    protected void start() {
        getLogger().atInfo().log("HytaleFarming plugin started.");
        Debug.log("start() called");
    }

    @Override
    protected void shutdown() {
        if (inputPacketHook != null) {
            inputPacketHook.unregister();
        }
        Debug.log("shutdown() called");
        getLogger().atInfo().log("HytaleFarming plugin shutdown.");
    }

    public String resolveHeldItemId(PlayerRef playerRef) {
        try {
            Player player = playerRef.getComponent(Player.getComponentType());
            if (player == null || player.getInventory() == null || player.getInventory().getItemInHand() == null) {
                return "<none>";
            }
            return player.getInventory().getItemInHand().getItemId();
        } catch (Exception ex) {
            Debug.log("[HoeDebug] failed resolving held item from server state: " + ex.getMessage());
            return "<none>";
        }
    }

    public void openUpgradeUiFromPacket(PlayerRef playerRef, String interactionType) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            Debug.log("[HoeDebug] Cannot open UI; invalid player ref for " + playerRef.getUsername());
            return;
        }

        Store<EntityStore> store = ref.getStore();
        Player player = playerRef.getComponent(Player.getComponentType());
        if (player == null) {
            Debug.log("[HoeDebug] Cannot open UI; Player component null for " + playerRef.getUsername());
            return;
        }

        Debug.log("[HoeDebug] Detected " + interactionType + " right-click with Tool_Hoe_Thorium for player=" + playerRef.getUsername());
        player.sendMessage(Message.raw("[HoeDebug] Thorium hoe detected -> opening UI"));

        try {
            player.getPageManager().openCustomPage(ref, store, new ThoriumHoeUpgradePage(playerRef));
            Debug.log("[HoeDebug] UI open invoked successfully for player=" + playerRef.getUsername());
        } catch (Exception ex) {
            Debug.log("[HoeDebug] UI open failed for player=" + playerRef.getUsername() + " reason=" + ex.getMessage());
        }
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
