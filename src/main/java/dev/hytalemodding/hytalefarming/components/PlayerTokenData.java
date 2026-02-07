package dev.hytalemodding.hytalefarming.components;

import com.hypixel.hytale.server.core.entity.EntityStore;
import com.hypixel.hytale.server.core.entity.component.Component;
import com.hypixel.hytale.server.core.util.codec.BuilderCodec;
import com.hypixel.hytale.server.core.util.codec.Codec;
import com.hypixel.hytale.server.core.util.codec.KeyedCodec;
import com.hypixel.hytale.server.core.util.codec.MapCodec;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

public class PlayerTokenData implements Component<EntityStore> {

    public static final BuilderCodec<PlayerTokenData> CODEC = BuilderCodec.builder(PlayerTokenData.class, PlayerTokenData::new)
            .append(new KeyedCodec<>("balance", Codec.LONG), (d, v) -> d.balance = v, d -> d.balance)
            .add()
            .append(new KeyedCodec<>("enchants", new MapCodec<>(Codec.INTEGER, HashMap::new, false)), (d, v) -> d.enchants = v, d -> d.enchants)
            .add()
            .build();

    private long balance = 0;
    private Map<String, Integer> enchants = new HashMap<>();

    public PlayerTokenData() {
    }

    private PlayerTokenData(PlayerTokenData copy) {
        this.balance = copy.balance;
        this.enchants = new HashMap<>(copy.enchants);
    }

    public long getBalance() {
        return balance;
    }

    public void setBalance(long balance) {
        this.balance = Math.max(0, balance);
    }

    public int getEnchantLevel(String key) {
        return enchants.getOrDefault(key, 0);
    }

    public void setEnchantLevel(String key, int level) {
        enchants.put(key, Math.max(0, level));
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        return new PlayerTokenData(this);
    }
}
