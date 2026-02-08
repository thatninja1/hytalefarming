package dev.hytalemodding.hytalefarming.events;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.hytalefarming.Debug;
import dev.hytalemodding.hytalefarming.HytaleFarmingPlugin;

import javax.annotation.Nonnull;

public class TokenFinderBreakBlockSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private final HytaleFarmingPlugin plugin;

    public TokenFinderBreakBlockSystem(HytaleFarmingPlugin plugin) {
        super(BreakBlockEvent.class);
        this.plugin = plugin;
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> chunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull BreakBlockEvent event) {
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        ItemStack inHand = event.getItemInHand();
        String itemId = inHand == null ? "<none>" : inHand.getItemId();
        String blockId = event.getBlockType() == null ? "<unknown>" : event.getBlockType().getId();
        if (blockId != null && blockId.startsWith("*")) {
            blockId = blockId.substring(1);
        }

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        int level = plugin.getTokenService().enchantLevel(playerRef.getUuid(), playerRef.getUsername(), "token_finder");
        Debug.log("BreakBlockEvent player=" + player.getDisplayName()
                + " item=" + itemId + " block=" + blockId + " tokenFinderLevel=" + level);

        if (!"Tool_Hoe_Thorium".equals(itemId)) {
            return;
        }
        boolean isCrop = blockId != null && (blockId.startsWith("Crop_") || blockId.startsWith("Plant_Crop_"));
        if (!isCrop) {
            return;
        }

        boolean isFullyGrown = blockId.contains("State_Definitions_StageFinal");
        if (!isFullyGrown) {
            Debug.log("[TokenFinder] Skipping token award: crop not fully grown blockId=" + blockId);
            return;
        }

        long awarded = plugin.getTokenService().processTokenFinderCropBreak(playerRef.getUuid(), playerRef.getUsername());
        if (awarded > 0) {
            int max = plugin.getEnchantsConfig().getTokenFinder().getMaxLevel();
            double chance = Math.min(1D, (double) level / (double) max);
            Debug.log("TokenFinder proc success player=" + player.getDisplayName() + " chance=" + chance + " awarded=" + awarded);
            player.sendMessage(Message.raw("+" + awarded + " " + plugin.getTokensConfig().getCurrencyName() + " (Token Finder)"));
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.of(Player.getComponentType());
    }
}
