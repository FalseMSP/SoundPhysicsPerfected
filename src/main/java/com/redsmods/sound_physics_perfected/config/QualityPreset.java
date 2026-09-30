package com.redsmods.sound_physics_perfected.config;

import java.util.List;
import java.util.Locale;

/**
 * Ray tracing quality presets, offered on the Music &amp; Sounds screen. Each sets how many rays are traced, how often
 * they bounce and how far they go; {@link #HIGH} is the mod's defaults.
 */
public enum QualityPreset {
    LOW(64, 2, 4, 3),
    MEDIUM(128, 3, 6, 4),
    HIGH(200, 3, 8, 4),
    ULTRA(512, 4, 12, 6),
    /** Settings that match no preset. */
    CUSTOM(-1, -1, -1, -1);

    /** The presets a player can pick (everything but {@link #CUSTOM}), from cheapest to best. */
    public static final List<QualityPreset> PRESETS = List.of(LOW, MEDIUM, HIGH, ULTRA);

    /** {@link Config#raysCast} */
    public final int rays;
    /** {@link Config#raysBounced} */
    public final int bounces;
    /** {@link Config#reverbBounces} */
    public final int reverbBounces;
    /** {@link Config#maxRayLength}, in chunks */
    public final int rayLength;

    QualityPreset(int rays, int bounces, int reverbBounces, int rayLength) {
        this.rays = rays;
        this.bounces = bounces;
        this.reverbBounces = reverbBounces;
        this.rayLength = rayLength;
    }

    /** Lower-case name, for translation keys. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The preset with exactly these settings, or {@link #CUSTOM}. */
    public static QualityPreset of(int rays, int bounces, int reverbBounces, int rayLength) {
        for (QualityPreset preset : PRESETS) {
            if (preset.rays == rays && preset.bounces == bounces && preset.reverbBounces == reverbBounces
                    && preset.rayLength == rayLength) {
                return preset;
            }
        }
        return CUSTOM;
    }

    /** The preset the config's current settings match, or {@link #CUSTOM}. */
    public static QualityPreset of(Config config) {
        return of(config.raysCast, config.raysBounced, config.reverbBounces, config.maxRayLength);
    }

    /** Writes this preset's settings into the config; {@link #CUSTOM} leaves it alone. */
    public void applyTo(Config config) {
        if (this == CUSTOM) return;
        config.raysCast = rays;
        config.raysBounced = bounces;
        config.reverbBounces = reverbBounces;
        config.maxRayLength = rayLength;
    }
}
