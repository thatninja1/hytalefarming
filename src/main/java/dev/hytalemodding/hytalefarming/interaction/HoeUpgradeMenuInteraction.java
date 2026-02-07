package dev.hytalemodding.hytalefarming.interaction;

import com.hypixel.hytale.server.core.entity.EntityStore;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.ref.Ref;
import com.hypixel.hytale.server.core.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.interaction.InteractionContext;
import com.hypixel.hytale.server.core.interaction.InteractionType;
import com.hypixel.hytale.server.core.interaction.instants.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.util.codec.BuilderCodec;
import dev.hytalemodding.hytalefarming.ui.ThoriumHoeUpgradePage;

import javax.annotation.Nonnull;

public class HoeUpgradeMenuInteraction extends SimpleInstantInteraction {

    public static final BuilderCodec<HoeUpgradeMenuInteraction> CODEC = BuilderCodec.builder(
            HoeUpgradeMenuInteraction.class,
            HoeUpgradeMenuInteraction::new,
            SimpleInstantInteraction.CODEC
    ).build();

    @Override
    protected void firstRun(@Nonnull InteractionType interactionType, @Nonnull InteractionContext interactionContext, @Nonnull CooldownHandler cooldownHandler) {
        if (interactionContext.getCommandBuffer() == null) {
            return;
        }

        Ref<EntityStore> ref = interactionContext.getEntity();
        Player player = interactionContext.getCommandBuffer().getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        player.getPageManager().openCustomPage(ref, interactionContext.getCommandBuffer().getExternalData().getStore(), new ThoriumHoeUpgradePage());
    }
}
