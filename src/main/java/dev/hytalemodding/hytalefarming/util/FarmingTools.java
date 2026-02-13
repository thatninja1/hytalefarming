package dev.hytalemodding.hytalefarming.util;

import java.util.Set;

public final class FarmingTools {
    private static final Set<String> VALID_SICKLES = Set.of(
            "Tool_Sickle_Adamantite",
            "Tool_Sickle_Cobalt",
            "Tool_Sickle_Crude",
            "Tool_Sickle_Gold",
            "Tool_Sickle_Iron",
            "Tool_Sickle_Mithril",
            "Tool_Sickle_Steel_Rusty",
            "Tool_Sickle_Thorium"
    );

    private FarmingTools() {
    }

    public static boolean isValidFarmingTool(String itemId) {
        return itemId != null && VALID_SICKLES.contains(itemId);
    }
}
