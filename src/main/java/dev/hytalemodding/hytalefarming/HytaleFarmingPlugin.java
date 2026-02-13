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
import dev.hytalemodding.hytalefarming.commands.FarmingCommandCollection;
import dev.hytalemodding.hytalefarming.commands.TokenTopCommand;
import dev.hytalemodding.hytalefarming.commands.TokensCommandCollection;
import dev.hytalemodding.hytalefarming.config.EnchantsConfig;
import dev.hytalemodding.hytalefarming.config.TokensConfig;
import dev.hytalemodding.hytalefarming.config.UiConfig;
import dev.hytalemodding.hytalefarming.events.TokenFinderBreakBlockSystem;
import dev.hytalemodding.hytalefarming.interaction.HoeUpgradeMenuInteraction;
import dev.hytalemodding.hytalefarming.service.TokenService;
import dev.hytalemodding.hytalefarming.ui.ThoriumHoeUpgradePage;
import dev.hytalemodding.hytalefarming.util.MessageFormatter;

import javax.annotation.Nonnull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class HytaleFarmingPlugin extends JavaPlugin {
    private static HytaleFarmingPlugin instance;

    private TokensConfig tokensConfig;
    private EnchantsConfig enchantsConfig;
    private TokenService tokenService;
    private UiConfig uiConfig;
    private InputPacketHook inputPacketHook;
    private TokenFinderBreakBlockSystem tokenFinderBreakBlockSystem;
    private Path dataDirectory;

    public HytaleFarmingPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        this.dataDirectory = getDataDirectory();

        this.tokensConfig = TokensConfig.load(dataDirectory.resolve("tokens.json"));
        this.enchantsConfig = EnchantsConfig.load(dataDirectory.resolve("enchants.json"));
        this.uiConfig = UiConfig.load(dataDirectory.resolve("config.json"));

        Debug.configure(tokensConfig.isDebug() && uiConfig.isDebug(), getLogger());
        Debug.log("setup() start");
        Debug.log("config loaded: currencyName=" + tokensConfig.getCurrencyName()
                + ", tokensTimes=" + tokensConfig.getTokensTimes()
                + ", debug=" + tokensConfig.isDebug());
        Debug.log("ui config loaded: title=\"" + uiConfig.getUiTitle() + "\" subtitle=\"" + uiConfig.getUiSubtitle() + "\"");

        this.tokenService = new TokenService(dataDirectory, tokensConfig, enchantsConfig);
        MessageFormatter.logHexSupportAtStartupIfNeeded(
                enchantsConfig.getTokenFinder().getProcMessage(),
                enchantsConfig.getFortune().getProcMessage(),
                enchantsConfig.getKeyfinder().getProcMessage()
        );
        Debug.log("token service initialized");

        Debug.log("Registering commands: /tokens, /tokenstop, /farming");
        getCommandRegistry().registerCommand(new TokensCommandCollection(this));
        Debug.log("registered command: /tokens");
        getCommandRegistry().registerCommand(new TokenTopCommand(this));
        Debug.log("registered command: /tokenstop");
        getCommandRegistry().registerCommand(new FarmingCommandCollection(this));
        Debug.log("registered command: /farming");

        getCodecRegistry(Interaction.CODEC).register(
                "thorium_hoe_upgrade_menu",
                HoeUpgradeMenuInteraction.class,
                HoeUpgradeMenuInteraction.CODEC
        );
        Debug.log("registered interaction codec: thorium_hoe_upgrade_menu");

        this.tokenFinderBreakBlockSystem = new TokenFinderBreakBlockSystem(this);
        getEntityStoreRegistry().registerSystem(tokenFinderBreakBlockSystem);
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

    public synchronized String reloadAllConfigs(String caller) {
        List<String> loadedFiles = new ArrayList<>();
        List<String> defaultsApplied = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Debug.log("[Reload] /farming reload invoked by caller=" + caller);

        Path tokensPath = dataDirectory.resolve("tokens.json");
        Path enchantsPath = dataDirectory.resolve("enchants.json");
        Path uiPath = dataDirectory.resolve("config.json");

        boolean tokensExistedBefore = Files.exists(tokensPath);
        try {
            TokensConfig newTokensConfig = TokensConfig.load(tokensPath);
            this.tokensConfig = newTokensConfig;
            loadedFiles.add("tokens.json");
            if (!tokensExistedBefore) {
                defaultsApplied.add("tokens.json");
            }
        } catch (Exception ex) {
            errors.add("tokens.json: " + ex.getMessage());
        }

        boolean enchantsExistedBefore = Files.exists(enchantsPath);
        try {
            EnchantsConfig newEnchantsConfig = EnchantsConfig.load(enchantsPath);
            this.enchantsConfig = newEnchantsConfig;
            loadedFiles.add("enchants.json");
            if (!enchantsExistedBefore) {
                defaultsApplied.add("enchants.json");
            }
        } catch (Exception ex) {
            errors.add("enchants.json: " + ex.getMessage());
        }

        boolean uiExistedBefore = Files.exists(uiPath);
        try {
            UiConfig newUiConfig = UiConfig.load(uiPath);
            this.uiConfig = newUiConfig;
            loadedFiles.add("config.json");
            if (!uiExistedBefore) {
                defaultsApplied.add("config.json");
            }
        } catch (Exception ex) {
            errors.add("config.json: " + ex.getMessage());
        }

        Debug.configure(tokensConfig.isDebug() && uiConfig.isDebug(), getLogger());

        this.tokenService = new TokenService(dataDirectory, tokensConfig, enchantsConfig);
        MessageFormatter.logHexSupportAtStartupIfNeeded(
                enchantsConfig.getTokenFinder().getProcMessage(),
                enchantsConfig.getFortune().getProcMessage(),
                enchantsConfig.getKeyfinder().getProcMessage()
        );

        if (tokensConfig.getTokensTimes() <= 0) {
            warnings.add("tokensTimes <= 0 will disable token gains.");
        }

        Debug.log("[Reload] loadedFiles=" + loadedFiles + " defaultsApplied=" + defaultsApplied + " warnings=" + warnings + " errors=" + errors);

        if (errors.isEmpty()) {
            getLogger().atInfo().log("[HytaleFarming] /farming reload success loaded=" + loadedFiles + " defaultsApplied=" + defaultsApplied);
            return "Reload successful. loaded=" + loadedFiles + " defaultsApplied=" + defaultsApplied + (warnings.isEmpty() ? "" : " warnings=" + warnings);
        }

        getLogger().atSevere().log("[HytaleFarming] /farming reload completed with errors. loaded=" + loadedFiles + " defaultsApplied=" + defaultsApplied + " errors=" + errors);
        return "Reload completed with errors. loaded=" + loadedFiles + " defaultsApplied=" + defaultsApplied + " errors=" + errors + (warnings.isEmpty() ? "" : " warnings=" + warnings);
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

    public TokenFinderBreakBlockSystem getTokenFinderBreakBlockSystem() {
        return tokenFinderBreakBlockSystem;
    }
}
