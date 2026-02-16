package dev.hytalemodding.hytalefarming;

import com.hypixel.hytale.logger.HytaleLogger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class Debug {
    private static boolean debugEnabled = true;
    private static HytaleLogger logger;
    private static final Set<String> WARN_ONCE_KEYS = ConcurrentHashMap.newKeySet();

    private Debug() {}

    public static void configure(boolean enabled, HytaleLogger hytaleLogger) {
        debugEnabled = enabled;
        logger = hytaleLogger;
    }

    public static void logDebug(String message) {
        if (!debugEnabled || logger == null) {
            return;
        }
        logger.atInfo().log("[HytaleFarming] " + message);
    }

    public static void logInfo(String message) {
        if (logger == null) {
            return;
        }
        logger.atInfo().log("[HytaleFarming] " + message);
    }

    public static void warn(String message) {
        if (logger == null) {
            return;
        }
        logger.atWarning().log("[HytaleFarming] " + message);
    }

    public static void warnOnce(String key, String message) {
        if (WARN_ONCE_KEYS.add(key)) {
            warn(message);
        }
    }

    public static void error(String message) {
        if (logger == null) {
            return;
        }
        logger.atSevere().log("[HytaleFarming] " + message);
    }

    public static void log(String message) {
        logDebug(message);
    }
}
