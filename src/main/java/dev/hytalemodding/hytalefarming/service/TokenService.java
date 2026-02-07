package dev.hytalemodding.hytalefarming.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.EntityStore;
import com.hypixel.hytale.server.core.entity.component.store.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.ref.Ref;
import dev.hytalemodding.hytalefarming.HytaleFarmingPlugin;
import dev.hytalemodding.hytalefarming.components.PlayerTokenData;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TokenService {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final HytaleFarmingPlugin plugin;
    private final Path ledgerPath;
    private final Map<UUID, LedgerEntry> ledger;

    public TokenService(HytaleFarmingPlugin plugin) {
        this.plugin = plugin;
        this.ledgerPath = plugin.getDataFolder().toPath().resolve("token-ledger.json");
        this.ledger = loadLedger();
    }

    public PlayerTokenData data(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerRef) {
        return store.ensureAndGetComponent(playerRef, plugin.getPlayerTokenDataComponent());
    }

    public long balance(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerRef) {
        return data(store, playerRef).getBalance();
    }

    public int enchantLevel(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerRef, String key) {
        return data(store, playerRef).getEnchantLevel(key);
    }

    public void addTokens(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerRef, @Nonnull Player player, long amount) {
        if (amount <= 0) {
            return;
        }
        PlayerTokenData data = data(store, playerRef);
        data.setBalance(data.getBalance() + amount);
        updateLedger(player.getUuid(), player.getDisplayName(), data.getBalance());
    }

    public boolean transfer(@Nonnull Store<EntityStore> store,
                            @Nonnull Ref<EntityStore> senderRef,
                            @Nonnull Player sender,
                            @Nonnull Ref<EntityStore> targetRef,
                            @Nonnull Player target,
                            long amount) {
        if (amount <= 0) {
            return false;
        }
        PlayerTokenData senderData = data(store, senderRef);
        if (senderData.getBalance() < amount) {
            return false;
        }
        senderData.setBalance(senderData.getBalance() - amount);
        updateLedger(sender.getUuid(), sender.getDisplayName(), senderData.getBalance());
        addTokens(store, targetRef, target, amount);
        return true;
    }

    public boolean tryUpgradeTokenFinder(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerRef) {
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return false;
        }

        PlayerTokenData data = data(store, playerRef);
        int current = data.getEnchantLevel("token_finder");
        int max = plugin.getEnchantsConfig().getTokenFinder().getMaxLevel();
        if (current >= max) {
            player.sendMessage(Message.raw("Token Finder already at max level."));
            return false;
        }

        int cost = plugin.getEnchantsConfig().getTokenFinder().getUpgradeCost(current);
        if (data.getBalance() < cost) {
            player.sendMessage(Message.raw("Not enough " + plugin.getTokensConfig().getCurrencyName() + "."));
            return false;
        }

        data.setBalance(data.getBalance() - cost);
        data.setEnchantLevel("token_finder", current + 1);
        updateLedger(player.getUuid(), player.getDisplayName(), data.getBalance());
        player.sendMessage(Message.raw("Token Finder upgraded to level " + (current + 1) + "."));
        return true;
    }

    public List<LedgerEntry> top(int limit) {
        ArrayList<LedgerEntry> top = new ArrayList<>(ledger.values());
        top.sort(Comparator.comparingLong(LedgerEntry::balance).reversed());
        return top.subList(0, Math.min(limit, top.size()));
    }

    private Map<UUID, LedgerEntry> loadLedger() {
        try {
            if (Files.notExists(ledgerPath)) {
                Files.createDirectories(ledgerPath.getParent());
                Files.writeString(ledgerPath, "{}");
                return new HashMap<>();
            }
            LedgerFile file = GSON.fromJson(Files.readString(ledgerPath), LedgerFile.class);
            return file == null || file.entries == null ? new HashMap<>() : file.entries;
        } catch (IOException e) {
            throw new IllegalStateException("Failed loading token ledger", e);
        }
    }

    private void updateLedger(UUID uuid, String name, long balance) {
        ledger.put(uuid, new LedgerEntry(uuid, name, balance));
        try {
            LedgerFile file = new LedgerFile();
            file.entries = ledger;
            Files.writeString(ledgerPath, GSON.toJson(file));
        } catch (IOException e) {
            throw new IllegalStateException("Failed writing token ledger", e);
        }
    }

    public record LedgerEntry(UUID uuid, String name, long balance) {
    }

    public static class LedgerFile {
        private Map<UUID, LedgerEntry> entries = new HashMap<>();
    }
}
