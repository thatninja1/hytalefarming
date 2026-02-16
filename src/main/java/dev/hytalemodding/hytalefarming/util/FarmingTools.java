package dev.hytalemodding.hytalefarming.util;

import com.hypixel.hytale.math.vector.Vector3i;

import java.util.ArrayList;
import java.util.List;
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

    public static List<Vector3i> getHarvestArea(String sickleId, Vector3i center) {
        int[] bounds = switch (sickleId) {
            case "Tool_Sickle_Iron" -> new int[]{1, 1};      // 3x3
            case "Tool_Sickle_Gold" -> new int[]{2, 2};      // 5x5
            case "Tool_Sickle_Thorium" -> new int[]{3, 2};   // 6x6 (3 left, 2 right)
            case "Tool_Sickle_Adamantite" -> new int[]{3, 3}; // 7x7
            case "Tool_Sickle_Mithril" -> new int[]{4, 3};   // 8x8 (4 left, 3 right)
            default -> new int[]{1, 1};
        };

        List<Vector3i> area = new ArrayList<>();
        for (int dx = -bounds[0]; dx <= bounds[1]; dx++) {
            for (int dz = -bounds[0]; dz <= bounds[1]; dz++) {
                area.add(new Vector3i(center.getX() + dx, center.getY(), center.getZ() + dz));
            }
        }
        return area;
    }
}
