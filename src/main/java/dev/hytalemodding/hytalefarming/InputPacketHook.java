package dev.hytalemodding.hytalefarming;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionChainData;
import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.hytalefarming.events.TokenFinderBreakBlockSystem;
import dev.hytalemodding.hytalefarming.util.FarmingTools;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class InputPacketHook {
    private static final long BREAK_OBSERVE_DELAY_MS = 100L;

    private final HytaleFarmingPlugin plugin;
    private final Map<UUID, Integer> packetCounts = new ConcurrentHashMap<>();
    private PacketFilter syncWatcher;
    private PacketFilter counterWatcher;
    private ScheduledExecutorService counterPrinter;
    private ScheduledExecutorService postUseVerifier;

    public InputPacketHook(HytaleFarmingPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        this.syncWatcher = PacketAdapters.registerInbound((PlayerRef playerRef, Packet packet) -> {
            if (!(packet instanceof SyncInteractionChains chains) || chains.updates == null) {
                return;
            }

            for (SyncInteractionChain update : chains.updates) {
                if (update == null || update.interactionType == null) {
                    continue;
                }

                InteractionType type = update.interactionType;
                String heldItemId = update.itemInHandId == null ? "<null>" : update.itemInHandId;
                Debug.log("[FarmingDebug] SyncInteractionChains: player=" + playerRef.getUsername() + " type=" + type + " heldItemId=" + heldItemId);

                if (type == InteractionType.Secondary) {
                    handleSecondary(playerRef, heldItemId);
                    continue;
                }

                if (type == InteractionType.Use) {
                    handleUseHarvest(playerRef, heldItemId, update);
                    continue;
                }

                Debug.log("[FarmingDebug] ignored interaction type=" + type + " player=" + playerRef.getUsername() + " heldItemId=" + heldItemId + " reason=unsupported_type");
            }
        });

        this.counterWatcher = PacketAdapters.registerInbound((PlayerRef playerRef, Packet packet) ->
                packetCounts.merge(playerRef.getUuid(), 1, Integer::sum)
        );

        this.counterPrinter = Executors.newSingleThreadScheduledExecutor();
        this.counterPrinter.scheduleAtFixedRate(() -> {
            if (packetCounts.isEmpty()) {
                return;
            }
            for (Map.Entry<UUID, Integer> e : packetCounts.entrySet()) {
                Debug.log("[FarmingDebug] packetCounter playerUuid=" + e.getKey() + " packetsInLastWindow=" + e.getValue());
            }
            packetCounts.clear();
        }, 3, 3, TimeUnit.SECONDS);

        this.postUseVerifier = Executors.newSingleThreadScheduledExecutor();

        Debug.log("Registered inbound packet watchers (input hook)");
    }

    private void handleSecondary(PlayerRef playerRef, String heldItemId) {
        if (!FarmingTools.isValidFarmingTool(heldItemId)) {
            Debug.log("[FarmingDebug] ignored interaction type=Secondary player=" + playerRef.getUsername()
                    + " heldItemId=" + heldItemId + " reason=not_farming_sickle");
            return;
        }

        Debug.log("[FarmingDebug] accepted interaction type=Secondary player=" + playerRef.getUsername()
                + " heldItemId=" + heldItemId + " -> opening UI");
        plugin.openUpgradeUiSafe(playerRef, null, InteractionType.Secondary.name(), heldItemId);
    }

    private void handleUseHarvest(PlayerRef playerRef, String heldItemId, SyncInteractionChain update) {
        if (!FarmingTools.isValidFarmingTool(heldItemId)) {
            Debug.log("[FarmingDebug] ignored interaction type=Use player=" + playerRef.getUsername()
                    + " heldItemId=" + heldItemId + " reason=not_farming_sickle");
            return;
        }

        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            Debug.log("[FarmingDebug] ignored interaction type=Use player=" + playerRef.getUsername() + " reason=invalid_player_ref");
            return;
        }

        Store<EntityStore> store = ref.getStore();
        EntityStore entityStore = store.getExternalData();
        World world = entityStore.getWorld();

        Vector3i target = resolveTargetBlock(update);
        if (target == null) {
            Debug.log("[FarmingDebug] ignored interaction type=Use player=" + playerRef.getUsername()
                    + " heldItemId=" + heldItemId + " reason=no_target_block");
            return;
        }

        world.execute(() -> {
            String blockIdBefore = "<unknown>";
            try {
                if (world.getBlockType(target.getX(), target.getY(), target.getZ()) != null) {
                    blockIdBefore = TokenFinderBreakBlockSystem.normalizeBlockId(world.getBlockType(target.getX(), target.getY(), target.getZ()).getId());
                }

                String cachedHeldItemId = heldItemId;
                String cachedBrokenBlockId = blockIdBefore;

                boolean harvestable = TokenFinderBreakBlockSystem.isValidHarvestableCrop(cachedBrokenBlockId);
                Debug.log("[FarmingDebug] interaction type=Use player=" + playerRef.getUsername()
                        + " heldItemId=" + cachedHeldItemId
                        + " target=" + target.getX() + "," + target.getY() + "," + target.getZ()
                        + " targetBlockId=" + cachedBrokenBlockId
                        + " treatedAsHarvest=" + harvestable);

                if (!harvestable) {
                    Debug.log("[FarmingDebug] ignored interaction type=Use player=" + playerRef.getUsername()
                            + " heldItemId=" + cachedHeldItemId + " reason=target_not_valid_fully_grown_crop blockId=" + cachedBrokenBlockId);
                    return;
                }

                TokenFinderBreakBlockSystem.registerPendingUseHarvest(
                        playerRef,
                        target.getX(),
                        target.getY(),
                        target.getZ(),
                        heldItemId,
                        cachedBrokenBlockId
                );

                Debug.log("[Harvest] registered pending Use context source=UseHarvest player=" + playerRef.getUsername()
                        + " target=" + target.getX() + "," + target.getY() + "," + target.getZ()
                        + " cachedHeldItemId=" + cachedHeldItemId
                        + " cachedBrokenBlockId=" + cachedBrokenBlockId);

                Debug.log("[Harvest] scheduled delayed harvest verification source=UseHarvest delayMs=" + BREAK_OBSERVE_DELAY_MS
                        + " target=" + target.getX() + "," + target.getY() + "," + target.getZ());

                postUseVerifier.schedule(() -> world.execute(() -> {
                    String blockIdAfter = "<unknown>";
                    boolean blockStillFullyGrown = false;

                    if (world.getBlockType(target.getX(), target.getY(), target.getZ()) != null) {
                        blockIdAfter = TokenFinderBreakBlockSystem.normalizeBlockId(world.getBlockType(target.getX(), target.getY(), target.getZ()).getId());
                    }
                    blockStillFullyGrown = TokenFinderBreakBlockSystem.isValidHarvestableCrop(blockIdAfter);

                    boolean breakEventObserved = TokenFinderBreakBlockSystem.hasRecentBreakEventObservation(
                            playerRef,
                            target.getX(),
                            target.getY(),
                            target.getZ(),
                            2000L
                    );

                    Debug.log("[Harvest] delayed verification source=UseHarvest target="
                            + target.getX() + "," + target.getY() + "," + target.getZ()
                            + " blockBefore=" + cachedBrokenBlockId
                            + " blockAfter=" + blockIdAfter
                            + " blockStillFullyGrown=" + blockStillFullyGrown
                            + " breakEventObserved=" + breakEventObserved);

                    if (blockStillFullyGrown) {
                        Debug.log("[Harvest] skipped proc pipeline source=UseHarvest reason=vanilla_harvest_not_observed target="
                                + target.getX() + "," + target.getY() + "," + target.getZ());
                        return;
                    }

                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player == null) {
                        Debug.log("[Harvest] skipped proc pipeline source=UseHarvest reason=player_component_missing");
                        return;
                    }

                    Debug.log("[Harvest] invoking proc pipeline source=UseHarvest target="
                            + target.getX() + "," + target.getY() + "," + target.getZ()
                            + " cachedHeldItemId=" + cachedHeldItemId
                            + " cachedBrokenBlockId=" + cachedBrokenBlockId);

                    plugin.getTokenFinderBreakBlockSystem().handleCropBreakAndProcs(
                            player,
                            playerRef,
                            cachedHeldItemId,
                            cachedBrokenBlockId,
                            target,
                            "UseHarvest"
                    );
                }), BREAK_OBSERVE_DELAY_MS, TimeUnit.MILLISECONDS);
            } catch (Exception ex) {
                Debug.log("[FarmingDebug] Use harvest failed player=" + playerRef.getUsername()
                        + " target=" + target.getX() + "," + target.getY() + "," + target.getZ()
                        + " blockId=" + blockIdBefore + " error=" + ex.getMessage());
            }
        });
    }

    private Vector3i resolveTargetBlock(SyncInteractionChain update) {
        InteractionChainData chainData = update.data;
        if (chainData != null && chainData.blockPosition != null) {
            return new Vector3i(chainData.blockPosition.x, chainData.blockPosition.y, chainData.blockPosition.z);
        }

        InteractionSyncData[] syncData = update.interactionData;
        if (syncData != null) {
            for (InteractionSyncData data : syncData) {
                if (data == null) {
                    continue;
                }
                if (data.blockPosition != null) {
                    return new Vector3i(data.blockPosition.x, data.blockPosition.y, data.blockPosition.z);
                }
                if (data.raycastHit != null) {
                    return new Vector3i((int) Math.floor(data.raycastHit.x), (int) Math.floor(data.raycastHit.y), (int) Math.floor(data.raycastHit.z));
                }
            }
        }

        return null;
    }

    public void unregister() {
        if (syncWatcher != null) {
            PacketAdapters.deregisterInbound(syncWatcher);
            syncWatcher = null;
        }
        if (counterWatcher != null) {
            PacketAdapters.deregisterInbound(counterWatcher);
            counterWatcher = null;
        }
        if (counterPrinter != null) {
            counterPrinter.shutdownNow();
            counterPrinter = null;
        }
        if (postUseVerifier != null) {
            postUseVerifier.shutdownNow();
            postUseVerifier = null;
        }
    }
}
