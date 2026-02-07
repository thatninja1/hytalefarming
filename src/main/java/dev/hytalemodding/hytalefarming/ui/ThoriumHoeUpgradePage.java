package dev.hytalemodding.hytalefarming.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUICommand;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import dev.hytalemodding.hytalefarming.Debug;

import javax.annotation.Nonnull;

public class ThoriumHoeUpgradePage extends CustomUIPage {

    private static final String PRIMARY_DOCUMENT_ID = "Custom/ThoriumHoeUpgrade.ui";
    private static final String LEGACY_DOCUMENT_ID = "ThoriumHoeUpgrade.ui";

    public ThoriumHoeUpgradePage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder,
                      @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());

        String expectedJarPath = "Common/UI/Custom/ThoriumHoeUpgrade.ui";
        boolean hasResourceStreamUi = getClass().getResourceAsStream("/" + expectedJarPath) != null;
        boolean hasClasspathUi = getClass().getClassLoader().getResource(expectedJarPath) != null;
        Debug.log("[HoeDebug] UI build start: docPrimary=" + PRIMARY_DOCUMENT_ID
                + " docLegacy=" + LEGACY_DOCUMENT_ID
                + " expectedJarPath=" + expectedJarPath
                + " resourceStreamExists=" + hasResourceStreamUi
                + " classpathExists=" + hasClasspathUi);

        if (!hasResourceStreamUi) {
            Debug.log("[HoeDebug] UI asset missing at /" + expectedJarPath + "; skipping append to avoid client crash");
            if (player != null) {
                player.sendMessage(Message.raw("[HytaleFarming] UI asset missing (/Common/UI/Custom/ThoriumHoeUpgrade.ui)."));
            }
            return;
        }

        boolean sent = false;
        try {
            uiCommandBuilder.append(PRIMARY_DOCUMENT_ID);
            Debug.log("[HoeDebug] UI command builder used append(docOnly) doc=" + PRIMARY_DOCUMENT_ID);
            sent = true;
        } catch (Exception primaryEx) {
            Debug.log("[HoeDebug] primary append(docOnly) failed: " + primaryEx.getMessage());
        }

        if (!sent) {
            try {
                uiCommandBuilder.append(LEGACY_DOCUMENT_ID);
                Debug.log("[HoeDebug] UI fallback builder used append(docOnly) doc=" + LEGACY_DOCUMENT_ID);
                sent = true;
            } catch (Exception fallbackEx) {
                Debug.log("[HoeDebug] fallback append(docOnly) failed: " + fallbackEx.getMessage());
            }
        }

        logBuiltCommands(uiCommandBuilder);

        if (!sent) {
            if (player != null) {
                player.sendMessage(Message.raw("[HytaleFarming] UI missing or failed to open"));
            }
            try {
                uiCommandBuilder.appendInline(
                        "Group #Root { LayoutMode: Center; Label { Text: \"Thorium Hoe Upgrades\"; Anchor: (Width: 420, Height: 42); } }",
                        "#Root"
                );
                Debug.log("[HoeDebug] inline fallback UI command sent");
                logBuiltCommands(uiCommandBuilder);
            } catch (Exception inlineEx) {
                Debug.log("[HoeDebug] inline fallback failed: " + inlineEx.getMessage());
            }
        }
    }

    private void logBuiltCommands(UICommandBuilder uiCommandBuilder) {
        try {
            CustomUICommand[] commands = uiCommandBuilder.getCommands();
            if (commands == null) {
                Debug.log("[HoeDebug] UI commands: <null>");
                return;
            }
            for (int i = 0; i < commands.length; i++) {
                CustomUICommand cmd = commands[i];
                if (cmd == null) {
                    Debug.log("[HoeDebug] UI cmd[" + i + "] = <null>");
                    continue;
                }
                Debug.log("[HoeDebug] UI cmd[" + i + "] type=" + cmd.type
                        + " selector=" + cmd.selector
                        + " data=" + cmd.data
                        + " text=" + cmd.text);
            }
        } catch (Exception ex) {
            Debug.log("[HoeDebug] failed to dump UI commands: " + ex.getMessage());
        }
    }
}
