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
    /**
     * How much harder treble is stopped than bass by what a sound passes through, relative to the materials' own
     * figures (1 = as listed, 0 = treble like bass). Real walls pass the bass and stop the treble (mass law).
     */
    public final double muffle;
    /** Whether this trace re-measures the room (records the echogram); the in-between traces keep the last one. */
    public final boolean measureRoom;

    public TraceSettings(int bounces, double segmentLength, boolean permeation, boolean reverb,
                         double permeationAbsorption, double maxBlocksPermeated, double bounceAbsorption,
                         RedsAttenuationType attenuationType) {
        this(bounces, bounces, segmentLength, permeation, reverb, permeationAbsorption, maxBlocksPermeated,
                bounceAbsorption, attenuationType);
    }

    public TraceSettings(int bounces, int reverbBounces, double segmentLength, boolean permeation, boolean reverb,
                         double permeationAbsorption, double maxBlocksPermeated, double bounceAbsorption,
                         RedsAttenuationType attenuationType) {
        this(bounces, reverbBounces, segmentLength, permeation, reverb, permeationAbsorption, maxBlocksPermeated,
                bounceAbsorption, attenuationType, 1.0);
    }

    public TraceSettings(int bounces, int reverbBounces, double segmentLength, boolean permeation, boolean reverb,
                         double permeationAbsorption, double maxBlocksPermeated, double bounceAbsorption,
                         RedsAttenuationType attenuationType, double muffle) {
        this(bounces, reverbBounces, segmentLength, permeation, reverb, permeationAbsorption, maxBlocksPermeated,
                bounceAbsorption, attenuationType, muffle, reverbBounces > bounces);
    }

    public TraceSettings(int bounces, int reverbBounces, double segmentLength, boolean permeation, boolean reverb,
                         double permeationAbsorption, double maxBlocksPermeated, double bounceAbsorption,
                         RedsAttenuationType attenuationType, double muffle, boolean measureRoom) {
        this.measureRoom = measureRoom;
        this.muffle = Math.max(muffle, 0.0);
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

    /** Whether sounds connect to bounces identically under both (so a recorded trail can be reused). */
    public boolean connectsLike(TraceSettings o) {
        return o != null && bounces == o.bounces && segmentLength == o.segmentLength && permeation == o.permeation
                && reverb == o.reverb && permeationAbsorption == o.permeationAbsorption
                && maxBlocksPermeated == o.maxBlocksPermeated && bounceAbsorption == o.bounceAbsorption
                && attenuationType == o.attenuationType && muffle == o.muffle;
    }

    /** Treble transmission through what was in the way, from the bass and treble obstruction. */
    public double transmissionHf(double obstruction, double obstructionHf) {
        return Math.pow(permeationAbsorption, obstruction + (obstructionHf - obstruction) * muffle);
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
