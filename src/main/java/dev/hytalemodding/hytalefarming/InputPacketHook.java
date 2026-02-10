package dev.hytalemodding.hytalefarming;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class InputPacketHook {
    private static final long BREAK_OBSERVE_DELAY_MS = 200L;

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
                Debug.log("[HoeDebug] SyncInteractionChains: player=" + playerRef.getUsername() + " type=" + type + " heldItemId=" + heldItemId);

                if (type == InteractionType.Secondary) {
                    handleSecondary(playerRef, heldItemId);
                    continue;
                }

                if (type == InteractionType.Use) {
                    handleUseHarvest(playerRef, heldItemId, update);
                    continue;
                }

                Debug.log("[HoeDebug] ignored interaction type=" + type + " player=" + playerRef.getUsername() + " heldItemId=" + heldItemId + " reason=unsupported_type");
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
                Debug.log("[HoeDebug] packetCounter playerUuid=" + e.getKey() + " packetsInLastWindow=" + e.getValue());
            }
            packetCounts.clear();
        }, 3, 3, TimeUnit.SECONDS);

        this.postUseVerifier = Executors.newSingleThreadScheduledExecutor();

        Debug.log("Registered inbound packet watchers (input hook)");
    }

    private void handleSecondary(PlayerRef playerRef, String heldItemId) {
        if (!"Tool_Hoe_Thorium".equals(heldItemId)) {
            Debug.log("[HoeDebug] ignored interaction type=Secondary player=" + playerRef.getUsername()
                    + " heldItemId=" + heldItemId + " reason=non_thorium_hoe");
            return;
        }

        Debug.log("[HoeDebug] accepted interaction type=Secondary player=" + playerRef.getUsername()
                + " heldItemId=" + heldItemId + " -> opening UI");
        plugin.openUpgradeUiSafe(playerRef, null, InteractionType.Secondary.name(), heldItemId);
    }

    private void handleUseHarvest(PlayerRef playerRef, String heldItemId, SyncInteractionChain update) {
        if (!"Tool_Hoe_Thorium".equals(heldItemId)) {
            Debug.log("[HoeDebug] ignored interaction type=Use player=" + playerRef.getUsername()
                    + " heldItemId=" + heldItemId + " reason=non_thorium_hoe");
            return;
        }

        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            Debug.log("[HoeDebug] ignored interaction type=Use player=" + playerRef.getUsername() + " reason=invalid_player_ref");
            return;
        }

        Store<EntityStore> store = ref.getStore();
        EntityStore entityStore = store.getExternalData();
        World world = entityStore.getWorld();

        Vector3i target = resolveTargetBlock(update);
        if (target == null) {
            Debug.log("[HoeDebug] ignored interaction type=Use player=" + playerRef.getUsername()
                    + " heldItemId=" + heldItemId + " reason=no_target_block");
            return;
        }

        world.execute(() -> {
            String blockId = "<unknown>";
            try {
                if (world.getBlockType(target.getX(), target.getY(), target.getZ()) != null) {
                    blockId = TokenFinderBreakBlockSystem.normalizeBlockId(world.getBlockType(target.getX(), target.getY(), target.getZ()).getId());
                }

                String cachedHeldItemId = heldItemId;
                String cachedBrokenBlockId = blockId;

                boolean harvestable = TokenFinderBreakBlockSystem.isValidHarvestableCrop(cachedBrokenBlockId);
                Debug.log("[HoeDebug] interaction type=Use player=" + playerRef.getUsername()
                        + " heldItemId=" + cachedHeldItemId
                        + " target=" + target.getX() + "," + target.getY() + "," + target.getZ()
                        + " targetBlockId=" + cachedBrokenBlockId
                        + " treatedAsHarvest=" + harvestable);

                if (!harvestable) {
                    Debug.log("[HoeDebug] ignored interaction type=Use player=" + playerRef.getUsername()
                            + " heldItemId=" + cachedHeldItemId + " reason=target_not_valid_fully_grown_crop blockId=" + cachedBrokenBlockId);
                    return;
                }

                Player player = store.getComponent(ref, Player.getComponentType());
                if (player == null) {
                    Debug.log("[Harvest] warning source=UseHarvest player component missing; cannot run player-context break");
                    return;
                }

                int packetEntityId = resolveBreakerEntityIdFromPacket(update);
                int refIndexEntityId = ref.getIndex();
                int playerRefIndexEntityId = player.getReference() == null ? 0 : player.getReference().getIndex();
                int breakerEntityId = firstPositive(packetEntityId, playerRefIndexEntityId, refIndexEntityId);

                int packetHotbarSlot = Math.max(0, update.activeHotbarSlot);
                int inventoryHotbarSlot = Math.max(0, player.getInventory().getActiveHotbarSlot());
                int activeHotbarSlot = inventoryHotbarSlot;

                Debug.log("[Harvest] source=UseHarvest breaker candidates packetEntityId=" + packetEntityId
                        + " playerRefIndexEntityId=" + playerRefIndexEntityId
                        + " refIndexEntityId=" + refIndexEntityId
                        + " chosenBreakerEntityId=" + breakerEntityId
                        + " chosenBreakerIdSource=" + breakerIdSource(packetEntityId, playerRefIndexEntityId, refIndexEntityId)
                        + " packetHotbarSlot=" + packetHotbarSlot
                        + " inventoryHotbarSlot=" + inventoryHotbarSlot
                        + " chosenHotbarSlot=" + activeHotbarSlot
                        + " chosenHotbarSlotSource=player.inventory.getActiveHotbarSlot()");

                if (breakerEntityId <= 0) {
                    Debug.log("[Harvest] warning source=UseHarvest chosen breakerEntityId <= 0; cannot guarantee vanilla break pipeline");
                }

                TokenFinderBreakBlockSystem.registerPendingUseHarvest(
                        playerRef,
                        target.getX(),
                        target.getY(),
                        target.getZ(),
                        cachedHeldItemId,
                        cachedBrokenBlockId
                );

                boolean broke = false;
                String breakPath = "none";
                int localX = ChunkUtil.localCoordinate(target.getX());
                int localZ = ChunkUtil.localCoordinate(target.getZ());
                long chunkIndex = ChunkUtil.indexChunkFromBlock(target.getX(), target.getZ());

                try {
                    broke = world.breakBlock(target.getX(), target.getY(), target.getZ(), Math.max(0, breakerEntityId));
                    breakPath = "world.breakBlock(x,y,z,breakerEntityId)";
                } catch (Exception ignored) {
                    // fallback below
                }

                if (!broke) {
                    try {
                        var chunk = world.getChunk(chunkIndex);
                        if (chunk != null) {
                            broke = chunk.breakBlock(localX, target.getY(), localZ, Math.max(0, breakerEntityId), activeHotbarSlot);
                            breakPath = "chunk.breakBlock(localX,y,localZ,breakerEntityId,activeHotbarSlot)_fallback";
                        }
                    } catch (Exception ignored) {
                        // final fallback below
                    }
                }

                if (!broke) {
                    broke = world.breakBlock(target.getX(), target.getY(), target.getZ(), 0);
                    breakPath = "world.breakBlock(x,y,z,0_last_resort)";
                }

                Debug.log("[Harvest] usingRealBreak=true source=UseHarvest player=" + playerRef.getUsername()
                        + " target=" + target.getX() + "," + target.getY() + "," + target.getZ()
                        + " cachedHeldItemId=" + cachedHeldItemId
                        + " cachedBrokenBlockId=" + cachedBrokenBlockId
                        + " breakPath=" + breakPath
                        + " activeHotbarSlot=" + activeHotbarSlot
                        + " breakerEntityId=" + breakerEntityId
                        + " chunkIndex=" + chunkIndex
                        + " localX=" + localX + " localZ=" + localZ
                        + " breakResult=" + broke);

                Debug.log("[Harvest] vanillaDropsCaptured=unknown source=UseHarvest mode=engine_real_break");
                Debug.log("[Harvest] vanillaEssenceCaptured=unknown source=UseHarvest mode=engine_real_break");

                final boolean breakCallResult = broke;

                postUseVerifier.schedule(() -> world.execute(() -> {
                    boolean breakEventObserved = TokenFinderBreakBlockSystem.hasRecentBreakEventObservation(
                            playerRef,
                            target.getX(),
                            target.getY(),
                            target.getZ(),
                            2000L
                    );

                    Debug.log("[Harvest] breakEventObservedAfterUse=" + breakEventObserved + " source=UseHarvest target="
                            + target.getX() + "," + target.getY() + "," + target.getZ());

                    if (breakCallResult && !breakEventObserved) {
                        Debug.log("[Harvest] warning source=UseHarvest break succeeded but no BreakBlockEvent observed; applying fallback proc pipeline");
                        plugin.getTokenFinderBreakBlockSystem().handleCropBreakAndProcs(
                                player,
                                playerRef,
                                cachedHeldItemId,
                                cachedBrokenBlockId,
                                target,
                                "UseHarvestFallback"
                        );
                    }
                }), BREAK_OBSERVE_DELAY_MS, TimeUnit.MILLISECONDS);
            } catch (Exception ex) {
                Debug.log("[HoeDebug] Use harvest failed player=" + playerRef.getUsername()
                        + " target=" + target.getX() + "," + target.getY() + "," + target.getZ()
                        + " blockId=" + blockId + " error=" + ex.getMessage());
            }
        });
    }

    private int resolveBreakerEntityIdFromPacket(SyncInteractionChain update) {
        if (update.data != null && update.data.entityId > 0) {
            return update.data.entityId;
        }
        if (update.interactionData != null) {
            for (InteractionSyncData d : update.interactionData) {
                if (d != null && d.entityId > 0) {
                    return d.entityId;
                }
            }
        }
        return 0;
    }

    private int firstPositive(int... values) {
        for (int value : values) {
            if (value > 0) {
                return value;
            }
        }
        return 0;
    }

    private String breakerIdSource(int packetEntityId, int playerRefIndexEntityId, int refIndexEntityId) {
        if (packetEntityId > 0) {
            return "interaction_packet_entityId";
        }
        if (playerRefIndexEntityId > 0) {
            return "player.reference.index";
        }
        if (refIndexEntityId > 0) {
            return "playerRef.reference.index";
        }
        return "none";
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
