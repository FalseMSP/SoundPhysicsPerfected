package com.redsmods.sound_physics_perfected.acoustics;

import java.util.Locale;

/**
 * One complete EAX reverb parameter set. Every value is clamped to its legal OpenAL EFX range on
 * construction (an out of range value makes alEffectf fail and silently keep the old value).
 */
public final class ReverbParams {
    public final float density;
    public final float diffusion;
    public final float gain;
    public final float gainHF;
    public final float gainLF;
    public final float decayTime;
    public final float decayHFRatio;
    public final float decayLFRatio;
    public final float reflectionsGain;
    public final float reflectionsDelay;
    public final float lateReverbGain;
    public final float lateReverbDelay;
    public final float echoTime;
    public final float echoDepth;
    public final float modulationTime;
    public final float modulationDepth;
    public final float airAbsorptionGainHF;
    public final float hfReference;
    public final float lfReference;
    public final float roomRolloffFactor;

    /**
     * World-space direction the early reflections / late reverb come from, scaled by how focused they are
     * (0 = all around, 1 = entirely from that direction). Converted to listener space when applied.
     */
    public final float reflectionsPanX, reflectionsPanY, reflectionsPanZ;
    public final float lateReverbPanX, lateReverbPanY, lateReverbPanZ;

    // Diagnostics (not sent to OpenAL)
    public final float enclosure;
    public final float meanFreePath;
    public final float absorption;

    public ReverbParams(float density, float diffusion, float gain, float gainHF, float gainLF,
                        float decayTime, float decayHFRatio, float decayLFRatio,
                        float reflectionsGain, float reflectionsDelay, float lateReverbGain, float lateReverbDelay,
                        float echoTime, float echoDepth, float modulationTime, float modulationDepth,
                        float airAbsorptionGainHF, float hfReference, float lfReference, float roomRolloffFactor,
                        float enclosure, float meanFreePath, float absorption) {
        this(density, diffusion, gain, gainHF, gainLF, decayTime, decayHFRatio, decayLFRatio, reflectionsGain,
                reflectionsDelay, lateReverbGain, lateReverbDelay, echoTime, echoDepth, modulationTime, modulationDepth,
                airAbsorptionGainHF, hfReference, lfReference, roomRolloffFactor, 0, 0, 0, 0, 0, 0,
                enclosure, meanFreePath, absorption);
    }

    public ReverbParams(float density, float diffusion, float gain, float gainHF, float gainLF,
                        float decayTime, float decayHFRatio, float decayLFRatio,
                        float reflectionsGain, float reflectionsDelay, float lateReverbGain, float lateReverbDelay,
                        float echoTime, float echoDepth, float modulationTime, float modulationDepth,
                        float airAbsorptionGainHF, float hfReference, float lfReference, float roomRolloffFactor,
                        float reflectionsPanX, float reflectionsPanY, float reflectionsPanZ,
                        float lateReverbPanX, float lateReverbPanY, float lateReverbPanZ,
                        float enclosure, float meanFreePath, float absorption) {
        this.reflectionsPanX = finite(reflectionsPanX);
        this.reflectionsPanY = finite(reflectionsPanY);
        this.reflectionsPanZ = finite(reflectionsPanZ);
        this.lateReverbPanX = finite(lateReverbPanX);
        this.lateReverbPanY = finite(lateReverbPanY);
        this.lateReverbPanZ = finite(lateReverbPanZ);
        this.density = clamp(density, 0.0f, 1.0f);
        this.diffusion = clamp(diffusion, 0.0f, 1.0f);
        this.gain = clamp(gain, 0.0f, 1.0f);
        this.gainHF = clamp(gainHF, 0.0f, 1.0f);
        this.gainLF = clamp(gainLF, 0.0f, 1.0f);
        this.decayTime = clamp(decayTime, 0.1f, 20.0f);
        this.decayHFRatio = clamp(decayHFRatio, 0.1f, 2.0f);
        this.decayLFRatio = clamp(decayLFRatio, 0.1f, 2.0f);
        this.reflectionsGain = clamp(reflectionsGain, 0.0f, 3.16f);
        this.reflectionsDelay = clamp(reflectionsDelay, 0.0f, 0.3f);
        this.lateReverbGain = clamp(lateReverbGain, 0.0f, 10.0f);
        this.lateReverbDelay = clamp(lateReverbDelay, 0.0f, 0.1f);
        this.echoTime = clamp(echoTime, 0.075f, 0.25f);
        this.echoDepth = clamp(echoDepth, 0.0f, 1.0f);
        this.modulationTime = clamp(modulationTime, 0.04f, 4.0f);
        this.modulationDepth = clamp(modulationDepth, 0.0f, 1.0f);
        this.airAbsorptionGainHF = clamp(airAbsorptionGainHF, 0.892f, 1.0f);
        this.hfReference = clamp(hfReference, 1000.0f, 20000.0f);
        this.lfReference = clamp(lfReference, 20.0f, 1000.0f);
        this.roomRolloffFactor = clamp(roomRolloffFactor, 0.0f, 10.0f);
        this.enclosure = enclosure;
        this.meanFreePath = meanFreePath;
        this.absorption = absorption;
    }

    /** This preset with another set's master gain (so presets follow the user's reverb intensity). */
    public ReverbParams withGainOf(ReverbParams o) {
        return new ReverbParams(density, diffusion, o.gain, gainHF, gainLF, decayTime, decayHFRatio, decayLFRatio,
                reflectionsGain, reflectionsDelay, lateReverbGain, lateReverbDelay, echoTime, echoDepth, modulationTime,
                modulationDepth, airAbsorptionGainHF, hfReference, lfReference, roomRolloffFactor,
                reflectionsPanX, reflectionsPanY, reflectionsPanZ, lateReverbPanX, lateReverbPanY, lateReverbPanZ,
                enclosure, meanFreePath, absorption);
    }

    /** Exponential smoothing step from this towards target by fraction t (0..1). */
    public ReverbParams approach(ReverbParams target, float t) {
        return new ReverbParams(
                lerp(density, target.density, t),
                lerp(diffusion, target.diffusion, t),
                lerp(gain, target.gain, t),
                lerp(gainHF, target.gainHF, t),
                lerp(gainLF, target.gainLF, t),
                // Decay is perceived logarithmically; interpolate it that way so short→long feels even.
                (float) Math.exp(lerp((float) Math.log(decayTime), (float) Math.log(target.decayTime), t)),
                lerp(decayHFRatio, target.decayHFRatio, t),
                lerp(decayLFRatio, target.decayLFRatio, t),
                lerp(reflectionsGain, target.reflectionsGain, t),
                lerp(reflectionsDelay, target.reflectionsDelay, t),
                lerp(lateReverbGain, target.lateReverbGain, t),
                lerp(lateReverbDelay, target.lateReverbDelay, t),
                lerp(echoTime, target.echoTime, t),
                lerp(echoDepth, target.echoDepth, t),
                lerp(modulationTime, target.modulationTime, t),
                lerp(modulationDepth, target.modulationDepth, t),
                lerp(airAbsorptionGainHF, target.airAbsorptionGainHF, t),
                lerp(hfReference, target.hfReference, t),
                lerp(lfReference, target.lfReference, t),
                lerp(roomRolloffFactor, target.roomRolloffFactor, t),
                lerp(reflectionsPanX, target.reflectionsPanX, t),
                lerp(reflectionsPanY, target.reflectionsPanY, t),
                lerp(reflectionsPanZ, target.reflectionsPanZ, t),
                lerp(lateReverbPanX, target.lateReverbPanX, t),
                lerp(lateReverbPanY, target.lateReverbPanY, t),
                lerp(lateReverbPanZ, target.lateReverbPanZ, t),
                lerp(enclosure, target.enclosure, t),
                lerp(meanFreePath, target.meanFreePath, t),
                lerp(absorption, target.absorption, t));
    }

    /** Whether the audible parameters differ by more than a barely perceptible amount. */
    public boolean differsAudiblyFrom(ReverbParams o) {
        return differsStructurallyFrom(o) || differsInLevelsFrom(o);
    }

    /**
     * Parameters whose change makes OpenAL Soft rebuild its reverb network: it switches to a second network
     * and fades the old one out, so these should not change on every tick.
     */
    public boolean differsStructurallyFrom(ReverbParams o) {
        return rel(decayTime, o.decayTime) > 0.01f
                || Math.abs(decayHFRatio - o.decayHFRatio) > 0.005f
                || Math.abs(decayLFRatio - o.decayLFRatio) > 0.005f
                || Math.abs(density - o.density) > 0.01f
                || Math.abs(diffusion - o.diffusion) > 0.01f
                || Math.abs(modulationTime - o.modulationTime) > 0.01f
                || Math.abs(modulationDepth - o.modulationDepth) > 0.01f
                || Math.abs(hfReference - o.hfReference) > 1.0f
                || Math.abs(lfReference - o.lfReference) > 1.0f;
    }

    /** Levels, delays and filters: cheap for OpenAL Soft to change at any time. */
    public boolean differsInLevelsFrom(ReverbParams o) {
        return Math.abs(gain - o.gain) > 0.002f
                || Math.abs(lateReverbGain - o.lateReverbGain) > 0.005f
                || Math.abs(reflectionsGain - o.reflectionsGain) > 0.005f
                || Math.abs(gainHF - o.gainHF) > 0.005f
                || Math.abs(gainLF - o.gainLF) > 0.005f
                || Math.abs(reflectionsDelay - o.reflectionsDelay) > 0.0005f
                || Math.abs(lateReverbDelay - o.lateReverbDelay) > 0.0005f
                || Math.abs(echoDepth - o.echoDepth) > 0.01f
                || Math.abs(echoTime - o.echoTime) > 0.002f
                || Math.abs(airAbsorptionGainHF - o.airAbsorptionGainHF) > 0.0005f
                || Math.abs(roomRolloffFactor - o.roomRolloffFactor) > 0.01f
                || Math.abs(reflectionsPanX - o.reflectionsPanX) > 0.02f
                || Math.abs(reflectionsPanY - o.reflectionsPanY) > 0.02f
                || Math.abs(reflectionsPanZ - o.reflectionsPanZ) > 0.02f
                || Math.abs(lateReverbPanX - o.lateReverbPanX) > 0.02f
                || Math.abs(lateReverbPanY - o.lateReverbPanY) > 0.02f
                || Math.abs(lateReverbPanZ - o.lateReverbPanZ) > 0.02f;
    }

    /** These levels with the structural parameters of another set (bit-identical, so OpenAL sees no change). */
    public ReverbParams withStructureOf(ReverbParams s) {
        return new ReverbParams(s.density, s.diffusion, gain, gainHF, gainLF,
                s.decayTime, s.decayHFRatio, s.decayLFRatio,
                reflectionsGain, reflectionsDelay, lateReverbGain, lateReverbDelay,
                echoTime, echoDepth, s.modulationTime, s.modulationDepth,
                airAbsorptionGainHF, s.hfReference, s.lfReference, roomRolloffFactor,
                reflectionsPanX, reflectionsPanY, reflectionsPanZ, lateReverbPanX, lateReverbPanY, lateReverbPanZ,
                enclosure, meanFreePath, absorption);
    }

    private static float rel(float a, float b) {
        return Math.abs(a - b) / Math.max(Math.abs(b), 1.0E-6f);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float finite(float v) {
        return Float.isFinite(v) ? v : 0.0f;
    }

    static float clamp(float v, float min, float max) {
        if (Float.isNaN(v)) return min;
        return v < min ? min : (v > max ? max : v);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT,
                "Reverb: decay=%.2fs hf=%.2f lf=%.2f late=%.2f@%.0fms refl=%.2f@%.0fms gainHF=%.2f echo=%.2f@%.0fms pan=%.2f/%.2f | enclosed=%.0f%% mfp=%.1f abs=%.2f",
                decayTime, decayHFRatio, decayLFRatio, lateReverbGain, lateReverbDelay * 1000, reflectionsGain,
                reflectionsDelay * 1000, gainHF, echoDepth, echoTime * 1000,
                Math.sqrt(reflectionsPanX * reflectionsPanX + reflectionsPanY * reflectionsPanY + reflectionsPanZ * reflectionsPanZ),
                Math.sqrt(lateReverbPanX * lateReverbPanX + lateReverbPanY * lateReverbPanY + lateReverbPanZ * lateReverbPanZ),
                enclosure * 100, meanFreePath, absorption);
    }
}
