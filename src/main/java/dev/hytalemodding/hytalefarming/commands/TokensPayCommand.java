package dev.hytalemodding.hytalefarming.commands;

import dev.hytalemodding.hytalefarming.config.TokensConfig;
import dev.hytalemodding.hytalefarming.service.TokenService;

import java.util.UUID;

public class TokensPayCommand {
    private final TokenService tokenService;
    private final TokensConfig tokensConfig;

    public TokensPayCommand(TokenService tokenService, TokensConfig tokensConfig) {
        this.tokenService = tokenService;
        this.tokensConfig = tokensConfig;
    }

    public String run(UUID senderId, String senderName, UUID targetId, String targetName, long amount) {
        if (amount <= 0) return "Amount must be > 0";
        boolean ok = tokenService.pay(senderId, senderName, targetId, targetName, amount);
        if (!ok) return "Not enough " + tokensConfig.getCurrencyName() + ".";
        return "Paid " + amount + " " + tokensConfig.getCurrencyName() + " to " + targetName;
    }
}
