package dev.hytalemodding.hytalefarming.commands;

import dev.hytalemodding.hytalefarming.config.TokensConfig;
import dev.hytalemodding.hytalefarming.service.TokenService;

import java.util.ArrayList;
import java.util.List;

public class TokenTopCommand {
    private final TokenService tokenService;
    private final TokensConfig tokensConfig;

    public TokenTopCommand(TokenService tokenService, TokensConfig tokensConfig) {
        this.tokenService = tokenService;
        this.tokensConfig = tokensConfig;
    }

    public List<String> run() {
        List<String> lines = new ArrayList<>();
        lines.add("Top " + tokensConfig.getCurrencyName() + " holders:");
        List<TokenService.LeaderboardEntry> top = tokenService.top(10);
        for (int i = 0; i < top.size(); i++) {
            TokenService.LeaderboardEntry e = top.get(i);
            lines.add("#" + (i + 1) + " " + e.playerName() + " - " + e.balance());
        }
        return lines;
    }
}
