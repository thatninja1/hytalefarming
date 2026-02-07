package dev.hytalemodding.hytalefarming.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class EnchantsConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private TokenFinder tokenFinder = new TokenFinder();

    public static EnchantsConfig load(Path path) {
        try {
            if (Files.notExists(path)) {
                EnchantsConfig defaults = new EnchantsConfig();
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(defaults));
                return defaults;
            }
            EnchantsConfig loaded = GSON.fromJson(Files.readString(path), EnchantsConfig.class);
            return loaded == null ? new EnchantsConfig() : loaded;
        } catch (IOException e) {
            throw new IllegalStateException("Failed loading enchants config", e);
        }
    }

    public TokenFinder getTokenFinder() {
        return tokenFinder == null ? new TokenFinder() : tokenFinder;
    }

    public static class TokenFinder {
        private int maxLevel = 10;
        private int baseUpgradeCost = 10;

        public int getMaxLevel() {
            return Math.max(1, maxLevel);
        }

        public int getUpgradeCost(int currentLevel) {
            return Math.max(1, baseUpgradeCost) * (currentLevel + 1);
        }
    }
}
