package dev.hytalemodding.hytalefarming.ui;

import com.hypixel.hytale.server.core.entity.EntityStore;
import com.hypixel.hytale.server.core.entity.component.store.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.ref.Ref;
import com.hypixel.hytale.server.core.ui.custom.CustomPageLifetime;
import com.hypixel.hytale.server.core.ui.custom.EventData;
import com.hypixel.hytale.server.core.ui.custom.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.custom.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.custom.UIEventBuilder;
import com.hypixel.hytale.server.core.ui.custom.event.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.util.codec.BuilderCodec;
import com.hypixel.hytale.server.core.util.codec.Codec;
import com.hypixel.hytale.server.core.util.codec.KeyedCodec;
import dev.hytalemodding.hytalefarming.HytaleFarmingPlugin;

import javax.annotation.Nonnull;

public class ThoriumHoeUpgradePage extends InteractiveCustomUIPage<ThoriumHoeUpgradePage.Data> {

    public static class Data {
        public static final BuilderCodec<Data> CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("@UpgradeAction", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .build();

        private String action = "";
    }

    public ThoriumHoeUpgradePage() {
        super(CustomPageLifetime.Closeable, Data.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder uiCommandBuilder, @Nonnull UIEventBuilder uiEventBuilder, @Nonnull Store<EntityStore> store) {
        HytaleFarmingPlugin plugin = HytaleFarmingPlugin.instance();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        int level = plugin.getTokenService().enchantLevel(store, ref, "token_finder");
        int max = plugin.getEnchantsConfig().getTokenFinder().getMaxLevel();
        int cost = plugin.getEnchantsConfig().getTokenFinder().getUpgradeCost(level);
        long balance = plugin.getTokenService().balance(store, ref);

        uiCommandBuilder.append("ThoriumHoeUpgrade.ui");
        uiCommandBuilder.setValue("#TitleLabel.Text", "Upgradeable Hoe: " + player.getDisplayName());
        uiCommandBuilder.setValue("#CurrencyLabel.Text", plugin.getTokensConfig().getCurrencyName() + ": " + balance);
        uiCommandBuilder.setValue("#TokenFinderLabel.Text", "Token Finder Lvl " + level + "/" + max);
        uiCommandBuilder.setValue("#TokenFinderCostLabel.Text", "Upgrade cost: " + cost);

        uiEventBuilder.addEventBinding(CustomUIEventBindingType.Clicked, "#UpgradeTokenFinderButton", EventData.of("@UpgradeAction", "token_finder"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, Data data) {
        if ("token_finder".equals(data.action)) {
            HytaleFarmingPlugin.instance().getTokenService().tryUpgradeTokenFinder(store, ref);
        }
        sendUpdate();
    }
}
