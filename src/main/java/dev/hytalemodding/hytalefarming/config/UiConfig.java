package dev.hytalemodding.hytalefarming.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class UiConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String uiTitle = "Ninja Farming";
    private String uiSubtitle = "Upgrade your Thorium Hoe";

    public static UiConfig load(Path path) {
        try {
            if (Files.notExists(path)) {
                UiConfig defaults = new UiConfig();
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(defaults));
                return defaults;
            }
            UiConfig loaded = GSON.fromJson(Files.readString(path), UiConfig.class);
            if (loaded == null) {
                UiConfig defaults = new UiConfig();
                Files.writeString(path, GSON.toJson(defaults));
                return defaults;
            }
            return loaded;
        } catch (IOException e) {
            try {
                UiConfig defaults = new UiConfig();
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(defaults));
                return defaults;
            } catch (IOException ignored) {
                throw new IllegalStateException("Failed loading ui config", e);
            }
        }
    }

    public String getUiTitle() {
        return uiTitle == null || uiTitle.isBlank() ? "Ninja Farming" : uiTitle;
    }

    public String getUiSubtitle() {
        return uiSubtitle == null || uiSubtitle.isBlank() ? "Upgrade your Thorium Hoe" : uiSubtitle;
    }
}
