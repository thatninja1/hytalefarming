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
import dev.hytalemodding.hytalefarming.util.FarmingTools;
import dev.hytalemodding.hytalefarming.util.MessageFormatter;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class TokenFinderBreakBlockSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private static final long DEDUPE_WINDOW_MS = 250L;
    private static final long PENDING_USE_WINDOW_MS = 1200L;
    private static final long RECENT_INTERACTION_WINDOW_MS = 500L;
    private static final long RADIUS_LOG_WINDOW_MS = 600L;
    private static final Map<String, Long> RECENT_HARVEST_REWARDS = new ConcurrentHashMap<>();
    private static final Map<String, PendingUseHarvestContext> PENDING_USE_HARVESTS = new ConcurrentHashMap<>();
    private static final Map<String, RecentSickleInteractionContext> RECENT_SICKLE_INTERACTIONS = new ConcurrentHashMap<>();
    private static final Map<String, Long> RECENT_BREAK_EVENTS = new ConcurrentHashMap<>();
    private static final Map<String, RadiusProcStats> RADIUS_PROC_STATS = new ConcurrentHashMap<>();

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

        if (!FarmingTools.isValidFarmingTool(eventHeldItemId) && target != null) {
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

        if (!FarmingTools.isValidFarmingTool(resolvedHeldItemId)) {
            RecentSickleInteractionContext context = resolveRecentSickleInteractionForBreak(playerRef, target);
            if (context != null) {
                resolvedHeldItemId = context.heldItemId();
                source = "Primary".equals(context.interactionType())
                        ? "PrimarySickleContextApplied"
                        : "SecondarySickleContextApplied";
                Debug.log("[CropBreak] source=" + source + " usedCachedContext=true cachedHeldItemId=" + resolvedHeldItemId
                        + " ageMs=" + (System.currentTimeMillis() - context.timestampMs())
                        + " interactionType=" + context.interactionType());
            } else {
                Debug.log("[CropBreak] no context attachment source=" + source + " reason=no_context_or_expired_or_out_of_range");
            }
        }

        moveDropsToInventoryIfPossible(event, player, source, blockId);
        handleCropBreakAndProcs(player, playerRef, resolvedHeldItemId, blockId, target, source, null);
    }

    public void handleCropBreakAndProcs(Player player,
                                        PlayerRef playerRef,
                                        String heldItemId,
                                        String brokenBlockId,
                                        Vector3i blockPos,
                                        String source) {
        handleCropBreakAndProcs(player, playerRef, heldItemId, brokenBlockId, blockPos, source, null);
    }

    public void handleCropBreakAndProcs(Player player,
                                        PlayerRef playerRef,
                                        String heldItemId,
                                        String brokenBlockId,
                                        Vector3i blockPos,
                                        String source,
                                        ProcBatch batch) {
        String normalizedBlockId = normalizeBlockId(brokenBlockId);

        Debug.log("[CropBreak] source=" + source
                + " player=" + playerRef.getUsername()
                + " heldItemId=" + heldItemId
                + " cachedBrokenBlockId=" + normalizedBlockId
                + " blockPos=" + (blockPos == null ? "<null>" : (blockPos.getX() + "," + blockPos.getY() + "," + blockPos.getZ())));

        if (!FarmingTools.isValidFarmingTool(heldItemId)) {
            Debug.log("[CropBreak] source=" + source + " rejected reason=not_farming_sickle heldItemId=" + heldItemId);
            return;
        }

        boolean validHarvestable = isValidHarvestableCrop(normalizedBlockId);
        Debug.log("[CropBreak] source=" + source + " fullyGrownValidationPassed=" + validHarvestable + " blockId=" + normalizedBlockId);
        if (!validHarvestable) {
            Debug.log("[CropBreak] source=" + source + " rejected reason=not_valid_fully_grown_crop blockId=" + normalizedBlockId);
            return;
        }

        if ("UseHarvest".equals(source)) {
            Debug.log("[Harvest] source=UseHarvest vanilla harvest observed via interaction; proc path continuing");
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

        if (batch != null) {
            batch.cropsProcessed++;
        }

        int tokenFinderLevel = plugin.getTokenService().enchantLevel(playerRef.getUuid(), playerRef.getUsername(), "token_finder");
        int fortuneLevel = plugin.getTokenService().enchantLevel(playerRef.getUuid(), playerRef.getUsername(), "fortune");
        int keyfinderLevel = plugin.getTokenService().enchantLevel(playerRef.getUuid(), playerRef.getUsername(), "keyfinder");

        Debug.log("[Drops] Could not intercept drop list; leaving vanilla drops for blockId=" + normalizedBlockId + " source=" + source);

        boolean tokenProc = processTokenFinder(player, playerRef, tokenFinderLevel, source, batch);
        boolean fortuneProc = processFortune(player, normalizedBlockId, fortuneLevel, source, batch);
        boolean keyfinderProc = processKeyfinder(player, playerRef, keyfinderLevel, source, batch);
        logRadiusSummary(playerRef, source, validHarvestable, tokenProc || fortuneProc || keyfinderProc);
    }

    private boolean processTokenFinder(Player player, PlayerRef playerRef, int level, String source, ProcBatch batch) {
        EnchantsConfig.TokenFinder cfg = plugin.getEnchantsConfig().getTokenFinder();
        int maxLevel = cfg.getMaxLevel();
        if (!rollProc("token_finder", level, maxLevel, cfg.getEnchantProc(), source)) {
            return false;
        }

        long awarded = (long) Math.max(0, level) * plugin.getTokensConfig().getTokensTimes();
        if (awarded <= 0) {
            return false;
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
        if (batch != null) {
            batch.totalTokensAwarded += awarded;
            batch.tokenFinderProcCount++;
        } else if (!procMessage.isBlank()) {
            player.sendMessage(Message.raw(procMessage));
        }
        return true;
    }

    private boolean processFortune(Player player, String blockId, int level, String source, ProcBatch batch) {
        EnchantsConfig.Fortune cfg = plugin.getEnchantsConfig().getFortune();
        int maxLevel = cfg.getMaxLevel();
        if (!rollProc("fortune", level, maxLevel, cfg.getEnchantProc(), source)) {
            return false;
        }

        int extraAmount = ThreadLocalRandom.current().nextInt(Math.max(0, level - 1), Math.max(1, level) + 1);
        if (extraAmount <= 0) {
            Debug.log("[Fortune] source=" + source + " rolled extraAmount=0 level=" + level + " blockId=" + blockId);
            return false;
        }

        String cropKey = extractCropKey(blockId);
        if (cropKey == null || cropKey.isBlank()) {
            Debug.log("[Fortune] source=" + source + " could not extract crop key from blockId=" + blockId);
            return false;
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
            if (batch != null) {
                batch.totalFortuneExtra += extraAmount;
                batch.fortuneProcCount++;
                batch.fortuneByItem.merge(itemId, extraAmount, Integer::sum);
            } else if (!procMessage.isBlank()) {
                player.sendMessage(Message.raw(procMessage));
            }
            return true;
        }
        return false;
    }

    private boolean processKeyfinder(Player player, PlayerRef playerRef, int level, String source, ProcBatch batch) {
        EnchantsConfig.Keyfinder cfg = plugin.getEnchantsConfig().getKeyfinder();
        int maxLevel = cfg.getMaxLevel();
        if (!rollProc("keyfinder", level, maxLevel, cfg.getEnchantProc(), source)) {
            return false;
        }

        EnchantsConfig.Crate chosenCrate = chooseCrate(cfg.getCrates());
        if (chosenCrate == null) {
            Debug.warn("[Keyfinder] source=" + source + " proc succeeded but crate config is invalid; no command executed");
            return false;
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
        if (batch != null) {
            batch.keyfinderProcCount++;
            batch.keyfinderByCrate.merge(chosenCrate.getCrateId(), 1, Integer::sum);
        } else if (!procMessage.isBlank()) {
            player.sendMessage(Message.raw(procMessage));
        }
        return true;
    }

    public void sendBatchSummaryIfAny(Player player, PlayerRef playerRef, ProcBatch batch, String source) {
        if (player == null || batch == null) {
            return;
        }
        if (batch.totalTokensAwarded <= 0 && batch.totalFortuneExtra <= 0 && batch.keyfinderByCrate.isEmpty()) {
            return;
        }

        List<String> lines = new ArrayList<>();
        if (batch.totalTokensAwarded > 0) {
            String line = MessageFormatter.format(plugin.getEnchantsConfig().getTokenFinder().getProcMessage(), Map.of(
                    "amount", String.valueOf(batch.totalTokensAwarded),
                    "currency", plugin.getTokensConfig().getCurrencyName(),
                    "enchant", "Token Finder",
                    "level", "",
                    "crateId", "",
                    "extra", ""
            ));
            if (!line.isBlank()) {
                lines.add(line);
            }
        }

        if (batch.totalFortuneExtra > 0) {
            String line = MessageFormatter.format(plugin.getEnchantsConfig().getFortune().getProcMessage(), Map.of(
                    "amount", "",
                    "currency", plugin.getTokensConfig().getCurrencyName(),
                    "enchant", "Fortune",
                    "level", "",
                    "crateId", "",
                    "extra", String.valueOf(batch.totalFortuneExtra)
            ));
            if (!line.isBlank()) {
                lines.add(line);
            }
        }

        if (!batch.keyfinderByCrate.isEmpty()) {
            String crateSummary = batch.keyfinderByCrate.entrySet().stream()
                    .map(e -> e.getKey() + " x" + e.getValue())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            String line = MessageFormatter.format(plugin.getEnchantsConfig().getKeyfinder().getProcMessage(), Map.of(
                    "amount", "",
                    "currency", plugin.getTokensConfig().getCurrencyName(),
                    "enchant", "Keyfinder",
                    "level", "",
                    "crateId", crateSummary,
                    "extra", ""
            ));
            if (!line.isBlank()) {
                lines.add(line);
            }
        }

        if (lines.isEmpty()) {
            return;
        }

        String aggregated = String.join("\n", lines);
        player.sendMessage(Message.raw(aggregated));

        Debug.log("[ProcBatch] source=" + source + " player=" + playerRef.getUsername()
                + " cropsProcessed=" + batch.cropsProcessed
                + " tokenTotal=" + batch.totalTokensAwarded
                + " fortuneExtra=" + batch.totalFortuneExtra
                + " keyfinderKeys=" + batch.keyfinderByCrate);
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


    public static void registerRecentSickleInteraction(PlayerRef playerRef,
                                                     String heldItemId,
                                                     Vector3i target,
                                                     String interactionType) {
        String key = playerRef.getUuid().toString();
        int x = target == null ? 0 : target.getX();
        int y = target == null ? 0 : target.getY();
        int z = target == null ? 0 : target.getZ();
        long now = System.currentTimeMillis();
        RECENT_SICKLE_INTERACTIONS.put(key, new RecentSickleInteractionContext(now, heldItemId, x, y, z, interactionType));
        Debug.log("[Harvest] stored recent interaction context player=" + playerRef.getUsername()
                + " interactionType=" + interactionType + " heldItemId=" + heldItemId
                + " target=" + x + "," + y + "," + z
                + " expiryAtMs=" + (now + RECENT_INTERACTION_WINDOW_MS));
    }

    public static RecentSickleInteractionContext getRecentSickleInteraction(PlayerRef playerRef) {
        String key = playerRef.getUuid().toString();
        RecentSickleInteractionContext context = RECENT_SICKLE_INTERACTIONS.get(key);
        if (context == null) {
            return null;
        }
        long age = System.currentTimeMillis() - context.timestampMs();
        if (age > RECENT_INTERACTION_WINDOW_MS) {
            RECENT_SICKLE_INTERACTIONS.remove(key);
            return null;
        }
        return context;
    }

    private static RecentSickleInteractionContext resolveRecentSickleInteractionForBreak(PlayerRef playerRef, Vector3i target) {
        RecentSickleInteractionContext context = getRecentSickleInteraction(playerRef);
        if (context == null) {
            return null;
        }

        if (target == null || (context.x() == 0 && context.y() == 0 && context.z() == 0)) {
            return context;
        }

        int dx = Math.abs(target.getX() - context.x());
        int dy = Math.abs(target.getY() - context.y());
        int dz = Math.abs(target.getZ() - context.z());
        if (dx > 8 || dy > 3 || dz > 8) {
            Debug.log("[CropBreak] context not attached reason=out_of_range dx=" + dx + " dy=" + dy + " dz=" + dz);
            return null;
        }

        return context;
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


    private void moveDropsToInventoryIfPossible(BreakBlockEvent event, Player player, String source, String blockId) {
        try {
            List<ItemStack> drops = tryExtractDrops(event);
            if (drops.isEmpty()) {
                Debug.log("[Drops] source=" + source + " blockId=" + blockId + " vanillaDropsCaptured=unknown fallback=ground");
                return;
            }

            int moved = 0;
            int essenceMoved = 0;
            for (ItemStack drop : drops) {
                if (drop == null || drop.getItemId() == null) {
                    continue;
                }
                ItemStackTransaction tx = player.getInventory().getCombinedEverything().addItemStack(drop);
                if (tx != null && tx.succeeded()) {
                    moved += 1;
                    if ("Ingredient_Life_Essence".equals(drop.getItemId())) {
                        essenceMoved += 1;
                    }
                }
            }

            Debug.log("[Drops] source=" + source + " blockId=" + blockId + " vanillaDropsCaptured=" + drops.size()
                    + " movedToInventory=" + moved + " vanillaEssenceMoved=" + essenceMoved + " fallback=none");
        } catch (Exception ex) {
            Debug.log("[Drops] source=" + source + " blockId=" + blockId + " drop-intercept failed=" + ex.getMessage() + " fallback=ground");
        }
    }

    private List<ItemStack> tryExtractDrops(BreakBlockEvent event) {
        List<ItemStack> extracted = new ArrayList<>();
        for (String methodName : List.of("getDrops", "getDropItems", "getItemDrops")) {
            try {
                Method method = event.getClass().getMethod(methodName);
                Object result = method.invoke(event);
                if (result instanceof Collection<?> collection) {
                    for (Object obj : collection) {
                        if (obj instanceof ItemStack stack) {
                            extracted.add(stack);
                        }
                    }
                    collection.clear();
                    if (!extracted.isEmpty()) {
                        return extracted;
                    }
                }
            } catch (Exception ignored) {
                // fallback to unknown behavior
            }
        }
        return extracted;
    }

    private void logRadiusSummary(PlayerRef playerRef, String source, boolean fullyGrown, boolean procTriggered) {
        String key = playerRef.getUuid() + ":" + source;
        long now = System.currentTimeMillis();
        RadiusProcStats stats = RADIUS_PROC_STATS.computeIfAbsent(key, ignored -> new RadiusProcStats(now));
        stats.processed++;
        if (fullyGrown) {
            stats.fullyGrown++;
        }
        if (procTriggered) {
            stats.procs++;
        }

        if ((now - stats.windowStartMs) >= RADIUS_LOG_WINDOW_MS) {
            Debug.log("[Radius] source=" + source + " player=" + playerRef.getUsername() + " processed=" + stats.processed
                    + " fullyGrown=" + stats.fullyGrown + " procs=" + stats.procs);
            RADIUS_PROC_STATS.put(key, new RadiusProcStats(now));
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

    private record PendingUseHarvestContext(String heldItemId, String cachedBlockId, long createdAtMs) {
    }

    public record RecentSickleInteractionContext(long timestampMs, String heldItemId, int x, int y, int z, String interactionType) {
    }

    public static final class ProcBatch {
        public long totalTokensAwarded;
        public int tokenFinderProcCount;
        public int totalFortuneExtra;
        public int fortuneProcCount;
        public int keyfinderProcCount;
        public int cropsProcessed;
        public final Map<String, Integer> fortuneByItem = new HashMap<>();
        public final Map<String, Integer> keyfinderByCrate = new HashMap<>();
    }

    private static final class RadiusProcStats {
        private final long windowStartMs;
        private int processed;
        private int fullyGrown;
        private int procs;

        private RadiusProcStats(long windowStartMs) {
            this.windowStartMs = windowStartMs;
        }
    }
}
