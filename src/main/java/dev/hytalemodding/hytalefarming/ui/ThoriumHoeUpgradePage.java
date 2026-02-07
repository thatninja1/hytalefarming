package dev.hytalemodding.hytalefarming.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.hytalefarming.Debug;

import javax.annotation.Nonnull;

public class ThoriumHoeUpgradePage extends CustomUIPage {

    private static final String UI_TEMPLATE = "Pages/HytaleFarming/ThoriumHoeUpgrade.ui";
    private static final String UI_RESOURCE_PATH = "Common/UI/Custom/Pages/HytaleFarming/ThoriumHoeUpgrade.ui";

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
        Debug.log("[HoeDebug] UI build start: template=" + UI_TEMPLATE
                + " expectedJarPath=" + UI_RESOURCE_PATH
                + " resourceStreamExists=" + hasResourceStreamUi
                + " classpathExists=" + hasClasspathUi);

        if (!hasResourceStreamUi) {
            Debug.log("[HoeDebug] UI asset missing at /" + UI_RESOURCE_PATH + "; skipping append to avoid client crash");
            if (player != null) {
                player.sendMessage(Message.raw("[HytaleFarming] UI asset missing (/" + UI_RESOURCE_PATH + ")."));
            }
            return;
        }

        try {
            Debug.log("[HoeDebug] append UI_TEMPLATE=" + UI_TEMPLATE);
            uiCommandBuilder.append(UI_TEMPLATE);
            uiEventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of("action", "close"));
            Debug.log("[HoeDebug] bound UI event Activating -> #CloseButton");
        } catch (Exception ex) {
            Debug.log("[HoeDebug] append UI_TEMPLATE failed: " + ex.getMessage());
            if (player != null) {
                player.sendMessage(Message.raw("[HytaleFarming] Failed to open hoe upgrade UI."));
            }
        }
    }
    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                String eventData) {
        Debug.log("[HoeDebug] UI data event received: " + eventData);
        if (eventData != null && eventData.contains("close")) {
            close();
            Debug.log("[HoeDebug] close event handled");
        }
    }

}
