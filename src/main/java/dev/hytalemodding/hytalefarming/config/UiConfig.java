package dev.hytalemodding.hytalefarming.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class UiConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private UiSection ui = new UiSection();
    private boolean debug = true;

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
            if (loaded.ui == null) {
                loaded.ui = new UiSection();
            }
            return loaded;
        } catch (IOException e) {
            throw new IllegalStateException("Failed loading ui config", e);
        }
    }

    public String getUiTitle() {
        return ui == null ? new UiSection().getTitle() : ui.getTitle();
    }

    public String getUiSubtitle() {
        return ui == null ? new UiSection().getSubtitle() : ui.getSubtitle();
    }

    public boolean isDebug() {
        return debug;
    }

    public static class UiSection {
        private String title = "Ninja Farming";
        private String subtitle = "Upgrade your Farming Tool";

        public String getTitle() {
            return title == null || title.isBlank() ? "Ninja Farming" : title;
        }

        public String getSubtitle() {
            return subtitle == null || subtitle.isBlank() ? "Upgrade your Farming Tool" : subtitle;
        }
    }
}
