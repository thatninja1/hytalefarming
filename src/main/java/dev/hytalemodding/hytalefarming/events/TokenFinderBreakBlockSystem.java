package dev.hytalemodding.hytalefarming.events;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.hytalefarming.Debug;
import dev.hytalemodding.hytalefarming.HytaleFarmingPlugin;
import dev.hytalemodding.hytalefarming.config.EnchantsConfig;

import javax.annotation.Nonnull;
import java.util.List;
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

        if (!"Tool_Hoe_Thorium".equals(itemId)) {
            return;
        }

        boolean isCrop = blockId != null && (blockId.startsWith("Crop_") || blockId.startsWith("Plant_Crop_"));
        if (!isCrop) {
            return;
        }

        boolean isFullyGrown = blockId.contains("State_Definitions_StageFinal");
        Debug.log("[EnchantProc] cropFullyGrown=" + isFullyGrown + " blockId=" + blockId + " player=" + playerRef.getUsername());
        if (!isFullyGrown) {
            return;
        }

        int tokenFinderLevel = plugin.getTokenService().enchantLevel(playerRef.getUuid(), playerRef.getUsername(), "token_finder");
        int fortuneLevel = plugin.getTokenService().enchantLevel(playerRef.getUuid(), playerRef.getUsername(), "fortune");
        int keyfinderLevel = plugin.getTokenService().enchantLevel(playerRef.getUuid(), playerRef.getUsername(), "keyfinder");

        processTokenFinder(player, playerRef, tokenFinderLevel);
        processFortune(player, blockId, fortuneLevel);
        processKeyfinder(player, playerRef, keyfinderLevel);
    }

    private void processTokenFinder(Player player, PlayerRef playerRef, int level) {
        EnchantsConfig.TokenFinder cfg = plugin.getEnchantsConfig().getTokenFinder();
        int maxLevel = cfg.getMaxLevel();
        if (!rollProc("TokenFinder", level, maxLevel, cfg.getEnchantProc(), false)) {
            return;
        }

        long awarded = (long) Math.max(0, level) * plugin.getTokensConfig().getTokensTimes();
        if (awarded <= 0) {
            return;
        }

        plugin.getTokenService().addTokens(playerRef.getUuid(), playerRef.getUsername(), awarded);
        player.sendMessage(Message.raw("+" + awarded + " " + plugin.getTokensConfig().getCurrencyName() + " (Token Finder)"));
    }

    private void processFortune(Player player, String blockId, int level) {
        EnchantsConfig.Fortune cfg = plugin.getEnchantsConfig().getFortune();
        int maxLevel = cfg.getMaxLevel();
        if (!rollProc("Fortune", level, maxLevel, cfg.getEnchantProc(), false)) {
            return;
        }

        int extraAmount = ThreadLocalRandom.current().nextInt(Math.max(0, level - 1), Math.max(1, level) + 1);
        if (extraAmount <= 0) {
            Debug.log("[Fortune] rolled extraAmount=0 level=" + level + " blockId=" + blockId);
            return;
        }

        String cropKey = extractCropKey(blockId);
        if (cropKey == null || cropKey.isBlank()) {
            Debug.log("[Fortune] could not extract crop key from blockId=" + blockId);
            return;
        }

        String itemId = "Plant_Crop_" + cropKey + "_Item";
        ItemStack extraStack = new ItemStack(itemId, extraAmount);
        ItemStackTransaction tx = player.getInventory().getCombinedEverything().addItemStack(extraStack);
        boolean success = tx != null && tx.succeeded();

        Debug.log("[Fortune] blockId=" + blockId + " cropItem=" + itemId + " extraAmount=" + extraAmount + " grantSucceeded=" + success);

        if (success) {
            player.sendMessage(Message.raw("+" + extraAmount + " " + itemId + " (Fortune)"));
        }
    }

    private void processKeyfinder(Player player, PlayerRef playerRef, int level) {
        EnchantsConfig.Keyfinder cfg = plugin.getEnchantsConfig().getKeyfinder();
        int maxLevel = cfg.getMaxLevel();
        if (!rollProc("Keyfinder", level, maxLevel, cfg.getEnchantProc(), true)) {
            return;
        }

        EnchantsConfig.Crate chosenCrate = chooseCrate(cfg.getCrates());
        if (chosenCrate == null) {
            Debug.log("[Keyfinder] proc succeeded but no crate configuration available");
            return;
        }

        String finalCommand = chosenCrate.getCommand()
                .replace("{player}", playerRef.getUsername())
                .replace("<crateid>", chosenCrate.getCrateId());

        String normalizedCommand = finalCommand.startsWith("/") ? finalCommand.substring(1) : finalCommand;
        Debug.log("[Keyfinder] proc succeeded crate=" + chosenCrate.getCrateId() + " command=" + normalizedCommand);
        CommandManager.get().handleCommand(player, normalizedCommand);
    }

    private EnchantsConfig.Crate chooseCrate(List<EnchantsConfig.Crate> crates) {
        if (crates == null || crates.isEmpty()) {
            return null;
        }

        double totalWeight = 0;
        for (EnchantsConfig.Crate crate : crates) {
            totalWeight += crate.getCrateChance();
        }

        if (totalWeight <= 0D) {
            return crates.getFirst();
        }

        double roll = ThreadLocalRandom.current().nextDouble(totalWeight);
        double current = 0;
        for (EnchantsConfig.Crate crate : crates) {
            current += crate.getCrateChance();
            if (roll <= current) {
                return crate;
            }
        }

        return crates.getLast();
    }

    private boolean rollProc(String enchantName, int level, int maxLevel, double enchantProc, boolean forceScaled) {
        if (level <= 0) {
            Debug.log("[EnchantProc] enchant=" + enchantName + " level=" + level + " maxLevel=" + maxLevel
                    + " enchantProc=" + enchantProc + " effectiveProc=0.0 roll=n/a result=false");
            return false;
        }

        double scaledProc = Math.max(0.0D, Math.min(1.0D, enchantProc * ((double) level / Math.max(1, maxLevel))));
        double effectiveProc = (enchantProc >= 1.0D && !forceScaled) ? 1.0D : scaledProc;
        double roll = ThreadLocalRandom.current().nextDouble();
        boolean result = roll <= effectiveProc;

        Debug.log("[EnchantProc] enchant=" + enchantName
                + " level=" + level
                + " maxLevel=" + maxLevel
                + " enchantProc=" + enchantProc
                + " effectiveProc=" + effectiveProc
                + " roll=" + roll
                + " result=" + result);
        return result;
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
