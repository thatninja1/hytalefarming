package dev.hytalemodding.hytalefarming;

import com.hypixel.hytale.logger.HytaleLogger;

public final class Debug {
    private static boolean enabled = true;
    private static HytaleLogger logger;

    private Debug() {}

    public static void configure(boolean debugEnabled, HytaleLogger hytaleLogger) {
        enabled = debugEnabled;
        logger = hytaleLogger;
    }

    public static void log(String message) {
        if (!enabled || logger == null) {
            return;
        }
        logger.atInfo().log("[HytaleFarming] " + message);
    }
}
