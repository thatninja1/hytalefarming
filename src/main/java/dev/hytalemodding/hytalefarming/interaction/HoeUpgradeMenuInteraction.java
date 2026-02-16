package dev.hytalemodding.hytalefarming.interaction;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.InteractionType;
import dev.hytalemodding.hytalefarming.Debug;
import dev.hytalemodding.hytalefarming.util.FarmingTools;

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

        if (interactionType == InteractionType.Secondary && FarmingTools.isValidFarmingTool(heldItem)) {
            Debug.log("Secondary sickle interaction received; UI opening via interaction is disabled. Use /farming upgrade.");
            return;
        }

        Debug.log("Interaction did not match disabled-secondary-ui path");
    }
}
