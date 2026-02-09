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
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.hytalefarming.Debug;
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

        int tokenFinderLevel = plugin.getTokenService().enchantLevel(playerRef.getUuid(), playerRef.getUsername(), "token_finder");
        int fortuneLevel = plugin.getTokenService().enchantLevel(playerRef.getUuid(), playerRef.getUsername(), "fortune");
        Debug.log("BreakBlockEvent player=" + player.getDisplayName()
                + " item=" + itemId + " block=" + blockId
                + " tokenFinderLevel=" + tokenFinderLevel
                + " fortuneLevel=" + fortuneLevel);

        if (!"Tool_Hoe_Thorium".equals(itemId)) {
            return;
        }

        boolean isCrop = blockId != null && (blockId.startsWith("Crop_") || blockId.startsWith("Plant_Crop_"));
        if (!isCrop) {
            return;
        }

        boolean isFullyGrown = blockId.contains("State_Definitions_StageFinal");
        Debug.log("[TokenFinder] cropFullyGrown=" + isFullyGrown + " blockId=" + blockId);
        if (!isFullyGrown) {
            Debug.log("[TokenFinder] Skipping token award: crop not fully grown blockId=" + blockId);
            return;
        }

        long awarded = plugin.getTokenService().processTokenFinderCropBreak(playerRef.getUuid(), playerRef.getUsername());
        if (awarded > 0) {
            int max = plugin.getEnchantsConfig().getTokenFinder().getMaxLevel();
            double chance = Math.min(1D, (double) tokenFinderLevel / (double) max);
            Debug.log("TokenFinder proc success player=" + player.getDisplayName() + " chance=" + chance + " awarded=" + awarded);
            player.sendMessage(Message.raw("+" + awarded + " " + plugin.getTokensConfig().getCurrencyName() + " (Token Finder)"));
        }

        processFortuneProc(player, blockId, fortuneLevel);
    }

    private void processFortuneProc(Player player, String blockId, int fortuneLevel) {
        if (fortuneLevel <= 0) {
            return;
        }

        int extraAmount = ThreadLocalRandom.current().nextInt(Math.max(0, fortuneLevel - 1), fortuneLevel + 1);
        if (extraAmount <= 0) {
            Debug.log("[Fortune] extra amount rolled 0; no extra items. level=" + fortuneLevel + " blockId=" + blockId);
            return;
        }

        String cropKey = extractCropKey(blockId);
        if (cropKey == null || cropKey.isBlank()) {
            Debug.log("[Fortune] could not extract crop key from blockId=" + blockId + "; skipping extra item grant");
            return;
        }

        String itemId = "Plant_Crop_" + cropKey + "_Item";
        ItemStack extraStack = new ItemStack(itemId, extraAmount);
        ItemStackTransaction tx = player.getInventory().getCombinedEverything().addItemStack(extraStack);
        boolean success = tx != null && tx.succeeded();

        Debug.log("[Fortune] level=" + fortuneLevel
                + " extraAmount=" + extraAmount
                + " computedItemId=" + itemId
                + " grantSucceeded=" + success);

        if (success) {
            player.sendMessage(Message.raw("+" + extraAmount + " " + itemId + " (Fortune)"));
        }
    }

    private String extractCropKey(String blockId) {
        if (blockId == null) {
            return null;
        }

        if (blockId.startsWith("Plant_Crop_")) {
            String remainder = blockId.substring("Plant_Crop_".length());
            int blockIndex = remainder.indexOf("_Block");
            if (blockIndex > 0) {
                return remainder.substring(0, blockIndex);
            }
        }

        if (blockId.startsWith("Crop_")) {
            String remainder = blockId.substring("Crop_".length());
            int blockIndex = remainder.indexOf("_Block");
            if (blockIndex > 0) {
                return remainder.substring(0, blockIndex);
            }
            return remainder;
        }

        return null;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.of(Player.getComponentType());
    }
}
