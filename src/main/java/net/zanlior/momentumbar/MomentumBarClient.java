package net.zanlior.momentumbar;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

public class MomentumBarClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> MomentumTracker.tick());
        HudElementRegistry.replaceElement(VanillaHudElements.INFO_BAR, old -> MomentumBarRenderer::render);
    }
}
