package dev.hytalemodding.hytalefarming.events;

import dev.hytalemodding.hytalefarming.service.TokenService;

import java.util.UUID;

/**
 * SDK-independent logic adapter for crop break events.
 */
public class TokenFinderBreakBlockSystem {
    private final TokenService tokenService;

    public TokenFinderBreakBlockSystem(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    public long onCropBreak(UUID playerId, String playerName, String heldItemId, String blockId) {
        if (!"Tool_Hoe_Thorium".equals(heldItemId)) return 0;
        if (!(blockId.startsWith("Crop_") || blockId.startsWith("Plant_Crop_"))) return 0;
        return tokenService.processTokenFinderCropBreak(playerId, playerName);
    }
}
