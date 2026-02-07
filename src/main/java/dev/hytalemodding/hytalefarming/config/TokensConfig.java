package dev.hytalemodding.hytalefarming.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TokensConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String currencyName = "Tokens";
    private int tokensTimes = 1;

    public static TokensConfig load(Path path) {
        try {
            if (Files.notExists(path)) {
                TokensConfig defaults = new TokensConfig();
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(defaults));
                return defaults;
            }
            TokensConfig loaded = GSON.fromJson(Files.readString(path), TokensConfig.class);
            return loaded == null ? new TokensConfig() : loaded;
        } catch (IOException e) {
            throw new IllegalStateException("Failed loading tokens config", e);
        }
    }

    public String getCurrencyName() {
        return currencyName == null || currencyName.isBlank() ? "Tokens" : currencyName;
    }

    public int getTokensTimes() {
        return Math.max(1, tokensTimes);
    }
}
