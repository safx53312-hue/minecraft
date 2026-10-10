package dev.enchantpoints;

import dev.enchantpoints.net.PointsSyncPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class EnchantPointsMod implements ModInitializer {
    public static final String MOD_ID = "enchantpoints";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        List<String> errors = ConfigManager.load();
        errors.forEach(e -> LOG.error("[EnchantPoints] config error: {}", e));
        PayloadTypeRegistry.playS2C().register(PointsSyncPayload.ID, PointsSyncPayload.CODEC);
        PointsCommands.register();
        PointsEvents.register();
        LOG.info("[EnchantPoints] loaded, max = {}", ConfigManager.get().maxPoints);
    }
}
