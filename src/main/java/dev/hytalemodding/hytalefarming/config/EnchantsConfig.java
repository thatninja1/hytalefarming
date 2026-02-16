package dev.hytalemodding.hytalefarming.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class EnchantsConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private TokenFinder tokenFinder = new TokenFinder();
    private Fortune fortune = new Fortune();
    private Keyfinder keyfinder = new Keyfinder();
    private EternalGrowth eternalGrowth = new EternalGrowth();

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

    public Fortune getFortune() {
        return fortune == null ? new Fortune() : fortune;
    }

    public Keyfinder getKeyfinder() {
        return keyfinder == null ? new Keyfinder() : keyfinder;
    }

    public EternalGrowth getEternalGrowth() {
        return eternalGrowth == null ? new EternalGrowth() : eternalGrowth;
    }

    public static class TokenFinder {
        private int maxLevel = 10;
        private int baseUpgradeCost = 10;
        private float enchantProc = 1.0f;
        private int defaultLevel = 1;
        private String procMessage = "#808080+{amount} {currency} (#FFD700{enchant}#808080)";

        public int getMaxLevel() {
            return Math.max(1, maxLevel);
        }

        public int getUpgradeCost(int currentLevel) {
            return Math.max(1, baseUpgradeCost) * (currentLevel + 1);
        }

        public double getEnchantProc() {
            return Math.max(0.0D, Math.min(1.0D, enchantProc));
        }

        public int getDefaultLevel() {
            return Math.max(0, Math.min(getMaxLevel(), defaultLevel));
        }

        public String getProcMessage() {
            return procMessage == null || procMessage.isBlank()
                    ? "+{amount} {currency} ({enchant})"
                    : procMessage;
        }
    }

    public static class Fortune {
        private int maxLevel = 5;
        private int baseUpgradeCost = 20;
        private int upgradeCostIncrease = 100;
        private float enchantProc = 1.0f;
        private String procMessage = "#80FF80Fortune proc! +{extra} crops";

        public int getMaxLevel() {
            return Math.max(1, maxLevel);
        }

        public int getUpgradeCost(int currentLevel) {
            return Math.max(1, baseUpgradeCost) + (Math.max(0, currentLevel) * Math.max(0, upgradeCostIncrease));
        }

        public double getEnchantProc() {
            return Math.max(0.0D, Math.min(1.0D, enchantProc));
        }

        public String getProcMessage() {
            return procMessage == null || procMessage.isBlank()
                    ? "{enchant} proc! +{extra}"
                    : procMessage;
        }
    }

    public static class Keyfinder {
        private int maxLevel = 100;
        private int baseUpgradeCost = 50;
        private float enchantProc = 1.0f;
        private String procMessage = "#00FFFFKeyfinder! You found a key: {crateId}";
        private List<Crate> crates = List.of(
                new Crate("Crate1", "/crates givekey {player} Crate1", 0.5f),
                new Crate("Crate2", "/crates givekey {player} Crate2", 0.5f)
        );

        public int getMaxLevel() {
            return Math.max(1, maxLevel);
        }

        public int getUpgradeCost(int currentLevel) {
            return Math.max(1, baseUpgradeCost) * (Math.max(0, currentLevel) + 1);
        }

        public double getEnchantProc() {
            return Math.max(0.0D, Math.min(1.0D, enchantProc));
        }

        public String getProcMessage() {
            return procMessage == null || procMessage.isBlank()
                    ? "{enchant} proc! {crateId}"
                    : procMessage;
        }

        public List<Crate> getCrates() {
            return crates == null ? List.of() : crates;
        }
    }

    public static class EternalGrowth {
        private int maxLevel = 5;
        private int baseUpgradeCost = 30;
        private int upgradeCostIncrease = 100;
        private float enchantProc = 0.25f;
        private String procMessage = "#AAFFAAEternal Growth! Advanced {count} crops";

        public int getMaxLevel() {
            return Math.max(1, maxLevel);
        }

        public int getUpgradeCost(int currentLevel) {
            return Math.max(1, baseUpgradeCost) + (Math.max(0, currentLevel) * Math.max(0, upgradeCostIncrease));
        }

        public double getEnchantProc() {
            return Math.max(0.0D, Math.min(1.0D, enchantProc));
        }

        public String getProcMessage() {
            return procMessage == null || procMessage.isBlank()
                    ? "{enchant} proc! {count}"
                    : procMessage;
        }
    }

    public static class Crate {
        private String crateId;
        private String command;
        @SerializedName(value = "crate_chance", alternate = {"crateChance"})
        private float crateChance;

        public Crate() {
        }

        public Crate(String crateId, String command, float crateChance) {
            this.crateId = crateId;
            this.command = command;
            this.crateChance = crateChance;
        }

        public String getCrateId() {
            return crateId == null || crateId.isBlank() ? "unknown" : crateId;
        }

        public String getCommand() {
            return command == null ? "" : command;
        }

        public double getCrateChance() {
            return Math.max(0.0D, crateChance);
        }
    }
}
