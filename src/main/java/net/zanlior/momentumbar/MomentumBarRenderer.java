package net.zanlior.momentumbar;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public class MomentumBarRenderer {

    private static final int W = 182, H = 5;

    private static final Identifier BACKGROUND = tex("bar_background.png");
    private static final Identifier FORWARD = tex("bar_forward.png");
    private static final Identifier FORWARD_BOOST = tex("bar_forward_boost.png");
    private static final Identifier BACKWARD = tex("bar_backward.png");
    private static final Identifier BACKWARD_BOOST = tex("bar_backward_boost.png");

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath("momentumbar", "textures/gui/momentum/" + name);

    }

    private MomentumBarRenderer() {
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        int x = mc.getWindow().getGuiScaledWidth() / 2 - W / 2;
        int y = mc.getWindow().getGuiScaledHeight() - 29;

        // Background track, always full width.
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0, 0, W, H, W, H);

        MomentumTracker.State state = MomentumTracker.getState();
        float fraction = MomentumTracker.getFraction();

        switch (state) {
            case FORWARD -> drawLeftAnchored(graphics, FORWARD, x, y, fraction);
            case BACKWARD -> drawLeftAnchored(graphics, BACKWARD, x, y, fraction);
            case FORWARD_BOOST -> {
                // Base bar is maxed out (you're already past sprint cap to be here).
                drawLeftAnchored(graphics, FORWARD, x, y, 1f);
                drawRightAnchoredBoost(graphics, FORWARD_BOOST, x, y, fraction, state);
            }
            case BACKWARD_BOOST -> {
                drawLeftAnchored(graphics, BACKWARD, x, y, 1f);
                drawRightAnchoredBoost(graphics, BACKWARD_BOOST, x, y, fraction, state);
            }
        }
    }

    /**
     * Normal fill: grows from the left edge (u=0), width = W * fraction.
     */
    private static void drawLeftAnchored(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, float fraction) {
        int fillW = Math.round(W * clamp01(fraction));
        if (fillW <= 0) return;
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, fillW, H, W, H);
    }

    /**
     * Boost overlay: grows from the RIGHT edge inward. Forward can span the
     * full bar; backward is capped at half. Screen position and sample (u) are anchored
     * to the right, so a small boost shows a thin strip of the texture's own
     * rightmost (hottest) color, growing left as the fraction increases.
     */
    private static void drawRightAnchoredBoost(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, float fraction, MomentumTracker.State state) {
        float cappedFraction = clamp01(fraction) * MomentumTracker.boostMaxFraction(state);
        int fillW = Math.round(W * cappedFraction);
        if (fillW <= 0) return;

        int u = W - fillW;                 // sample the texture's own right side
        int drawX = x + (W - fillW);       // draw pinned to the bar's right edge

        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, drawX, y, u, 0, fillW, H, W, H);
    }

    private static float clamp01(float v) {
        return Math.clamp(v, 0f, 1f);
    }
}
