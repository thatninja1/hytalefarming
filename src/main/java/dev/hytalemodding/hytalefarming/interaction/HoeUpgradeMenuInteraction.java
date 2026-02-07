package dev.hytalemodding.hytalefarming.interaction;

public class HoeUpgradeMenuInteraction {
    public boolean shouldOpen(String heldItemId) {
        return "Tool_Hoe_Thorium".equals(heldItemId);
    }
}
