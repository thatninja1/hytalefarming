package dev.hytalemodding.hytalefarming.components;

import java.util.HashMap;
import java.util.Map;

public class PlayerTokenData {
    private long balance;
    private final Map<String, Integer> enchants;

    public PlayerTokenData() {
        this.balance = 0;
        this.enchants = new HashMap<>();
    }

    public long getBalance() {
        return balance;
    }

    public void setBalance(long balance) {
        this.balance = Math.max(0, balance);
    }

    public int getEnchantLevel(String key) {
        return enchants.getOrDefault(key, 0);
    }

    public void setEnchantLevel(String key, int level) {
        enchants.put(key, Math.max(level, 0));
    }

    public Map<String, Integer> getEnchants() {
        return enchants;
    }
}
