package dev.hytalemodding.hytalefarming.commands;

import dev.hytalemodding.hytalefarming.config.TokensConfig;
import dev.hytalemodding.hytalefarming.service.TokenService;

import java.util.UUID;

public class TokensBalanceCommand {
    private final TokenService tokenService;
    private final TokensConfig tokensConfig;

    public TokensBalanceCommand(TokenService tokenService, TokensConfig tokensConfig) {
        this.tokenService = tokenService;
        this.tokensConfig = tokensConfig;
    }

    public String run(UUID requesterId, String requesterName) {
        long bal = tokenService.balance(requesterId, requesterName);
        return tokensConfig.getCurrencyName() + ": " + bal;
    }

    public String run(UUID targetId, String targetName, boolean targetMode) {
        long bal = tokenService.balance(targetId, targetName);
        return targetName + " " + tokensConfig.getCurrencyName() + ": " + bal;
    }
}
