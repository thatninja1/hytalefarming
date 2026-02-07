package dev.hytalemodding.hytalefarming.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
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
    private static final String APPEND_SELECTOR = "#Root";

    public ThoriumHoeUpgradePage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder uiCommandBuilder,
                      @Nonnull UIEventBuilder uiEventBuilder,
                      @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());

        String expectedJarPath = "resources/Common/UI/Custom/ThoriumHoeUpgrade.ui";
        boolean hasClasspathUi = getClass().getClassLoader().getResource(expectedJarPath) != null;
        Debug.log("[HoeDebug] UI build start: docPrimary=" + PRIMARY_DOCUMENT_ID
                + " docLegacy=" + LEGACY_DOCUMENT_ID
                + " selector=" + APPEND_SELECTOR
                + " expectedJarPath=" + expectedJarPath
                + " classpathExists=" + hasClasspathUi);

        try {
            uiCommandBuilder.append(PRIMARY_DOCUMENT_ID, APPEND_SELECTOR);
            Debug.log("[HoeDebug] UI command sent to client via append(doc,selector) doc="
                    + PRIMARY_DOCUMENT_ID + " selector=" + APPEND_SELECTOR);
        } catch (Exception primaryEx) {
            Debug.log("[HoeDebug] primary UI append failed: " + primaryEx.getMessage());
            try {
                uiCommandBuilder.append(LEGACY_DOCUMENT_ID, APPEND_SELECTOR);
                Debug.log("[HoeDebug] UI fallback command sent doc=" + LEGACY_DOCUMENT_ID + " selector=" + APPEND_SELECTOR);
            } catch (Exception fallbackEx) {
                Debug.log("[HoeDebug] fallback UI append failed: " + fallbackEx.getMessage());
                if (player != null) {
                    player.sendMessage(Message.raw("[HytaleFarming] UI failed to open; missing ThoriumHoeUpgrade.ui"));
                }
                uiCommandBuilder.appendInline(
                        "Group #ThoriumHoeFallback { LayoutMode: Center; Label { Text: \"Thorium Hoe Upgrades\"; Anchor: (Width: 400, Height: 40); } }",
                        APPEND_SELECTOR
                );
                Debug.log("[HoeDebug] inline fallback UI command sent");
            }
        }
    }
}
