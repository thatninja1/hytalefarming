package dev.hytalemodding.hytalefarming;

import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class InputPacketHook {
    private final HytaleFarmingPlugin plugin;
    private final Map<UUID, Integer> packetCounts = new ConcurrentHashMap<>();
    private PacketFilter syncWatcher;
    private PacketFilter counterWatcher;
    private ScheduledExecutorService counterPrinter;

    public InputPacketHook(HytaleFarmingPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        this.syncWatcher = PacketAdapters.registerInbound((PlayerRef playerRef, Packet packet) -> {
            if (!(packet instanceof SyncInteractionChains chains)) {
                return;
            }
            if (chains.updates == null) {
                return;
            }

            for (SyncInteractionChain update : chains.updates) {
                if (update == null || update.interactionType == null) {
                    continue;
                }

                InteractionType type = update.interactionType;
                Debug.log("[HoeDebug] SyncInteractionChains: player=" + playerRef.getUsername() + " type=" + type);

                if (type != InteractionType.Secondary && type != InteractionType.Use) {
                    continue;
                }

                String packetItemId = update.itemInHandId == null ? "<null>" : update.itemInHandId;
                String serverItemId = plugin.resolveHeldItemId(playerRef);
                String itemId = "<none>".equals(serverItemId) ? packetItemId : serverItemId;
                boolean match = "Tool_Hoe_Thorium".equals(itemId);

                Debug.log("[HoeDebug] heldItemId=" + itemId + " packetItemId=" + packetItemId + " serverItemId=" + serverItemId + " match=" + match);

                if (match) {
                    plugin.openUpgradeUiFromPacket(playerRef, type.name());
                }
            }
        });

        this.counterWatcher = PacketAdapters.registerInbound((PlayerRef playerRef, Packet packet) -> {
            packetCounts.merge(playerRef.getUuid(), 1, Integer::sum);
        });

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

        Debug.log("Registered inbound packet watchers (input hook)");
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
    }
}
