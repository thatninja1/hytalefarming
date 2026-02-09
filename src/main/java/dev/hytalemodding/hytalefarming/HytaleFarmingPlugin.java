package dev.hytalemodding.hytalefarming;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.hytalefarming.commands.TokenTopCommand;
import dev.hytalemodding.hytalefarming.commands.TokensCommandCollection;
import dev.hytalemodding.hytalefarming.config.EnchantsConfig;
import dev.hytalemodding.hytalefarming.config.TokensConfig;
import dev.hytalemodding.hytalefarming.config.UiConfig;
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
    private UiConfig uiConfig;
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
        this.uiConfig = UiConfig.load(dataDirectory.resolve("config.json"));

        Debug.configure(tokensConfig.isDebug(), getLogger());
        Debug.log("setup() start");
        Debug.log("config loaded: currencyName=" + tokensConfig.getCurrencyName()
                + ", tokensTimes=" + tokensConfig.getTokensTimes()
                + ", debug=" + tokensConfig.isDebug());
        Debug.log("ui config loaded: title=\"" + uiConfig.getUiTitle() + "\" subtitle=\"" + uiConfig.getUiSubtitle() + "\"");

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


        boolean uiAssetStreamPresent = getClass().getResourceAsStream("/Common/UI/Custom/Pages/HytaleFarming/ThoriumHoeUpgrade.ui") != null;
        boolean uiAssetClasspathPresent = getClass().getClassLoader().getResource("Common/UI/Custom/Pages/HytaleFarming/ThoriumHoeUpgrade.ui") != null;
        Debug.log("[HoeDebug] startup UI asset check resourceStream(/Common/UI/Custom/Pages/HytaleFarming/ThoriumHoeUpgrade.ui)="
                + uiAssetStreamPresent + " classLoader(Common/UI/Custom/Pages/HytaleFarming/ThoriumHoeUpgrade.ui)=" + uiAssetClasspathPresent);

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

    public void openUpgradeUiSafe(PlayerRef playerRef, World world, String interactionType, String heldItemId) {
        try {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                Debug.log("[HoeDebug] openUpgradeUiSafe aborted: invalid player ref for " + playerRef.getUsername());
                return;
            }

            World targetWorld = world;
            if (targetWorld == null) {
                Store<EntityStore> store = ref.getStore();
                EntityStore entityStore = store.getExternalData();
                targetWorld = entityStore.getWorld();
            }

            String currentThread = Thread.currentThread().getName();
            Debug.log("[HoeDebug] openUpgradeUiSafe called on thread=" + currentThread
                    + " targetWorld=" + targetWorld.getName()
                    + " worldThreadActive=" + targetWorld.isInThread());

            Runnable openTask = () -> openUpgradeUiInternal(playerRef, interactionType, heldItemId);
            if (targetWorld.isInThread()) {
                openTask.run();
            } else {
                targetWorld.execute(openTask);
                Debug.log("[HoeDebug] scheduled UI open to WorldThread=" + targetWorld.getName());
            }
        } catch (Exception ex) {
            Debug.log("[HoeDebug] openUpgradeUiSafe failed before scheduling: " + ex.getMessage());
        }
    }

    private void openUpgradeUiInternal(PlayerRef playerRef, String interactionType, String heldItemId) {
        String threadName = Thread.currentThread().getName();
        Debug.log("[HoeDebug] openUpgradeUiInternal on thread=" + threadName + " interactionType=" + interactionType + " heldItemId=" + heldItemId);

        try {
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                Debug.log("[HoeDebug] Cannot open UI; invalid player ref in internal for " + playerRef.getUsername());
                return;
            }

            Store<EntityStore> store = ref.getStore();
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                Debug.log("[HoeDebug] Cannot open UI; Player component null for " + playerRef.getUsername());
                return;
            }

            long tokenBalance = tokenService.balance(playerRef.getUuid(), playerRef.getUsername());
            int tokenFinderLevel = tokenService.enchantLevel(playerRef.getUuid(), playerRef.getUsername(), "token_finder");
            Debug.log("[HoeDebug] tokenBalance=" + tokenBalance + " tokenFinderLevel=" + tokenFinderLevel);

            player.sendMessage(Message.raw("[HoeDebug] Thorium hoe detected -> opening UI"));
            player.getPageManager().openCustomPage(ref, store, new ThoriumHoeUpgradePage(playerRef));
            Debug.log("[HoeDebug] UI open invoked successfully for player=" + playerRef.getUsername());
        } catch (Exception ex) {
            Debug.log("[HoeDebug] UI open failed for player=" + playerRef.getUsername() + " reason=" + ex.getMessage());
            try {
                Ref<EntityStore> ref = playerRef.getReference();
                if (ref != null && ref.isValid()) {
                    Store<EntityStore> store = ref.getStore();
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player != null) {
                        player.sendMessage(Message.raw("[HytaleFarming] UI missing or failed to open"));
                    }
                }
            } catch (Exception ignored) {
                Debug.log("[HoeDebug] failed to notify player about UI failure: " + ignored.getMessage());
            }
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

    public UiConfig getUiConfig() {
        return uiConfig;
    }

    public TokenService getTokenService() {
        return tokenService;
    }
}
