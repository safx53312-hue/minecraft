package dev.enchantpoints.client;

import dev.enchantpoints.net.PointsSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;

/** Display only. Numbers come from the server (PointsSyncPayload); nothing is calculated here. */
public class EnchantPointsClient implements ClientModInitializer {
    @Override
    public void onInitialize() {
        ClientPlayNetworking.registerGlobalReceiver(PointsSyncPayload.ID, (payload, context) ->
                context.client().execute(() -> PointsHud.update(payload)));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PointsHud.clear());

        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof InventoryScreen) {
                ScreenEvents.afterRender(screen).register((s, ctx, mouseX, mouseY, delta) -> PointsHud.render(s, ctx));
            }
        });
    }
}
