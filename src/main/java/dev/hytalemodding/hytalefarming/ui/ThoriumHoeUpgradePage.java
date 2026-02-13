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

    private static final String UI_TEMPLATE = "Pages/HytaleFarming/ThoriumHoeUpgrade.ui";
    private static final String UI_RESOURCE_PATH = "Common/UI/Custom/Pages/HytaleFarming/ThoriumHoeUpgrade.ui";

    private static final String ACTION_KEY = "action";
    private static final String ACTION_CLOSE = "close";
    private static final String ACTION_UPGRADE_TOKEN_FINDER = "upgrade_token_finder";
    private static final String ACTION_UPGRADE_FORTUNE = "upgrade_fortune";
    private static final String ACTION_UPGRADE_KEYFINDER = "upgrade_keyfinder";

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
        boolean looksLoadableFromAssetPack = UI_TEMPLATE.startsWith("Pages/") && hasResourceStreamUi;

        Debug.log("[FarmingDebug] UI build start: thread=" + Thread.currentThread().getName()
                + " templateDoc=" + UI_TEMPLATE
                + " expectedJarPath=" + UI_RESOURCE_PATH
                + " resourceStreamExists=" + hasResourceStreamUi
                + " classpathExists=" + hasClasspathUi
                + " assetPackLoadableGuess=" + looksLoadableFromAssetPack);

        if (!hasResourceStreamUi || !looksLoadableFromAssetPack) {
            Debug.log("[FarmingDebug] UI open failure: document not resolvable; skipping append");
            if (player != null) {
                player.sendMessage(Message.raw("UI asset missing: " + UI_TEMPLATE));
            }
            return;
        }

        String markupError = validateUiMarkupSafely();
        if (markupError != null) {
            Debug.log("[FarmingDebug] UI open failure: markup validation failed -> " + markupError);
            if (player != null) {
                player.sendMessage(Message.raw("[HytaleFarming] UI parse failed; check server logs."));
            }
            return;
        }

        try {
            Debug.log("[FarmingDebug] append(docOnly) -> " + UI_TEMPLATE);
            uiCommandBuilder.append(UI_TEMPLATE);
            bindAndPopulate(uiCommandBuilder, uiEventBuilder);
            Debug.log("[FarmingDebug] UI open success for player=" + playerRef.getUsername());
        } catch (Exception ex) {
            Debug.log("[FarmingDebug] UI open failure append failed: " + ex.getMessage());
            if (player != null) {
                player.sendMessage(Message.raw("[HytaleFarming] Failed to open farming tool upgrade UI."));
            }
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                String eventData) {
        Debug.log("[FarmingDebug] UI click event received: player=" + playerRef.getUsername() + " event=" + eventData);
        if (eventData == null || eventData.isBlank()) {
            return;
        }

        if (eventData.contains(ACTION_CLOSE)) {
            close();
            Debug.log("[FarmingDebug] close event handled");
            return;
        }

        boolean tokenFinderClicked = eventData.contains(ACTION_UPGRADE_TOKEN_FINDER);
        boolean fortuneClicked = eventData.contains(ACTION_UPGRADE_FORTUNE);
        boolean keyfinderClicked = eventData.contains(ACTION_UPGRADE_KEYFINDER);
        if (!tokenFinderClicked && !fortuneClicked && !keyfinderClicked) {
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            Debug.log("[FarmingDebug] upgrade click ignored: Player component is null");
            return;
        }

        TokenService tokenService = HytaleFarmingPlugin.instance().getTokenService();

        String enchantKey;
        String enchantName;
        int maxLevel;
        int cost;
        if (tokenFinderClicked) {
            enchantKey = "token_finder";
            enchantName = "Token Finder";
            maxLevel = HytaleFarmingPlugin.instance().getEnchantsConfig().getTokenFinder().getMaxLevel();
            cost = HytaleFarmingPlugin.instance().getEnchantsConfig().getTokenFinder().getUpgradeCost(tokenService.enchantLevel(playerRef.getUuid(), playerRef.getUsername(), enchantKey));
        } else if (fortuneClicked) {
            enchantKey = "fortune";
            enchantName = "Fortune";
            maxLevel = HytaleFarmingPlugin.instance().getEnchantsConfig().getFortune().getMaxLevel();
            cost = HytaleFarmingPlugin.instance().getEnchantsConfig().getFortune().getUpgradeCost(tokenService.enchantLevel(playerRef.getUuid(), playerRef.getUsername(), enchantKey));
        } else {
            enchantKey = "keyfinder";
            enchantName = "Keyfinder";
            maxLevel = HytaleFarmingPlugin.instance().getEnchantsConfig().getKeyfinder().getMaxLevel();
            cost = HytaleFarmingPlugin.instance().getEnchantsConfig().getKeyfinder().getUpgradeCost(tokenService.enchantLevel(playerRef.getUuid(), playerRef.getUsername(), enchantKey));
        }
        int currentLevel = tokenService.enchantLevel(playerRef.getUuid(), playerRef.getUsername(), enchantKey);
        long balanceBefore = tokenService.balance(playerRef.getUuid(), playerRef.getUsername());

        if (currentLevel >= maxLevel) {
            Debug.log("[FarmingDebug] upgrade failed: reason=maxed enchant=" + enchantKey + " level=" + currentLevel + " max=" + maxLevel);
            player.sendMessage(Message.raw(enchantName + " is already max level."));
            refreshUi();
            return;
        }

        if (balanceBefore < cost) {
            Debug.log("[FarmingDebug] upgrade failed: reason=insufficient enchant=" + enchantKey + " balance=" + balanceBefore + " cost=" + cost);
            player.sendMessage(Message.raw("Not enough Tokens. Need " + cost + "."));
            refreshUi();
            return;
        }

        boolean upgraded;
        if (tokenFinderClicked) {
            upgraded = tokenService.tryUpgradeTokenFinder(playerRef.getUuid(), playerRef.getUsername());
        } else if (fortuneClicked) {
            upgraded = tokenService.tryUpgradeFortune(playerRef.getUuid(), playerRef.getUsername());
        } else {
            upgraded = tokenService.tryUpgradeKeyfinder(playerRef.getUuid(), playerRef.getUsername());
        }
        if (!upgraded) {
            Debug.log("[FarmingDebug] upgrade failed: reason=serviceReturnedFalse enchant=" + enchantKey);
            player.sendMessage(Message.raw("Upgrade failed. Please try again."));
            refreshUi();
            return;
        }

        int newLevel = tokenService.enchantLevel(playerRef.getUuid(), playerRef.getUsername(), enchantKey);
        long balanceAfter = tokenService.balance(playerRef.getUuid(), playerRef.getUsername());
        Debug.log("[FarmingDebug] upgrade success: enchant=" + enchantKey
                + " cost=" + cost
                + " balanceBefore=" + balanceBefore
                + " balanceAfter=" + balanceAfter
                + " levelBefore=" + currentLevel
                + " levelAfter=" + newLevel
                + " persisted=true");
        player.sendMessage(Message.raw(enchantName + " upgraded to level " + newLevel + "."));
        refreshUi();
    }

    private void bindAndPopulate(UICommandBuilder uiCommandBuilder,
                                 UIEventBuilder uiEventBuilder) {
        TokenService tokenService = HytaleFarmingPlugin.instance().getTokenService();
        EnchantsConfig.TokenFinder tokenFinderCfg = HytaleFarmingPlugin.instance().getEnchantsConfig().getTokenFinder();
        EnchantsConfig.Fortune fortuneCfg = HytaleFarmingPlugin.instance().getEnchantsConfig().getFortune();
        EnchantsConfig.Keyfinder keyfinderCfg = HytaleFarmingPlugin.instance().getEnchantsConfig().getKeyfinder();

        long balance = tokenService.balance(playerRef.getUuid(), playerRef.getUsername());
        int tokenFinderLevel = tokenService.enchantLevel(playerRef.getUuid(), playerRef.getUsername(), "token_finder");
        int tokenFinderMaxLevel = tokenFinderCfg.getMaxLevel();
        int tokenFinderCost = tokenFinderCfg.getUpgradeCost(tokenFinderLevel);

        int fortuneLevel = tokenService.enchantLevel(playerRef.getUuid(), playerRef.getUsername(), "fortune");
        int fortuneMaxLevel = fortuneCfg.getMaxLevel();
        int fortuneCost = fortuneCfg.getUpgradeCost(fortuneLevel);

        int keyfinderLevel = tokenService.enchantLevel(playerRef.getUuid(), playerRef.getUsername(), "keyfinder");
        int keyfinderMaxLevel = keyfinderCfg.getMaxLevel();
        int keyfinderCost = keyfinderCfg.getUpgradeCost(keyfinderLevel);

        uiCommandBuilder.set("#TitleLabel.Text", HytaleFarmingPlugin.instance().getUiConfig().getUiTitle());
        uiCommandBuilder.set("#SubtitleLabel.Text", HytaleFarmingPlugin.instance().getUiConfig().getUiSubtitle());
        uiCommandBuilder.set("#TokenBalanceLabel.Text", "Tokens: " + balance);
        uiCommandBuilder.set("#TokenFinderLevelLabel.Text", "Level: " + tokenFinderLevel + " / " + tokenFinderMaxLevel);
        uiCommandBuilder.set("#TokenFinderCostLabel.Text", tokenFinderLevel >= tokenFinderMaxLevel ? "Cost: N/A" : "Cost: " + tokenFinderCost + " Tokens");
        uiCommandBuilder.set("#TokenFinderUpgradeButtonLabel.Text", tokenFinderLevel >= tokenFinderMaxLevel ? "MAX" : "Upgrade");
        uiCommandBuilder.set("#FortuneLevelLabel.Text", "Level: " + fortuneLevel + " / " + fortuneMaxLevel);
        uiCommandBuilder.set("#FortuneCostLabel.Text", fortuneLevel >= fortuneMaxLevel ? "Cost: N/A" : "Cost: " + fortuneCost + " Tokens");
        uiCommandBuilder.set("#FortuneUpgradeButtonLabel.Text", fortuneLevel >= fortuneMaxLevel ? "MAX" : "Upgrade");
        uiCommandBuilder.set("#KeyfinderLevelLabel.Text", "Level: " + keyfinderLevel + " / " + keyfinderMaxLevel);
        uiCommandBuilder.set("#KeyfinderCostLabel.Text", keyfinderLevel >= keyfinderMaxLevel ? "Cost: N/A" : "Cost: " + keyfinderCost + " Tokens");
        uiCommandBuilder.set("#KeyfinderUpgradeButtonLabel.Text", keyfinderLevel >= keyfinderMaxLevel ? "MAX" : "Upgrade");

        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of(ACTION_KEY, ACTION_CLOSE));
        Debug.log("[FarmingDebug] bound UI event Activating -> #CloseButton");

        if (tokenFinderLevel < tokenFinderMaxLevel) {
            uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#TokenFinderUpgradeButton", EventData.of(ACTION_KEY, ACTION_UPGRADE_TOKEN_FINDER));
            Debug.log("[FarmingDebug] bound UI event Activating -> #TokenFinderUpgradeButton");
        } else {
            Debug.log("[FarmingDebug] token finder at MAX; no upgrade binding added");
        }

        if (fortuneLevel < fortuneMaxLevel) {
            uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#FortuneUpgradeButton", EventData.of(ACTION_KEY, ACTION_UPGRADE_FORTUNE));
            Debug.log("[FarmingDebug] bound UI event Activating -> #FortuneUpgradeButton");
        } else {
            Debug.log("[FarmingDebug] fortune at MAX; no upgrade binding added");
        }

        if (keyfinderLevel < keyfinderMaxLevel) {
            uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#KeyfinderUpgradeButton", EventData.of(ACTION_KEY, ACTION_UPGRADE_KEYFINDER));
            Debug.log("[FarmingDebug] bound UI event Activating -> #KeyfinderUpgradeButton");
        } else {
            Debug.log("[FarmingDebug] keyfinder at MAX; no upgrade binding added");
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

                if (line.startsWith("Button #") && line.endsWith("{")) {
                    int depth = 1;
                    for (int j = i + 1; j < lines.length; j++) {
                        String inner = lines[j].trim();
                        depth += count(inner, '{');
                        depth -= count(inner, '}');
                        if (depth == 1 && inner.startsWith("Text:")) {
                            return "unsupported Button.Text field detected at line " + (j + 1);
                        }
                        if (depth <= 0) {
                            break;
                        }
                    }
                }

                if (line.startsWith("Group #") && line.endsWith("{")) {
                    int depth = 1;
                    for (int j = i + 1; j < lines.length; j++) {
                        String inner = lines[j].trim();
                        depth += count(inner, '{');
                        depth -= count(inner, '}');
                        if (depth == 1 && inner.startsWith("Style:")) {
                            return "unsupported Group.Style field detected at line " + (j + 1);
                        }
                        if (depth <= 0) {
                            break;
                        }
                    }
                }

                if (line.contains("Style:") && line.matches(".*\\bColor\\s*:.*")) {
                    return "unsupported LabelStyle.Color field detected at line " + (i + 1) + "; use TextColor";
                }
            }
            return null;
        } catch (Exception ex) {
            return "validation exception: " + ex.getMessage();
        }
    }

    private int count(String line, char ch) {
        int n = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ch) {
                n++;
            }
        }
        return n;
    }

    private void refreshUi() {
        try {
            rebuild();
            Debug.log("[FarmingDebug] UI refreshed after upgrade interaction");
        } catch (Exception ex) {
            Debug.log("[FarmingDebug] UI refresh failed: " + ex.getMessage());
        }
    }
}
