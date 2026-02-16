package dev.hytalemodding.hytalefarming.util;

import dev.hytalemodding.hytalefarming.Debug;

import java.util.Map;
import java.util.regex.Pattern;

public final class MessageFormatter {
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final boolean HEX_CHAT_SUPPORTED = false;

    private MessageFormatter() {
    }

    public static String format(String template, Map<String, String> placeholders) {
        String formatted = template == null ? "" : template;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String value = entry.getValue() == null ? "" : entry.getValue();
                formatted = formatted.replace("{" + entry.getKey() + "}", value);
            }
        }

        if (!HEX_CHAT_SUPPORTED && HEX_COLOR_PATTERN.matcher(formatted).find()) {
            Debug.warnOnce("hex-chat-color-unsupported", "Hex chat colors not supported; stripping codes.");
            formatted = HEX_COLOR_PATTERN.matcher(formatted).replaceAll("");
        }

        return formatted;
    }

    public static void logHexSupportAtStartupIfNeeded(String... templates) {
        if (HEX_CHAT_SUPPORTED || templates == null) {
            return;
        }

        for (String template : templates) {
            if (template != null && HEX_COLOR_PATTERN.matcher(template).find()) {
                Debug.warnOnce("hex-chat-color-unsupported", "Hex chat colors not supported; stripping codes.");
                return;
            }
        }
    }
}
