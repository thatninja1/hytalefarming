package dev.hytalemodding.hytalefarming.events;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.EntityStore;
import com.hypixel.hytale.server.core.entity.archetype.Archetype;
import com.hypixel.hytale.server.core.entity.archetype.ArchetypeChunk;
import com.hypixel.hytale.server.core.entity.archetype.Query;
import com.hypixel.hytale.server.core.entity.commandbuffer.CommandBuffer;
import com.hypixel.hytale.server.core.entity.component.store.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.event.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.item.ItemStack;
import dev.hytalemodding.hytalefarming.HytaleFarmingPlugin;

import javax.annotation.Nonnull;
import java.util.concurrent.ThreadLocalRandom;

public class TokenFinderBreakBlockSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private final HytaleFarmingPlugin plugin;

    public TokenFinderBreakBlockSystem(HytaleFarmingPlugin plugin) {
        super(BreakBlockEvent.class);
        this.plugin = plugin;
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull BreakBlockEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        ItemStack held = player.getInventory().getHotbar().getCurrentItemStack();
        if (held == null || !"Tool_Hoe_Thorium".equals(held.getItemId())) {
            return;
        }

        String brokenBlockId = event.getBlockType().getId();
        if (!brokenBlockId.startsWith("Crop_") && !brokenBlockId.startsWith("Plant_Crop_")) {
            return;
        }

        int level = plugin.getTokenService().enchantLevel(store, event.getRef(), "token_finder");
        if (level <= 0) {
            return;
        }

        int max = plugin.getEnchantsConfig().getTokenFinder().getMaxLevel();
        double chance = Math.min(1D, (double) level / (double) max);

        if (ThreadLocalRandom.current().nextDouble() <= chance) {
            int awarded = level * plugin.getTokensConfig().getTokensTimes();
            plugin.getTokenService().addTokens(store, event.getRef(), player, awarded);
            player.sendMessage(Message.raw("+" + awarded + " " + plugin.getTokensConfig().getCurrencyName() + " (Token Finder)"));
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}
