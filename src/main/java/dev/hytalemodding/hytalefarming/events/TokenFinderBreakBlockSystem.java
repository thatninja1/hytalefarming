package dev.hytalemodding.hytalefarming.events;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class TokenFinderBreakBlockSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private static final long DEDUPE_WINDOW_MS = 250L;
    private static final Map<String, Long> RECENT_HARVEST_REWARDS = new ConcurrentHashMap<>();

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

        boolean validHarvestable = isValidHarvestableCrop(blockId);
        Debug.log("[EnchantProc] cropValidAndFullyGrown=" + validHarvestable + " blockId=" + blockId + " player=" + playerRef.getUsername());
        if (!validHarvestable) {
            return;
        }

        Vector3i targetBlock = event.getTargetBlock();
        int blockX = targetBlock == null ? 0 : targetBlock.getX();
        int blockY = targetBlock == null ? 0 : targetBlock.getY();
        int blockZ = targetBlock == null ? 0 : targetBlock.getZ();
        if (!shouldProcessReward(playerRef, blockX, blockY, blockZ, "break_event")) {
            Debug.log("[HarvestDedupe] Skipping duplicate reward player=" + playerRef.getUsername() + " block=" + blockX + "," + blockY + "," + blockZ);
            return;
        }

        int tokenFinderLevel = plugin.getTokenService().enchantLevel(playerRef.getUuid(), playerRef.getUsername(), "token_finder");
        int fortuneLevel = plugin.getTokenService().enchantLevel(playerRef.getUuid(), playerRef.getUsername(), "fortune");
        int keyfinderLevel = plugin.getTokenService().enchantLevel(playerRef.getUuid(), playerRef.getUsername(), "keyfinder");

        Debug.log("[Drops] Could not intercept drop list; leaving vanilla drops for blockId=" + blockId);

        processTokenFinder(player, playerRef, tokenFinderLevel);
        processFortune(player, blockId, fortuneLevel);
        processKeyfinder(player, playerRef, keyfinderLevel);
    }

    private void processTokenFinder(Player player, PlayerRef playerRef, int level) {
        EnchantsConfig.TokenFinder cfg = plugin.getEnchantsConfig().getTokenFinder();
        int maxLevel = cfg.getMaxLevel();
        if (!rollProc("token_finder", level, maxLevel, cfg.getEnchantProc())) {
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
        if (!rollProc("fortune", level, maxLevel, cfg.getEnchantProc())) {
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
        if (!rollProc("keyfinder", level, maxLevel, cfg.getEnchantProc())) {
            return;
        }

        EnchantsConfig.Crate chosenCrate = chooseCrate(cfg.getCrates());
        if (chosenCrate == null) {
            Debug.log("[Keyfinder] warning: proc succeeded but crate config is invalid; no command executed");
            return;
        }

        String finalCommand = chosenCrate.getCommand()
                .replace("{player}", playerRef.getUsername())
                .replace("<crateid>", chosenCrate.getCrateId());

        String normalizedCommand = finalCommand.startsWith("/") ? finalCommand.substring(1) : finalCommand;
        Debug.log("Keyfinder proc success -> selected crateId=" + chosenCrate.getCrateId() + " command=" + normalizedCommand);
        CommandManager.get().handleCommand(player, normalizedCommand);
    }

    private EnchantsConfig.Crate chooseCrate(List<EnchantsConfig.Crate> crates) {
        if (crates == null || crates.isEmpty()) {
            Debug.log("[Keyfinder] warning: no crates configured; no command executed");
            return null;
        }

        double totalWeight = 0;
        for (EnchantsConfig.Crate crate : crates) {
            totalWeight += crate.getCrateChance();
        }

        if (totalWeight <= 0D) {
            Debug.log("[Keyfinder] warning: all crate_chance weights are <= 0; no command executed");
            return null;
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

    private boolean rollProc(String enchantId, int level, int maxLevel, double enchantProcFromJson) {
        if (level <= 0) {
            Debug.log("[EnchantProc] enchant=" + enchantId + " level=" + level + " maxLevel=" + maxLevel
                    + " enchantProc=" + enchantProcFromJson + " computedChance=0.0 roll=n/a procResult=false");
            return false;
        }

        double computedChance;
        if (enchantProcFromJson >= 1.0D) {
            computedChance = 1.0D;
        } else {
            computedChance = enchantProcFromJson * ((double) level / Math.max(1, maxLevel));
        }
        computedChance = Math.max(0.0D, Math.min(1.0D, computedChance));

        double roll = ThreadLocalRandom.current().nextDouble();
        boolean result = roll <= computedChance;

        Debug.log("[EnchantProc] enchant=" + enchantId
                + " level=" + level
                + " maxLevel=" + maxLevel
                + " enchantProc=" + enchantProcFromJson
                + " computedChance=" + computedChance
                + " roll=" + roll
                + " procResult=" + result);
        return result;
    }


    public static boolean isValidHarvestableCrop(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return false;
        }
        String normalized = normalizeBlockId(blockId);
        boolean isCrop = normalized.startsWith("Crop_") || normalized.startsWith("Plant_Crop_");
        boolean isFullyGrown = normalized.contains("State_Definitions_StageFinal");
        return isCrop && isFullyGrown;
    }

    public static String normalizeBlockId(String blockId) {
        if (blockId == null) {
            return null;
        }
        return blockId.startsWith("*") ? blockId.substring(1) : blockId;
    }

    public static boolean shouldProcessReward(PlayerRef playerRef, int x, int y, int z, String source) {
        long now = System.currentTimeMillis();
        String key = playerRef.getUuid() + ":" + x + ":" + y + ":" + z;
        Long previous = RECENT_HARVEST_REWARDS.put(key, now);
        if (previous == null) {
            return true;
        }
        boolean allowed = (now - previous) > DEDUPE_WINDOW_MS;
        if (!allowed) {
            Debug.log("[HarvestDedupe] source=" + source + " player=" + playerRef.getUsername() + " key=" + key + " elapsedMs=" + (now - previous));
        }
        return allowed;
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
