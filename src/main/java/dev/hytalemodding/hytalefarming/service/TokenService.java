package dev.hytalemodding.hytalefarming.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.hytalemodding.hytalefarming.components.PlayerTokenData;
import dev.hytalemodding.hytalefarming.config.EnchantsConfig;
import dev.hytalemodding.hytalefarming.config.TokensConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntFunction;

public class TokenService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path dbPath;
    private final TokensConfig tokensConfig;
    private final EnchantsConfig enchantsConfig;
    private final Database database;

    public TokenService(Path dataDirectory, TokensConfig tokensConfig, EnchantsConfig enchantsConfig) {
        this.dbPath = dataDirectory.resolve("player-data.json");
        this.tokensConfig = tokensConfig;
        this.enchantsConfig = enchantsConfig;
        this.database = load();
    }

    public synchronized long balance(UUID playerId, String playerName) {
        return account(playerId, playerName).data.getBalance();
    }

    public synchronized int enchantLevel(UUID playerId, String playerName, String enchantKey) {
        PlayerAccount account = account(playerId, playerName);
        if (ensureDefaultEnchantLevels(account)) {
            save();
        }
        return account.data.getEnchantLevel(enchantKey);
    }

    public synchronized void addTokens(UUID playerId, String playerName, long amount) {
        if (amount <= 0) return;
        PlayerAccount account = account(playerId, playerName);
        account.data.setBalance(account.data.getBalance() + amount);
        save();
    }

    public synchronized boolean pay(UUID fromId, String fromName, UUID toId, String toName, long amount) {
        if (amount <= 0) return false;
        PlayerAccount from = account(fromId, fromName);
        if (from.data.getBalance() < amount) return false;
        PlayerAccount to = account(toId, toName);
        from.data.setBalance(from.data.getBalance() - amount);
        to.data.setBalance(to.data.getBalance() + amount);
        save();
        return true;
    }

    public synchronized boolean tryUpgradeTokenFinder(UUID playerId, String playerName) {
        return tryUpgradeEnchant(playerId, playerName, "token_finder", enchantsConfig.getTokenFinder().getMaxLevel(),
                current -> enchantsConfig.getTokenFinder().getUpgradeCost(current));
    }

    public synchronized boolean tryUpgradeFortune(UUID playerId, String playerName) {
        return tryUpgradeEnchant(playerId, playerName, "fortune", enchantsConfig.getFortune().getMaxLevel(),
                current -> enchantsConfig.getFortune().getUpgradeCost(current));
    }

    public synchronized boolean tryUpgradeKeyfinder(UUID playerId, String playerName) {
        return tryUpgradeEnchant(playerId, playerName, "keyfinder", enchantsConfig.getKeyfinder().getMaxLevel(),
                current -> enchantsConfig.getKeyfinder().getUpgradeCost(current));
    }

    public synchronized boolean tryUpgradeEternalGrowth(UUID playerId, String playerName) {
        return tryUpgradeEnchant(playerId, playerName, "eternal_growth", enchantsConfig.getEternalGrowth().getMaxLevel(),
                current -> enchantsConfig.getEternalGrowth().getUpgradeCost(current));
    }

    public synchronized void addTokensForProc(UUID playerId, String playerName, long amount) {
        addTokens(playerId, playerName, amount);
    }


    private boolean tryUpgradeEnchant(UUID playerId, String playerName, String enchantKey, int maxLevel, IntFunction<Integer> costProvider) {
        PlayerAccount account = account(playerId, playerName);
        int current = account.data.getEnchantLevel(enchantKey);
        if (current >= maxLevel) return false;

        int cost = costProvider.apply(Math.max(0, current));
        if (account.data.getBalance() < cost) return false;

        account.data.setBalance(account.data.getBalance() - cost);
        account.data.setEnchantLevel(enchantKey, current + 1);
        save();
        return true;
    }

    public synchronized List<LeaderboardEntry> top(int limit) {
        ArrayList<LeaderboardEntry> entries = new ArrayList<>();
        for (PlayerAccount account : database.players.values()) {
            entries.add(new LeaderboardEntry(account.playerId, account.playerName, account.data.getBalance()));
        }
        entries.sort(Comparator.comparingLong(LeaderboardEntry::balance).reversed());
        return entries.subList(0, Math.min(limit, entries.size()));
    }

    private PlayerAccount account(UUID id, String name) {
        PlayerAccount existing = database.players.get(id.toString());
        if (existing != null) {
            existing.playerName = name;
            if (ensureDefaultEnchantLevels(existing)) {
                save();
            }
            return existing;
        }
        PlayerAccount created = new PlayerAccount();
        created.playerId = id;
        created.playerName = name;
        created.data = new PlayerTokenData();
        ensureDefaultEnchantLevels(created);
        database.players.put(id.toString(), created);
        return created;
    }

    private boolean ensureDefaultEnchantLevels(PlayerAccount account) {
        if (account == null || account.data == null) {
            return false;
        }

        if (!account.data.getEnchants().containsKey("token_finder")) {
            account.data.setEnchantLevel("token_finder", enchantsConfig.getTokenFinder().getDefaultLevel());
            return true;
        }

        return false;
    }

    private Database load() {
        try {
            if (Files.notExists(dbPath)) {
                Files.createDirectories(dbPath.getParent());
                Database defaults = new Database();
                Files.writeString(dbPath, GSON.toJson(defaults));
                return defaults;
            }
            Database loaded = GSON.fromJson(Files.readString(dbPath), Database.class);
            return loaded == null ? new Database() : loaded;
        } catch (IOException e) {
            throw new IllegalStateException("Failed loading token database", e);
        }
    }

    private void save() {
        try {
            Files.writeString(dbPath, GSON.toJson(database));
        } catch (IOException e) {
            throw new IllegalStateException("Failed saving token database", e);
        }
    }

    public record LeaderboardEntry(UUID playerId, String playerName, long balance) {}

    private static class Database {
        Map<String, PlayerAccount> players = new HashMap<>();
    }

    private static class PlayerAccount {
        UUID playerId;
        String playerName;
        PlayerTokenData data;
    }
}
