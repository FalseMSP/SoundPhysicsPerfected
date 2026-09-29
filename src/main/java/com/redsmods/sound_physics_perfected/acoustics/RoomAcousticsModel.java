package com.redsmods.sound_physics_perfected.acoustics;

/**
 * Turns the listener's ray statistics into EAX reverb parameters using standard room acoustics:
 *
 * <ul>
 *   <li>Mean free path (average distance between reflections, 4V/S) from the surface-to-surface ray segments.</li>
 *   <li>Mean surface absorption per band from the materials the rays hit. Rays escaping to open air count as a
 *       perfectly absorbing "open window", so openness is handled by the same equations.</li>
 *   <li>Decay time per band from the Eyring formula with air absorption:
 *       RT60 = 0.161·V / (−S·ln(1−α) + 4mV) = (0.161/4)·mfp / (−ln(1−α) + m·mfp).</li>
 *   <li>Reverberant level from the room constant R = S·α/(1−α) (reverberant energy ∝ 1/R), perceptually
 *       compressed, and early reflection level/delay from the distance and reflectivity of the nearest surfaces.</li>
 *   <li>Discrete echoes when distant reflective surfaces are seen from an otherwise open position.</li>
 * </ul>
 *
 * Pure Java so it can be tested offline.
 */
public final class RoomAcousticsModel {
    /** Energy attenuation of air per metre at ~250 Hz / ~1 kHz / ~5 kHz (20 °C, 50 % RH). */
    static final double AIR_LOW = 0.0002, AIR_MID = 0.0012, AIR_HIGH = 0.008;

    /** Late reverb gain for the reference room (a bare ~5x5x3 stone room), and that room's 1/R. */
    static final double LATE_GAIN_REFERENCE = 1.2;
    static final double INVERSE_ROOM_CONSTANT_REFERENCE = 0.17;
    /**
     * Exponent compressing reverberant level against room size. Physics says 0.5 (energy ∝ 1/R); games
     * need big caves to still sound big, and the late tail's length already conveys size.
     */
    static final double LATE_GAIN_EXPONENT = 0.25;
    /** EAX GAIN (−7 dB) at the default "Global Reverb Intensity" of 2. */
    static final double MASTER_GAIN_PER_INTENSITY = 0.2236;

    private RoomAcousticsModel() {
    }

    public static final class Settings {
        public final double speedOfSound;
        public final double intensity;
        public final double maxGain;
        public final double decayMultiplier;
        public final double maxDecayTime;
        public final double reflectionsMultiplier;
        public final double lateMultiplier;
        public final double echoMultiplier;
        public final double hfReference;
        public final double lfReference;

        public Settings(double speedOfSound, double intensity, double maxGain, double decayMultiplier,
                        double maxDecayTime, double reflectionsMultiplier, double lateMultiplier,
                        double echoMultiplier, double hfReference, double lfReference) {
            this.speedOfSound = Math.max(speedOfSound, 1.0);
            this.intensity = Math.max(intensity, 0.0);
            this.maxGain = maxGain;
            this.decayMultiplier = Math.max(decayMultiplier, 0.0);
            this.maxDecayTime = Math.max(maxDecayTime, 0.1);
            this.reflectionsMultiplier = Math.max(reflectionsMultiplier, 0.0);
            this.lateMultiplier = Math.max(lateMultiplier, 0.0);
            this.echoMultiplier = Math.max(echoMultiplier, 0.0);
            this.hfReference = hfReference;
            this.lfReference = lfReference;
        }

        public static Settings defaults() {
            return new Settings(343.0, 2.0, 1.0, 1.0, 5.0, 1.0, 1.0, 1.0, 5000.0, 250.0);
        }
    }

    public static ReverbParams estimate(ReverbStats s, Settings cfg) {
        int hits = s.totalHits();
        int escapes = s.firstEscapes + s.laterEscapes;
        if (s.rays == 0 || hits == 0) {
            return openAir(cfg);
        }
        double c = cfg.speedOfSound;
        double rays = s.rays;

        // --- Geometry -------------------------------------------------------------------------------
        double enclosure = s.firstHits / rays;                    // fraction of directions with a surface
        double escapeProbability = escapes / (double) (hits + escapes);
        double meanFreePath;
        if (s.laterHits >= Math.max(8, s.rays / 20)) {
            meanFreePath = s.laterDistanceSum / s.laterHits;
        } else if (s.firstHits > 0) {
            // Listener near the middle of a space: mean distance to walls ≈ 3/4 of the chord length.
            meanFreePath = (4.0 / 3.0) * s.firstDistanceSum / s.firstHits;
        } else {
            meanFreePath = s.segmentLength;
        }
        meanFreePath = clamp(meanFreePath, 0.5, Math.max(s.segmentLength, 0.5));
        double visibleArea = Math.max(4.0 * Math.PI / rays * s.firstAreaSum, 1.0);

        // --- Absorption (surfaces + openings) ---------------------------------------------------------
        double absLow = effectiveAbsorption(s.absorptionLowSum / hits, escapeProbability);
        double absMid = effectiveAbsorption(s.absorptionMidSum / hits, escapeProbability);
        double absHigh = effectiveAbsorption(s.absorptionHighSum / hits, escapeProbability);

        // --- Decay (Eyring + air absorption), then scaled and softly limited -----------------------
        double eyring = 24.0 * Math.log(10.0) / c / 4.0 * meanFreePath;
        double tLow = shapeDecay(eyring / (-Math.log(1.0 - absLow) + AIR_LOW * meanFreePath), cfg);
        double tMid = shapeDecay(eyring / (-Math.log(1.0 - absMid) + AIR_MID * meanFreePath), cfg);
        double tHigh = shapeDecay(eyring / (-Math.log(1.0 - absHigh) + AIR_HIGH * meanFreePath), cfg);

        // --- Levels ---------------------------------------------------------------------------------
        double inverseRoomLow = (1.0 - absLow) / (absLow * visibleArea);
        double inverseRoomMid = (1.0 - absMid) / (absMid * visibleArea);
        double inverseRoomHigh = (1.0 - absHigh) / (absHigh * visibleArea);
        double late = LATE_GAIN_REFERENCE
                * Math.pow(inverseRoomMid / INVERSE_ROOM_CONSTANT_REFERENCE, LATE_GAIN_EXPONENT)
                * cfg.lateMultiplier;
        double gainHF = Math.min(1.0, inverseRoomHigh / inverseRoomMid);
        double gainLF = Math.min(1.0, inverseRoomLow / inverseRoomMid);

        double reflections = 0.0, reflectionsDelay = 0.0;
        if (s.firstHits > 0) {
            double reflectivity = s.firstReflectivitySum / s.firstHits;
            double nearDistance = s.firstHits / s.firstInverseDistanceSum; // harmonic mean: near walls dominate
            reflections = 0.9 * reflectivity * Math.pow(enclosure, 1.5) * Math.sqrt(4.0 / (4.0 + nearDistance))
                    * cfg.reflectionsMultiplier;
            reflectionsDelay = 2.0 * nearDistance / c;
        }
        double lateDelay = 0.7 * meanFreePath / c + 0.005;

        // --- Texture --------------------------------------------------------------------------------
        double density = meanFreePath / 6.0;                     // small rooms: sparse, "boxy" modes
        double closed = 1.0 - escapeProbability;
        double diffusion = 0.25 + 0.75 * closed * closed;        // open air: few, distinct reflections

        double echoDepth = 0.0, echoTime = 0.25;
        if (s.farHits > 0) {
            double openness = s.firstEscapes / rays;
            double farReflectivity = s.farReflectivitySum / s.farHits;
            double farFraction = s.farHits / rays;
            echoDepth = 2.0 * openness * farReflectivity * Math.min(1.0, 4.0 * farFraction) * cfg.echoMultiplier;
            echoTime = 2.0 * (s.farDistanceSum / s.farHits) / c;
        }

        double gain = Math.min(MASTER_GAIN_PER_INTENSITY * cfg.intensity, Math.min(1.0, cfg.maxGain));

        // OpenAL Soft (1.23, as bundled with Minecraft) renders an LF decay ratio above 1 with a large overshoot:
        // measured, a ratio of 1.57 on a 1.71 s decay rings for ~4.4 s in the mids and ~6.5 s in the lows, and a
        // ratio of 2 leaves a long low-level rumble. HF ratios behave. So longer bass decay is not requested.
        double lfRatio = Math.min(tLow / tMid, 1.0);

        return new ReverbParams(
                (float) density, (float) diffusion, (float) gain, (float) gainHF, (float) gainLF,
                (float) tMid, (float) (tHigh / tMid), (float) lfRatio,
                (float) reflections, (float) reflectionsDelay, (float) late, (float) lateDelay,
                (float) echoTime, (float) echoDepth, 0.25f, 0.0f,
                0.994f, (float) cfg.hfReference, (float) cfg.lfReference, 0.0f,
                (float) enclosure, (float) meanFreePath, (float) absMid);
    }

    /** Parameters for a listener with nothing around (e.g. mid air): no reverb at all. */
    public static ReverbParams openAir(Settings cfg) {
        double gain = Math.min(MASTER_GAIN_PER_INTENSITY * cfg.intensity, Math.min(1.0, cfg.maxGain));
        return new ReverbParams(1.0f, 0.25f, (float) gain, 1.0f, 1.0f,
                0.1f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f,
                0.25f, 0.0f, 0.25f, 0.0f, 0.994f, (float) cfg.hfReference, (float) cfg.lfReference, 0.0f,
                0.0f, 0.0f, 1.0f);
    }

    private static double effectiveAbsorption(double surfaceAbsorption, double escapeProbability) {
        return clamp(escapeProbability + (1.0 - escapeProbability) * surfaceAbsorption, 0.001, 0.999);
    }

    /** Applies the user's multiplier and a soft limit that leaves short decays untouched. */
    private static double shapeDecay(double t, Settings cfg) {
        t *= cfg.decayMultiplier;
        double r = t / cfg.maxDecayTime;
        return Math.max(t / Math.sqrt(1.0 + r * r), 0.1);
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}
