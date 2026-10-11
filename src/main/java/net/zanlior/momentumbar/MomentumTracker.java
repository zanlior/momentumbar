package net.zanlior.momentumbar;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

public final class MomentumTracker {

    public enum State { FORWARD, FORWARD_BOOST, BACKWARD, BACKWARD_BOOST }

    // ---- tune these to taste, in blocks/tick (multiply by 20 for blocks/sec) ----
    // Caps scale with movement-speed modifiers, excluding sprinting itself.
    // Forward headrooms mark the normal sprint-jump peak (all light blue revealed).
    // Higher speed continues into aqua. Headrooms need in-game calibration per Speed level.
    private static final float SPRINT_CAP = 0.15f;
    private static final float BACKWARD_CAP = 0.25f;

    private static final float FORWARD_BOOST_HEADROOM_BASE = 0.17f;    // no Speed effect
    private static final float FORWARD_BOOST_HEADROOM_SPEED1 = 0.15f;  // TODO: calibrate against real Speed I sprint-jump peak
    private static final float FORWARD_BOOST_HEADROOM_SPEED2 = 0.15f;  // TODO: calibrate against real Speed II sprint-jump peak

    private static final float BACKWARD_BOOST_HEADROOM_BASE = 0.25f;    // no Speed effect
    private static final float BACKWARD_BOOST_HEADROOM_SPEED1 = 0.25f;  // TODO: calibrate if needed
    private static final float BACKWARD_BOOST_HEADROOM_SPEED2 = 0.25f;  // TODO: calibrate if needed

    private static final Identifier SPRINTING_MODIFIER_ID = Identifier.withDefaultNamespace("sprinting");
    private static final float MIN_SCALE = 0.05f;

    private static final float DEADZONE = 0.01f;
    private static final float HYSTERESIS = 0.03f; // 3% buffer around each cap to stop flicker

    // Forward texture: light blue occupies x=42..181; aqua lies to its left.
    private static final float FORWARD_NORMAL_BOOST_FRACTION = 140f / 182f;
    private static final float BACKWARD_BOOST_MAX_FRACTION = 0.5f;

    private static float smoothedSpeed = 0f;
    private static State state = State.FORWARD;
    private static float fraction = 0f; // 0..1; boost reveal width also depends on direction
    private static boolean forwardBoosted = false;
    private static boolean backwardBoosted = false;

    private MomentumTracker() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        float targetSpeed;
        boolean movingBackward;
        int speedLevel;
        float movementScale;

        if (player == null) {
            targetSpeed = 0f;
            movingBackward = false;
            speedLevel = 0;
            movementScale = 1f;
        } else {
            Vec3 v = player.getDeltaMovement();
            double horizontalSpeed = Math.sqrt(v.x * v.x + v.z * v.z);

            float yawRad = (float) Math.toRadians(player.getYRot());
            double lookX = -Math.sin(yawRad);
            double lookZ = Math.cos(yawRad);
            double dot = v.x * lookX + v.z * lookZ;

            targetSpeed = (float) horizontalSpeed;
            movingBackward = dot < 0;
            speedLevel = speedEffectLevel(player);
            movementScale = movementSpeedScale(player);
        }

        smoothedSpeed += (targetSpeed - smoothedSpeed) * 0.35f;
        if (smoothedSpeed < 0f) smoothedSpeed = 0f;

        float sprintCap = SPRINT_CAP * movementScale;
        float forwardHeadroom = forwardHeadroomForLevel(speedLevel);
        float backwardCap = BACKWARD_CAP * movementScale;
        float backwardHeadroom = backwardHeadroomForLevel(speedLevel);

        if (smoothedSpeed < DEADZONE) {
            state = State.FORWARD;
            fraction = 0f;
            forwardBoosted = false;
            backwardBoosted = false;
            return;
        }

        if (movingBackward) {
            forwardBoosted = false;

            float onT = backwardCap * (1f + HYSTERESIS);
            float offT = backwardCap;
            if (!backwardBoosted && smoothedSpeed > onT) {
                backwardBoosted = true;
            } else if (backwardBoosted && smoothedSpeed <= offT) {
                backwardBoosted = false;
            }

            if (backwardBoosted) {
                state = State.BACKWARD_BOOST;
                float extra = smoothedSpeed - backwardCap;
                fraction = clamp01(extra / backwardHeadroom);
            } else {
                state = State.BACKWARD;
                fraction = clamp01(smoothedSpeed / backwardCap);
            }
            return;
        }

        backwardBoosted = false;

        float onThreshold = sprintCap * (1f + HYSTERESIS);
        float offThreshold = sprintCap;
        if (!forwardBoosted && smoothedSpeed > onThreshold) {
            forwardBoosted = true;
        } else if (forwardBoosted && smoothedSpeed <= offThreshold) {
            forwardBoosted = false;
        }

        if (forwardBoosted) {
            state = State.FORWARD_BOOST;
            float extra = smoothedSpeed - sprintCap;
            fraction = clamp01(extra / forwardHeadroom * FORWARD_NORMAL_BOOST_FRACTION);
        } else {
            state = State.FORWARD;
            fraction = clamp01(smoothedSpeed / sprintCap);
        }
    }

    /** 0 = no Speed effect, 1 = Speed I, 2 = Speed II, 3+ = Speed III and up. */
    private static int speedEffectLevel(LocalPlayer player) {
        MobEffectInstance speed = player.getEffect(MobEffects.SPEED);
        if (speed == null) return 0;
        return speed.getAmplifier() + 1; // amplifier is 0-indexed (0 = Speed I)
    }

    /** Movement-speed scale for caps, with vanilla's multiplicative sprint bonus divided out. */
    private static float movementSpeedScale(LocalPlayer player) {
        AttributeInstance instance = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (instance == null) return 1f;

        double base = instance.getBaseValue();
        if (base <= 0.0) return 1f;

        double value = instance.getValue();
        AttributeModifier sprinting = instance.getModifier(SPRINTING_MODIFIER_ID);
        if (sprinting != null && sprinting.amount() > -1.0) {
            // Read only: do not remove the modifier from the player's attribute.
            value /= 1.0 + sprinting.amount();
        }

        return (float) Math.max(value / base, MIN_SCALE);
    }

    /** Independent per-level headroom for the FORWARD boost - calibrate each separately. */
    private static float forwardHeadroomForLevel(int level) {
        return switch (level) {
            case 1 -> FORWARD_BOOST_HEADROOM_SPEED1;
            case 2 -> FORWARD_BOOST_HEADROOM_SPEED2;
            default -> FORWARD_BOOST_HEADROOM_BASE;
        };
    }

    /** Independent per-level headroom for the BACKWARD boost - calibrate each separately. */
    private static float backwardHeadroomForLevel(int level) {
        return switch (level) {
            case 1 -> BACKWARD_BOOST_HEADROOM_SPEED1;
            case 2 -> BACKWARD_BOOST_HEADROOM_SPEED2;
            default -> BACKWARD_BOOST_HEADROOM_BASE;
        };
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

    public static float boostMaxFraction(State s) {
        return s == State.FORWARD_BOOST ? 1f : BACKWARD_BOOST_MAX_FRACTION;
    }

    private static float clamp01(float v) {
        return Math.clamp(v, 0f, 1f);
    }
}
