package net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support;

import java.util.Random;

import static net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultNoiseProvider.DEFAULT_NOISINESS_DIVIDER;
import static net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.SinusoidalDeviationProvider.DEFAULT_SLOPE_DIVIDER;


public class DefaultMouseMotionNature extends MouseMotionNature {

    public static final int TIME_TO_STEPS_DIVIDER = 8;
    public static final int MIN_STEPS = 10;
    public static final int EFFECT_FADE_STEPS = 15;
    public static final int REACTION_TIME_BASE_MS = 20;
    public static final int REACTION_TIME_VARIATION_MS = 120;

    public DefaultMouseMotionNature() {
        // Mobile: no AWT Robot (there is no desktop pointer); NaturalMouse sets its own SystemCalls
        // and MouseInfoAccessor, which drive the game's synthetic pointer.

        setDeviationProvider(new SinusoidalDeviationProvider(DEFAULT_SLOPE_DIVIDER));
        setNoiseProvider(new DefaultNoiseProvider(DEFAULT_NOISINESS_DIVIDER));
        setSpeedManager(new DefaultSpeedManager());
        setOvershootManager(new DefaultOvershootManager(new Random()));
        setEffectFadeSteps(EFFECT_FADE_STEPS);
        setMinSteps(MIN_STEPS);
        setReactionTimeBaseMs(REACTION_TIME_BASE_MS);
        setReactionTimeVariationMs(REACTION_TIME_VARIATION_MS);
        setTimeToStepsDivider(TIME_TO_STEPS_DIVIDER);
    }
}
