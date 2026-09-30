package com.redsmods.sound_physics_perfected.acoustics;

import com.redsmods.sound_physics_perfected.config.RedsAttenuationType;

/** Immutable snapshot of the config values one trace uses, so a trace never sees a half-edited config. */
public final class TraceSettings {
    public final int bounces;
    /** Bounces used for the room estimate; rays keep going past {@link #bounces} without connecting to sounds. */
    public final int reverbBounces;
    public final double segmentLength;
    public final double maxTotalDistance;
    public final boolean permeation;
    public final boolean reverb;
    public final double permeationAbsorption;
    public final double maxBlocksPermeated;
    public final double bounceAbsorption;
    public final RedsAttenuationType attenuationType;

    public TraceSettings(int bounces, double segmentLength, boolean permeation, boolean reverb,
                         double permeationAbsorption, double maxBlocksPermeated, double bounceAbsorption,
                         RedsAttenuationType attenuationType) {
        this(bounces, bounces, segmentLength, permeation, reverb, permeationAbsorption, maxBlocksPermeated,
                bounceAbsorption, attenuationType);
    }

    public TraceSettings(int bounces, int reverbBounces, double segmentLength, boolean permeation, boolean reverb,
                         double permeationAbsorption, double maxBlocksPermeated, double bounceAbsorption,
                         RedsAttenuationType attenuationType) {
        this.bounces = bounces;
        this.reverbBounces = Math.max(bounces, reverbBounces);
        this.segmentLength = segmentLength;
        this.maxTotalDistance = segmentLength * bounces;
        this.permeation = permeation;
        this.reverb = reverb;
        this.permeationAbsorption = permeationAbsorption;
        this.maxBlocksPermeated = maxBlocksPermeated;
        this.bounceAbsorption = bounceAbsorption;
        this.attenuationType = attenuationType;
    }

    /** Path weight: transmission / distance falloff, as in earlier versions of the mod. */
    public double weight(double pathDistance, double obstruction, double distanceToSound) {
        double transmission = Math.pow(permeationAbsorption, obstruction);
        double d = Math.max(distanceToSound + pathDistance, 0.1);
        return switch (attenuationType) {
            case LINEAR -> transmission / d;
            case ENERGY, INVERSE_SQUARE, VERCIDIUM_INVERSE_SQUARE, VERCIDIUM_LINEAR -> transmission / (d * d);
        };
    }
}
