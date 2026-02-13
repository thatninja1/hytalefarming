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
import dev.hytalemodding.hytalefarming.util.MessageFormatter;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class TokenFinderBreakBlockSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private static final long DEDUPE_WINDOW_MS = 250L;
    private static final long PENDING_USE_WINDOW_MS = 1200L;
    private static final Map<String, Long> RECENT_HARVEST_REWARDS = new ConcurrentHashMap<>();
    private static final Map<String, PendingUseHarvestContext> PENDING_USE_HARVESTS = new ConcurrentHashMap<>();
    private static final Map<String, Long> RECENT_BREAK_EVENTS = new ConcurrentHashMap<>();

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

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        if (event.getTargetBlock() != null) {
            markBreakEventObserved(playerRef, event.getTargetBlock().getX(), event.getTargetBlock().getY(), event.getTargetBlock().getZ(), "BreakBlockEvent");
        }

        ItemStack inHand = event.getItemInHand();
        String eventHeldItemId = inHand == null ? "<none>" : inHand.getItemId();
        String blockId = event.getBlockType() == null ? "<unknown>" : event.getBlockType().getId();
        Vector3i target = event.getTargetBlock();

        String resolvedHeldItemId = eventHeldItemId;
        String source = "PrimaryBreak";

        if (!"Tool_Hoe_Thorium".equals(eventHeldItemId) && target != null) {
            PendingUseHarvestContext pending = consumePendingUseHarvest(playerRef, target.getX(), target.getY(), target.getZ());
            if (pending != null) {
                resolvedHeldItemId = pending.heldItemId();
                source = "UseHarvest";
                if (pending.cachedBlockId() != null && !pending.cachedBlockId().isBlank()) {
                    blockId = pending.cachedBlockId();
                }
                Debug.log("[Harvest] consumed pending Use context player=" + playerRef.getUsername()
                        + " heldItemId=" + resolvedHeldItemId
                        + " cachedBlockId=" + blockId
                        + " ageMs=" + (System.currentTimeMillis() - pending.createdAtMs()));
            }
        }

        handleCropBreakAndProcs(player, playerRef, resolvedHeldItemId, blockId, target, source);
    }

    public void handleCropBreakAndProcs(Player player,
                                        PlayerRef playerRef,
                                        String heldItemId,
                                        String brokenBlockId,
                                        Vector3i blockPos,
                                        String source) {
        String normalizedBlockId = normalizeBlockId(brokenBlockId);

        Debug.log("[CropBreak] source=" + source
                + " player=" + playerRef.getUsername()
                + " heldItemId=" + heldItemId
                + " cachedBrokenBlockId=" + normalizedBlockId
                + " blockPos=" + (blockPos == null ? "<null>" : (blockPos.getX() + "," + blockPos.getY() + "," + blockPos.getZ())));

        if (!"Tool_Hoe_Thorium".equals(heldItemId)) {
            Debug.log("[CropBreak] source=" + source + " rejected reason=not_thorium_hoe heldItemId=" + heldItemId);
            return;
        }

        boolean validHarvestable = isValidHarvestableCrop(normalizedBlockId);
        Debug.log("[CropBreak] source=" + source + " fullyGrownValidationPassed=" + validHarvestable + " blockId=" + normalizedBlockId);
        if (!validHarvestable) {
            Debug.log("[CropBreak] source=" + source + " rejected reason=not_valid_fully_grown_crop blockId=" + normalizedBlockId);
            return;
        }

        if ("UseHarvest".equals(source)) {
            Debug.log("[Harvest] usingRealBreak=true source=UseHarvest");
            Debug.log("[Harvest] vanillaDropsCaptured=unknown source=UseHarvest");
            Debug.log("[Harvest] vanillaEssenceCaptured=unknown source=UseHarvest");
        }

        int blockX = blockPos == null ? 0 : blockPos.getX();
        int blockY = blockPos == null ? 0 : blockPos.getY();
        int blockZ = blockPos == null ? 0 : blockPos.getZ();
        if (!shouldProcessReward(playerRef, blockX, blockY, blockZ, source)) {
            Debug.log("[HarvestDedupe] source=" + source + " skipped duplicate reward player=" + playerRef.getUsername()
                    + " block=" + blockX + "," + blockY + "," + blockZ);
            return;
        }

        int tokenFinderLevel = plugin.getTokenService().enchantLevel(playerRef.getUuid(), playerRef.getUsername(), "token_finder");
        int fortuneLevel = plugin.getTokenService().enchantLevel(playerRef.getUuid(), playerRef.getUsername(), "fortune");
        int keyfinderLevel = plugin.getTokenService().enchantLevel(playerRef.getUuid(), playerRef.getUsername(), "keyfinder");

        Debug.log("[Drops] Could not intercept drop list; leaving vanilla drops for blockId=" + normalizedBlockId + " source=" + source);

        processTokenFinder(player, playerRef, tokenFinderLevel, source);
        processFortune(player, normalizedBlockId, fortuneLevel, source);
        processKeyfinder(player, playerRef, keyfinderLevel, source);
    }

    private void processTokenFinder(Player player, PlayerRef playerRef, int level, String source) {
        EnchantsConfig.TokenFinder cfg = plugin.getEnchantsConfig().getTokenFinder();
        int maxLevel = cfg.getMaxLevel();
        if (!rollProc("token_finder", level, maxLevel, cfg.getEnchantProc(), source)) {
            return;
        }

        long awarded = (long) Math.max(0, level) * plugin.getTokensConfig().getTokensTimes();
        if (awarded <= 0) {
            return;
        }

        plugin.getTokenService().addTokens(playerRef.getUuid(), playerRef.getUsername(), awarded);

        String procMessage = MessageFormatter.format(cfg.getProcMessage(), Map.of(
                "amount", String.valueOf(awarded),
                "currency", plugin.getTokensConfig().getCurrencyName(),
                "enchant", "Token Finder",
                "level", String.valueOf(level),
                "crateId", "",
                "extra", ""
        ));
        if (!procMessage.isBlank()) {
            player.sendMessage(Message.raw(procMessage));
        }
    }

    private void processFortune(Player player, String blockId, int level, String source) {
        EnchantsConfig.Fortune cfg = plugin.getEnchantsConfig().getFortune();
        int maxLevel = cfg.getMaxLevel();
        if (!rollProc("fortune", level, maxLevel, cfg.getEnchantProc(), source)) {
            return;
        }

        int extraAmount = ThreadLocalRandom.current().nextInt(Math.max(0, level - 1), Math.max(1, level) + 1);
        if (extraAmount <= 0) {
            Debug.log("[Fortune] source=" + source + " rolled extraAmount=0 level=" + level + " blockId=" + blockId);
            return;
        }

        String cropKey = extractCropKey(blockId);
        if (cropKey == null || cropKey.isBlank()) {
            Debug.log("[Fortune] source=" + source + " could not extract crop key from blockId=" + blockId);
            return;
        }

        String itemId = "Plant_Crop_" + cropKey + "_Item";
        ItemStack extraStack = new ItemStack(itemId, extraAmount);
        ItemStackTransaction tx = player.getInventory().getCombinedEverything().addItemStack(extraStack);
        boolean success = tx != null && tx.succeeded();

        Debug.log("[Fortune] source=" + source + " blockId=" + blockId + " cropItem=" + itemId + " extraAmount=" + extraAmount + " grantSucceeded=" + success);

        if (success) {
            String procMessage = MessageFormatter.format(cfg.getProcMessage(), Map.of(
                    "amount", "",
                    "currency", plugin.getTokensConfig().getCurrencyName(),
                    "enchant", "Fortune",
                    "level", String.valueOf(level),
                    "crateId", "",
                    "extra", String.valueOf(extraAmount)
            ));
            if (!procMessage.isBlank()) {
                player.sendMessage(Message.raw(procMessage));
            }
        }
    }

    private void processKeyfinder(Player player, PlayerRef playerRef, int level, String source) {
        EnchantsConfig.Keyfinder cfg = plugin.getEnchantsConfig().getKeyfinder();
        int maxLevel = cfg.getMaxLevel();
        if (!rollProc("keyfinder", level, maxLevel, cfg.getEnchantProc(), source)) {
            return;
        }

        EnchantsConfig.Crate chosenCrate = chooseCrate(cfg.getCrates());
        if (chosenCrate == null) {
            Debug.warn("[Keyfinder] source=" + source + " proc succeeded but crate config is invalid; no command executed");
            return;
        }

        String finalCommand = chosenCrate.getCommand()
                .replace("{player}", playerRef.getUsername())
                .replace("<crateid>", chosenCrate.getCrateId());

        String normalizedCommand = finalCommand.startsWith("/") ? finalCommand.substring(1) : finalCommand;
        Debug.log("Keyfinder proc success -> source=" + source + " selected crateId=" + chosenCrate.getCrateId() + " command=" + normalizedCommand);
        CommandManager.get().handleCommand(player, normalizedCommand);

        String procMessage = MessageFormatter.format(cfg.getProcMessage(), Map.of(
                "amount", "",
                "currency", plugin.getTokensConfig().getCurrencyName(),
                "enchant", "Keyfinder",
                "level", String.valueOf(level),
                "crateId", chosenCrate.getCrateId(),
                "extra", ""
        ));
        if (!procMessage.isBlank()) {
            player.sendMessage(Message.raw(procMessage));
        }
    }

    private EnchantsConfig.Crate chooseCrate(List<EnchantsConfig.Crate> crates) {
        if (crates == null || crates.isEmpty()) {
            Debug.warn("[Keyfinder] no crates configured; no command executed");
            return null;
        }

        double totalWeight = 0;
        for (EnchantsConfig.Crate crate : crates) {
            totalWeight += crate.getCrateChance();
        }

        if (totalWeight <= 0D) {
            Debug.warn("[Keyfinder] all crate_chance weights are <= 0; no command executed");
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

    private boolean rollProc(String enchantId, int level, int maxLevel, double enchantProcFromJson, String source) {
        if (level <= 0) {
            Debug.log("[EnchantProc] source=" + source + " enchant=" + enchantId + " level=" + level + " maxLevel=" + maxLevel
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

        Debug.log("[EnchantProc] source=" + source
                + " enchant=" + enchantId
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

    public static void registerPendingUseHarvest(PlayerRef playerRef, int x, int y, int z, String heldItemId, String cachedBlockId) {
        String key = playerRef.getUuid() + ":" + x + ":" + y + ":" + z;
        PENDING_USE_HARVESTS.put(key, new PendingUseHarvestContext(heldItemId, cachedBlockId, System.currentTimeMillis()));
        Debug.log("[Harvest] registered pending Use context player=" + playerRef.getUsername() + " key=" + key
                + " heldItemId=" + heldItemId + " cachedBlockId=" + cachedBlockId);
    }

    private static PendingUseHarvestContext consumePendingUseHarvest(PlayerRef playerRef, int x, int y, int z) {
        String key = playerRef.getUuid() + ":" + x + ":" + y + ":" + z;
        PendingUseHarvestContext context = PENDING_USE_HARVESTS.remove(key);
        if (context == null) {
            return null;
        }

        long age = System.currentTimeMillis() - context.createdAtMs();
        if (age > PENDING_USE_WINDOW_MS) {
            Debug.log("[Harvest] pending Use context expired key=" + key + " ageMs=" + age);
            return null;
        }

        return context;
    }


    public static void markBreakEventObserved(PlayerRef playerRef, int x, int y, int z, String source) {
        String key = playerRef.getUuid() + ":" + x + ":" + y + ":" + z;
        RECENT_BREAK_EVENTS.put(key, System.currentTimeMillis());
        Debug.log("[Harvest] observed break event source=" + source + " key=" + key);
    }

    public static boolean hasRecentBreakEventObservation(PlayerRef playerRef, int x, int y, int z, long maxAgeMs) {
        String key = playerRef.getUuid() + ":" + x + ":" + y + ":" + z;
        Long ts = RECENT_BREAK_EVENTS.get(key);
        if (ts == null) {
            return false;
        }
        return (System.currentTimeMillis() - ts) <= Math.max(0L, maxAgeMs);
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

    private record PendingUseHarvestContext(String heldItemId, String cachedBlockId, long createdAtMs) {
    }
}
