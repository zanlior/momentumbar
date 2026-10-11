package net.zanlior.momentumbar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public final class MomentumTracker {

    public enum State { FORWARD, FORWARD_BOOST, BACKWARD, BACKWARD_BOOST }

    // ---- tune these to taste, in blocks/tick (multiply by 20 for blocks/sec) ----
    private static final float SPRINT_CAP = 0.29f;
    private static final float FORWARD_BOOST_HEADROOM = 0.34f;   // extra speed above SPRINT_CAP for a full (100%) BOOST reveal
    private static final float BACKWARD_CAP = 0.5f;
    private static final float BACKWARD_BOOST_HEADROOM = 0.5f;   // extra speed above BACKWARD_CAP for a full (100%) BOOST reveal

    private static final float DEADZONE = 0.01f;
    private static final float HYSTERESIS = 0.03f; // 3% buffer around each cap to stop flicker

    // BOOST bars are capped to at most half the bar's width, per design.
    private static final float BOOST_MAX_FRACTION = 0.5f;

    private static float smoothedSpeed = 0f;
    private static State state = State.FORWARD;
    private static float fraction = 0f; // 0..1, meaning depends on state (see BOOST_MAX_FRACTION above)
    private static boolean forwardBoosted = false;
    private static boolean backwardBoosted = false;

    private MomentumTracker() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        float targetSpeed;
        boolean movingBackward;

        if (player == null) {
            targetSpeed = 0f;
            movingBackward = false;
        } else {
            Vec3 v = player.getDeltaMovement();
            double horizontalSpeed = Math.sqrt(v.x * v.x + v.z * v.z);

            float yawRad = (float) Math.toRadians(player.getYRot());
            double lookX = -Math.sin(yawRad);
            double lookZ = Math.cos(yawRad);
            double dot = v.x * lookX + v.z * lookZ;

            targetSpeed = (float) horizontalSpeed;
            movingBackward = dot < 0;
        }

        smoothedSpeed += (targetSpeed - smoothedSpeed) * 0.35f;
        if (smoothedSpeed < 0f) smoothedSpeed = 0f;

        if (smoothedSpeed < DEADZONE) {
            state = State.FORWARD;
            fraction = 0f;
            forwardBoosted = false;
            backwardBoosted = false;
            return;
        }

        if (movingBackward) {
            forwardBoosted = false;

            float onT = BACKWARD_CAP * (1f + HYSTERESIS);
            float offT = BACKWARD_CAP * (1f - HYSTERESIS);
            if (!backwardBoosted && smoothedSpeed > onT) {
                backwardBoosted = true;
            } else if (backwardBoosted && smoothedSpeed < offT) {
                backwardBoosted = false;
            }

            if (backwardBoosted) {
                state = State.BACKWARD_BOOST;
                float extra = smoothedSpeed - BACKWARD_CAP;
                fraction = clamp01(extra / BACKWARD_BOOST_HEADROOM);
            } else {
                state = State.BACKWARD;
                fraction = clamp01(smoothedSpeed / BACKWARD_CAP);
            }
            return;
        }

        backwardBoosted = false;

        float onThreshold = SPRINT_CAP * (1f + HYSTERESIS);
        float offThreshold = SPRINT_CAP * (1f - HYSTERESIS);
        if (!forwardBoosted && smoothedSpeed > onThreshold) {
            forwardBoosted = true;
        } else if (forwardBoosted && smoothedSpeed < offThreshold) {
            forwardBoosted = false;
        }

        if (forwardBoosted) {
            state = State.FORWARD_BOOST;
            float extra = smoothedSpeed - SPRINT_CAP;
            fraction = clamp01(extra / FORWARD_BOOST_HEADROOM);
        } else {
            state = State.FORWARD;
            fraction = clamp01(smoothedSpeed / SPRINT_CAP);
        }
    }

    public static State getState() {
        return state;
    }

    /** 0..1 - for BOOST states this is already relative to the boost's own headroom, not the bar width. */
    public static float getFraction() {
        return fraction;
    }

    public static boolean isBoost(State s) {
        return s == State.FORWARD_BOOST || s == State.BACKWARD_BOOST;
    }

    public static float boostMaxFraction() {
        return BOOST_MAX_FRACTION;
    }

    private static float clamp01(float v) {
        return Math.clamp(v, 0f, 1f);
    }
}