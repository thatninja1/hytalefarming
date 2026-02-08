package dev.hytalemodding.hytalefarming.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.hytalefarming.Debug;
import dev.hytalemodding.hytalefarming.HytaleFarmingPlugin;
import dev.hytalemodding.hytalefarming.config.EnchantsConfig;
import dev.hytalemodding.hytalefarming.service.TokenService;

import javax.annotation.Nonnull;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ThoriumHoeUpgradePage extends CustomUIPage {

    private static final String UI_TEMPLATE = "Custom/ThoriumHoeUpgrade.ui";
    private static final String UI_RESOURCE_PATH = "Common/UI/Custom/ThoriumHoeUpgrade.ui";

    private static final String ACTION_KEY = "action";
    private static final String ACTION_CLOSE = "close";
    private static final String ACTION_UPGRADE_TOKEN_FINDER = "upgrade_token_finder";

    public ThoriumHoeUpgradePage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder,
                      @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());

        boolean hasResourceStreamUi = getClass().getResourceAsStream("/" + UI_RESOURCE_PATH) != null;
        boolean hasClasspathUi = getClass().getClassLoader().getResource(UI_RESOURCE_PATH) != null;
        boolean looksLoadableFromAssetPack = UI_TEMPLATE.startsWith("Custom/") && hasResourceStreamUi;

        Debug.log("[HoeDebug] UI build start: thread=" + Thread.currentThread().getName()
                + " templateDoc=" + UI_TEMPLATE
                + " expectedJarPath=" + UI_RESOURCE_PATH
                + " resourceStreamExists=" + hasResourceStreamUi
                + " classpathExists=" + hasClasspathUi
                + " assetPackLoadableGuess=" + looksLoadableFromAssetPack);

        if (!hasResourceStreamUi || !looksLoadableFromAssetPack) {
            Debug.log("[HoeDebug] UI open failure: document not resolvable; skipping append");
            if (player != null) {
                player.sendMessage(Message.raw("UI asset missing: " + UI_TEMPLATE));
            }
            return;
        }

        String markupError = validateUiMarkupSafely();
        if (markupError != null) {
            Debug.log("[HoeDebug] UI open failure: markup validation failed -> " + markupError);
            if (player != null) {
                player.sendMessage(Message.raw("[HytaleFarming] UI parse failed; check server logs."));
            }
            return;
        }

        try {
            Debug.log("[HoeDebug] append(docOnly) -> " + UI_TEMPLATE);
            uiCommandBuilder.append(UI_TEMPLATE);
            bindAndPopulate(uiCommandBuilder, uiEventBuilder);
            Debug.log("[HoeDebug] UI open success for player=" + playerRef.getUsername());
        } catch (Exception ex) {
            Debug.log("[HoeDebug] UI open failure append failed: " + ex.getMessage());
            if (player != null) {
                player.sendMessage(Message.raw("[HytaleFarming] Failed to open hoe upgrade UI."));
            }
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                String eventData) {
        Debug.log("[HoeDebug] UI click event received: player=" + playerRef.getUsername() + " event=" + eventData);
        if (eventData == null || eventData.isBlank()) {
            return;
        }

        if (eventData.contains(ACTION_CLOSE)) {
            close();
            Debug.log("[HoeDebug] close event handled");
            return;
        }

        if (!eventData.contains(ACTION_UPGRADE_TOKEN_FINDER)) {
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Debug.log("[HoeDebug] upgrade click ignored: Player component is null");
            return;
        }

        TokenService tokenService = HytaleFarmingPlugin.instance().getTokenService();
        EnchantsConfig.TokenFinder tokenFinderCfg = HytaleFarmingPlugin.instance().getEnchantsConfig().getTokenFinder();

        int currentLevel = tokenService.enchantLevel(playerRef.getUuid(), playerRef.getUsername(), "token_finder");
        int maxLevel = tokenFinderCfg.getMaxLevel();
        int cost = tokenFinderCfg.getUpgradeCost(currentLevel);
        long balanceBefore = tokenService.balance(playerRef.getUuid(), playerRef.getUsername());

        if (currentLevel >= maxLevel) {
            Debug.log("[HoeDebug] upgrade failed: reason=maxed level=" + currentLevel + " max=" + maxLevel);
            player.sendMessage(Message.raw("Token Finder is already max level."));
            refreshUi();
            return;
        }

        if (balanceBefore < cost) {
            Debug.log("[HoeDebug] upgrade failed: reason=insufficient balance=" + balanceBefore + " cost=" + cost);
            player.sendMessage(Message.raw("Not enough Tokens. Need " + cost + "."));
            refreshUi();
            return;
        }

        boolean upgraded = tokenService.tryUpgradeTokenFinder(playerRef.getUuid(), playerRef.getUsername());
        if (!upgraded) {
            Debug.log("[HoeDebug] upgrade failed: reason=serviceReturnedFalse");
            player.sendMessage(Message.raw("Upgrade failed. Please try again."));
            refreshUi();
            return;
        }

        int newLevel = tokenService.enchantLevel(playerRef.getUuid(), playerRef.getUsername(), "token_finder");
        long balanceAfter = tokenService.balance(playerRef.getUuid(), playerRef.getUsername());
        Debug.log("[HoeDebug] upgrade success: cost=" + cost
                + " balanceBefore=" + balanceBefore
                + " balanceAfter=" + balanceAfter
                + " levelBefore=" + currentLevel
                + " levelAfter=" + newLevel
                + " persisted=true");
        player.sendMessage(Message.raw("Token Finder upgraded to level " + newLevel + "."));
        refreshUi();
    }

    private void bindAndPopulate(UICommandBuilder uiCommandBuilder,
                                 UIEventBuilder uiEventBuilder) {
        TokenService tokenService = HytaleFarmingPlugin.instance().getTokenService();
        EnchantsConfig.TokenFinder tokenFinderCfg = HytaleFarmingPlugin.instance().getEnchantsConfig().getTokenFinder();

        long balance = tokenService.balance(playerRef.getUuid(), playerRef.getUsername());
        int level = tokenService.enchantLevel(playerRef.getUuid(), playerRef.getUsername(), "token_finder");
        int maxLevel = tokenFinderCfg.getMaxLevel();
        int cost = tokenFinderCfg.getUpgradeCost(level);

        uiCommandBuilder.set("#SubtitleLabel.Text", "Upgrade your Thorium Hoe");
        uiCommandBuilder.set("#TokenBalanceLabel.Text", "Tokens: " + balance);
        uiCommandBuilder.set("#TokenFinderLevelLabel.Text", "Level: " + level + " / " + maxLevel);
        uiCommandBuilder.set("#TokenFinderCostLabel.Text", level >= maxLevel ? "Cost: N/A" : "Cost: " + cost + " Tokens");
        uiCommandBuilder.set("#TokenFinderUpgradeButtonLabel.Text", level >= maxLevel ? "MAX" : "Upgrade");

        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of(ACTION_KEY, ACTION_CLOSE));
        Debug.log("[HoeDebug] bound UI event Activating -> #CloseButton");

        if (level < maxLevel) {
            uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TokenFinderUpgradeButton", EventData.of(ACTION_KEY, ACTION_UPGRADE_TOKEN_FINDER));
            Debug.log("[HoeDebug] bound UI event Activating -> #TokenFinderUpgradeButton");
        } else {
            Debug.log("[HoeDebug] token finder at MAX; no upgrade binding added");
        }
    }

    private String validateUiMarkupSafely() {
        try (InputStream is = getClass().getResourceAsStream("/" + UI_RESOURCE_PATH)) {
            if (is == null) {
                return "resource stream is null";
            }
            String uiText = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            String[] lines = uiText.split("\\R");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.startsWith("Button #")) {
                    for (int j = i + 1; j < lines.length; j++) {
                        String inner = lines[j].trim();
                        if (inner.startsWith("}")) {
                            break;
                        }
                        if (inner.startsWith("Text:")) {
                            return "unsupported Button.Text field detected at line " + (j + 1);
                        }
                    }
                }
            }

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.startsWith("Group #")) {
                    for (int j = i + 1; j < lines.length; j++) {
                        String inner = lines[j].trim();
                        if (inner.startsWith("}")) {
                            break;
                        }
                        if (inner.startsWith("Style:")) {
                            return "unsupported Group.Style field detected at line " + (j + 1);
                        }
                    }
                }
                if (line.contains("Style:") && line.contains("Color:")) {
                    return "unsupported LabelStyle.Color field detected at line " + (i + 1) + "; use TextColor";
                }
            }
            return null;
        } catch (Exception ex) {
            return "validation exception: " + ex.getMessage();
        }
    }

    private void refreshUi() {
        try {
            rebuild();
            Debug.log("[HoeDebug] UI refreshed after upgrade interaction");
        } catch (Exception ex) {
            Debug.log("[HoeDebug] UI refresh failed: " + ex.getMessage());
        }
    }
}
