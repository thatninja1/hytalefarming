package dev.hytalemodding.hytalefarming.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.InteractionType;
import dev.hytalemodding.hytalefarming.Debug;
import dev.hytalemodding.hytalefarming.ui.ThoriumHoeUpgradePage;

import javax.annotation.Nonnull;

public class HoeUpgradeMenuInteraction extends SimpleInstantInteraction {

    public static final BuilderCodec<HoeUpgradeMenuInteraction> CODEC = BuilderCodec.builder(
            HoeUpgradeMenuInteraction.class,
            HoeUpgradeMenuInteraction::new,
            SimpleInstantInteraction.CODEC
    ).build();

    @Override
    protected void firstRun(@Nonnull InteractionType interactionType,
                            @Nonnull InteractionContext interactionContext,
                            @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> commandBuffer = interactionContext.getCommandBuffer();
        if (commandBuffer == null) {
            Debug.log("Interaction firstRun: commandBuffer null");
            return;
        }

        Ref<EntityStore> ref = interactionContext.getEntity();
        Player player = commandBuffer.getComponent(ref, Player.getComponentType());
        String heldItem = interactionContext.getHeldItem() == null ? "<none>" : interactionContext.getHeldItem().getItemId();

        Debug.log("Detected right-click interaction type=" + interactionType
                + " player=" + (player == null ? "<null>" : player.getDisplayName())
                + " heldItem=" + heldItem);

        if (player == null) {
            return;
        }

        if (interactionType == InteractionType.Secondary && "Tool_Hoe_Thorium".equals(heldItem)) {
            PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                Debug.log("Failed to open UI: PlayerRef component missing");
                return;
            }
            player.getPageManager().openCustomPage(ref, commandBuffer.getStore(), new ThoriumHoeUpgradePage(playerRef));
            Debug.log("Detected Secondary right-click with Tool_Hoe_Thorium - opened upgrade UI");
        } else {
            Debug.log("Interaction did not match secondary thorium hoe requirement");
        }
    }
}
