package com.redsmods.sound_physics_perfected;

import com.redsmods.sound_physics_perfected.acoustics.ReverbParams;
import com.redsmods.sound_physics_perfected.config.Config;
import com.redsmods.sound_physics_perfected.config.DebugType;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;

import static com.redsmods.sound_physics_perfected.RaycastingHelper.*;
import static org.joml.Math.lerp;
import static org.lwjgl.openal.EXTEfx.*;

/**
 * Owns the EFX objects for one OpenAL context (the game's, or Simple Voice Chat's).
 */
public class OpenALEffectsHandler {
    private int auxFXSlot = 0;
    private int reverbEffect = 0;
    private int muffleFilter = 0;
    private int sendFilter = 0;
    private int directBlockFilter = 0;
    public boolean efxInitialized = false;

    /** Parameters currently loaded into the effect slot (null = none / legacy). */
    private ReverbParams loadedParams;
    private boolean legacyParamsLoaded;
    private long lastStructuralLoad;
    /** Listener-space pan vectors currently loaded (reflections xyz, late reverb xyz). */
    private final float[] loadedPans = new float[6];
    /** Minimum time between reverb network rebuilds (decay, density, diffusion... changes). */
    private static final long STRUCTURAL_INTERVAL_NANOS = 250_000_000L;

    public int getDirectBlockFilter() {
        return directBlockFilter;
    }

    // ================================================================================================================
    // Modern (ray traced) reverb
    // ================================================================================================================

    /**
     * Loads a reverb environment into the effect slot, if it changed audibly since the last load.
     * EFX slots take a copy of the effect when it is attached, so the effect must be re-attached after its
     * parameters change; without that, parameter changes are never heard.
     */
    public void applyEnvironment(ReverbParams p) {
        if (!efxInitialized || p == null) return;
        float[] pans = listenerSpacePans(p, RaycastingHelper.getListenerBasis());
        long now = System.nanoTime();
        if (loadedParams != null && !legacyParamsLoaded) {
            boolean structural = p.differsStructurallyFrom(loadedParams);
            if (structural && now - lastStructuralLoad < STRUCTURAL_INTERVAL_NANOS) {
                // OpenAL Soft rebuilds (and cross-fades) its reverb network whenever decay, density etc. change.
                // Doing that every tick of a transition splits tails between networks, so those parameters
                // follow at a slower rate while levels keep updating every tick.
                p = p.withStructureOf(loadedParams);
                structural = false;
            }
            boolean turned = false;
            for (int i = 0; i < 6; i++) turned |= Math.abs(pans[i] - loadedPans[i]) > 0.03f;
            if (!structural && !turned && !p.differsInLevelsFrom(loadedParams)) return;
            if (structural) lastStructuralLoad = now;
        } else {
            lastStructuralLoad = now;
        }

        alEffectf(reverbEffect, AL_EAXREVERB_DENSITY, p.density);
        alEffectf(reverbEffect, AL_EAXREVERB_DIFFUSION, p.diffusion);
        alEffectf(reverbEffect, AL_EAXREVERB_GAIN, p.gain);
        alEffectf(reverbEffect, AL_EAXREVERB_GAINHF, p.gainHF);
        alEffectf(reverbEffect, AL_EAXREVERB_GAINLF, p.gainLF);
        alEffectf(reverbEffect, AL_EAXREVERB_DECAY_TIME, p.decayTime);
        alEffectf(reverbEffect, AL_EAXREVERB_DECAY_HFRATIO, p.decayHFRatio);
        alEffectf(reverbEffect, AL_EAXREVERB_DECAY_LFRATIO, p.decayLFRatio);
        alEffectf(reverbEffect, AL_EAXREVERB_REFLECTIONS_GAIN, p.reflectionsGain);
        alEffectf(reverbEffect, AL_EAXREVERB_REFLECTIONS_DELAY, p.reflectionsDelay);
        alEffectf(reverbEffect, AL_EAXREVERB_LATE_REVERB_GAIN, p.lateReverbGain);
        alEffectf(reverbEffect, AL_EAXREVERB_LATE_REVERB_DELAY, p.lateReverbDelay);
        alEffectf(reverbEffect, AL_EAXREVERB_ECHO_TIME, p.echoTime);
        alEffectf(reverbEffect, AL_EAXREVERB_ECHO_DEPTH, p.echoDepth);
        alEffectf(reverbEffect, AL_EAXREVERB_MODULATION_TIME, p.modulationTime);
        alEffectf(reverbEffect, AL_EAXREVERB_MODULATION_DEPTH, p.modulationDepth);
        alEffectf(reverbEffect, AL_EAXREVERB_AIR_ABSORPTION_GAINHF, p.airAbsorptionGainHF);
        alEffectf(reverbEffect, AL_EAXREVERB_HFREFERENCE, p.hfReference);
        alEffectf(reverbEffect, AL_EAXREVERB_LFREFERENCE, p.lfReference);
        alEffectf(reverbEffect, AL_EAXREVERB_ROOM_ROLLOFF_FACTOR, p.roomRolloffFactor);
        alEffectfv(reverbEffect, AL_EAXREVERB_REFLECTIONS_PAN, new float[]{pans[0], pans[1], pans[2]});
        alEffectfv(reverbEffect, AL_EAXREVERB_LATE_REVERB_PAN, new float[]{pans[3], pans[4], pans[5]});
        // Air absorption is already part of the modelled HF decay; do not let EFX limit it a second time.
        alEffecti(reverbEffect, AL_EAXREVERB_DECAY_HFLIMIT, AL10.AL_FALSE);
        alAuxiliaryEffectSloti(auxFXSlot, AL_EFFECTSLOT_EFFECT, reverbEffect);
        logALError("Failed to load reverb environment");

        loadedParams = p;
        legacyParamsLoaded = false;
        System.arraycopy(pans, 0, loadedPans, 0, 6);
    }

    /**
     * EAX pan vectors are relative to the listener, in left-handed coordinates (+X right, +Y up, +Z forward),
     * while the environment keeps them in world space; project onto the listener's axes.
     * basis = right xyz, up xyz, forward xyz.
     */
    static float[] listenerSpacePans(ReverbParams p, float[] basis) {
        return new float[]{
                dot(p.reflectionsPanX, p.reflectionsPanY, p.reflectionsPanZ, basis, 0),
                dot(p.reflectionsPanX, p.reflectionsPanY, p.reflectionsPanZ, basis, 3),
                dot(p.reflectionsPanX, p.reflectionsPanY, p.reflectionsPanZ, basis, 6),
                dot(p.lateReverbPanX, p.lateReverbPanY, p.lateReverbPanZ, basis, 0),
                dot(p.lateReverbPanX, p.lateReverbPanY, p.lateReverbPanZ, basis, 3),
                dot(p.lateReverbPanX, p.lateReverbPanY, p.lateReverbPanZ, basis, 6)};
    }

    private static float dot(float x, float y, float z, float[] basis, int o) {
        return x * basis[o] + y * basis[o + 1] + z * basis[o + 2];
    }

    /** The legacy modes were always heard with the initial parameters; put those back when switching to them. */
    public void useLegacyParams() {
        if (!efxInitialized || legacyParamsLoaded) return;
        // Recreate the effect so every parameter returns to the EAX default, exactly as at start-up.
        EXTEfx.alDeleteEffects(reverbEffect);
        reverbEffect = EXTEfx.alGenEffects();
        EXTEfx.alEffecti(reverbEffect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB);
        setBasicReverbParams();
        alAuxiliaryEffectSloti(auxFXSlot, AL_EFFECTSLOT_EFFECT, reverbEffect);
        legacyParamsLoaded = true;
        loadedParams = null;
    }

    /**
     * Routes a source into the reverb. The send goes through a low-pass matching the source's occlusion
     * (0 = clear, 1 = fully muffled), so a muffled sound does not produce bright, unmuffled reverb.
     */
    public void attachReverbSend(int sourceId, float muffleStrength) {
        attachReverbSend(sourceId, muffleStrength, lerp(1.0f, 0.1f, clamp(muffleStrength, 0.0f, 1.0f)));
    }

    /** @param hfRatio how much of the treble gets through relative to the bass (1 = unfiltered) */
    public void attachReverbSend(int sourceId, float muffleStrength, float hfRatio) {
        float m = clamp(muffleStrength, 0.0f, 1.0f);
        EXTEfx.alFilterf(sendFilter, AL_LOWPASS_GAIN, lerp(1.0f, 0.2f, m));
        EXTEfx.alFilterf(sendFilter, AL_LOWPASS_GAINHF, clamp(hfRatio, 0.01f, 1.0f));
        AL11.alSource3i(sourceId, AL_AUXILIARY_SEND_FILTER, auxFXSlot, 0, sendFilter);
    }

    /** Current environment + reverb send for one source. Used by voice chat, on its own context and thread. */
    public void applyReverbToSource(int sourceId) {
        if (!efxInitialized) return;
        if (!EXTEfx.alIsAuxiliaryEffectSlot(auxFXSlot)) {
            System.err.println("auxFXSlot " + auxFXSlot + " is not valid in the current context");
            cleanupEFXResources();
            initializeReverb();
            return;
        }
        applyEnvironment(RaycastingHelper.getCurrentReverb());
        attachReverbSend(sourceId, 0.0f);
    }

    /** Re-creates the EFX objects if the context lost them (e.g. device reset). Returns whether EFX is usable. */
    public boolean ensureValid() {
        if (!efxInitialized) {
            initializeReverb();
            return efxInitialized;
        }
        if (!EXTEfx.alIsAuxiliaryEffectSlot(auxFXSlot)) {
            cleanupEFXResources();
            initializeReverb();
        }
        return efxInitialized;
    }

    // ================================================================================================================
    // Legacy reverb modes (kept exactly as they were, including only ever changing the send level)
    // ================================================================================================================

    public void applyLegacyReverbToSource(int sourceId) {
        try {
            if (getDistanceFromWallEchoDenom() == 0 || getReverbDenom() == 0 || getOutdoorLeakDenom() == 0)
                return;

            float wallDistance = (float) (RaycastingHelper.getDistanceFromWallEcho() / RaycastingHelper.getDistanceFromWallEchoDenom());
            float occlusionPercent = (float) RaycastingHelper.getReverbStrength() / RaycastingHelper.getReverbDenom();
            occlusionPercent = 1.0f - occlusionPercent;
            float outdoorLeakPercent = (float) RaycastingHelper.getOutdoorLeak() / RaycastingHelper.getOutdoorLeakDenom();
            outdoorLeakPercent = outdoorLeakPercent * 2;

            float distanceMeters     = clamp(wallDistance, 1.0f, 100.0f);
            occlusionPercent   = 1 - clamp(occlusionPercent+outdoorLeakPercent, 0.0f, 1.0f);
            outdoorLeakPercent = clamp(outdoorLeakPercent, 0.0f, 1.0f);

            float dryFactor = 1.0f - outdoorLeakPercent; // 0 = fully outdoor, 1 = fully indoor
            float speedOfSound = 343.0f;

            float wallDelay = (distanceMeters * 2.0f) / speedOfSound;

            float decayTime        = clamp(wallDelay * 5.0f * dryFactor, 0.1f, 6.0f);
            float reflectionsDelay = clamp(wallDelay * 0.5f, 0.005f, 0.05f);
            float lateReverbDelay  = clamp(wallDelay, 0.01f, 0.1f);

            float decayHfRatio     = lerp(0.5f, 1.3f, (1.0f - occlusionPercent) * dryFactor);
            float diffusion        = lerp(0.3f, 1.0f, dryFactor * (1.0f - occlusionPercent));
            float gainHF           = lerp(0.05f, 0.9f, (1.0f - occlusionPercent) * dryFactor);

            float reflectionsGain  = lerp(0.0f, 0.7f, dryFactor);
            float lateReverbGain   = lerp(0.0f, 1.0f, dryFactor);

            float density          = lerp(0.3f, 1.0f, dryFactor);
            float gain             = lerp(0.05f, 0.3f, dryFactor);
            float airAbsorptionHF  = lerp(0.95f, 0.99f, dryFactor);
            float roomRolloff      = 0.4f;

            // Apply to OpenAL effect
            EXTEfx.alFilterf(sendFilter, EXTEfx.AL_LOWPASS_GAIN, gain);
            EXTEfx.alFilterf(sendFilter, EXTEfx.AL_LOWPASS_GAINHF, 1.0f);
            alEffectf(reverbEffect, AL_EAXREVERB_DENSITY,                density);
            alEffectf(reverbEffect, AL_EAXREVERB_GAIN,                   gain);
            alEffectf(reverbEffect, AL_EAXREVERB_AIR_ABSORPTION_GAINHF,  airAbsorptionHF);
            alEffectf(reverbEffect, AL_EAXREVERB_ROOM_ROLLOFF_FACTOR,    roomRolloff);
            alEffectf(reverbEffect, AL_EAXREVERB_DECAY_TIME,         decayTime);
            alEffectf(reverbEffect, AL_EAXREVERB_DECAY_HFRATIO,      decayHfRatio);
            alEffectf(reverbEffect, AL_EAXREVERB_DIFFUSION,          diffusion);
            alEffectf(reverbEffect, AL_EAXREVERB_GAINHF,             gainHF);
            alEffectf(reverbEffect, AL_EAXREVERB_REFLECTIONS_DELAY,  reflectionsDelay);
            alEffectf(reverbEffect, AL_EAXREVERB_LATE_REVERB_DELAY,  lateReverbDelay);
            alEffectf(reverbEffect, AL_EAXREVERB_REFLECTIONS_GAIN,   reflectionsGain);
            alEffectf(reverbEffect, AL_EAXREVERB_LATE_REVERB_GAIN,   lateReverbGain);
            AL11.alSource3i(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, auxFXSlot, 0, sendFilter);
        } catch (Exception e) {
        }
    }

    public void applyInitalLegacyReverbToSource(int sourceId) {
        try {
            float wallDistance = (float) (RaycastingHelper.getDistanceFromWallEcho() / RaycastingHelper.getDistanceFromWallEchoDenom());
            float occlusionPercent = (float) RaycastingHelper.getReverbStrength() / RaycastingHelper.getReverbDenom();
            float outdoorLeakPercent = (float) RaycastingHelper.getOutdoorLeak() / RaycastingHelper.getOutdoorLeakDenom();

            float distanceMeters     = clamp(wallDistance, 1.0f, 100.0f);
            occlusionPercent   = 1 - clamp(occlusionPercent+outdoorLeakPercent, 0.0f, 1.0f);
            outdoorLeakPercent = clamp(outdoorLeakPercent, 0.0f, 1.0f);

            float dryFactor = 1.0f - outdoorLeakPercent; // 0 = fully outdoor, 1 = fully indoor
            float speedOfSound = 343.0f;

            float wallDelay = (distanceMeters * 2.0f) / speedOfSound;

            float decayTime        = clamp(wallDelay * 5.0f * dryFactor, 0.1f, 6.0f);
            float reflectionsDelay = clamp(wallDelay * 0.5f, 0.005f, 0.05f);
            float lateReverbDelay  = clamp(wallDelay, 0.01f, 0.1f);

            float decayHfRatio     = lerp(0.5f, 1.3f, (1.0f - occlusionPercent) * dryFactor);
            float diffusion        = lerp(0.3f, 1.0f, dryFactor * (1.0f - occlusionPercent));
            float gainHF           = lerp(0.05f, 0.9f, (1.0f - occlusionPercent) * dryFactor);

            float reflectionsGain  = lerp(0.0f, 0.7f, dryFactor);
            float lateReverbGain   = lerp(0.0f, 1.0f, dryFactor);

            float density          = lerp(0.3f, 1.0f, dryFactor);
            float gain             = lerp(0.05f, 0.3f, dryFactor);
            float airAbsorptionHF  = lerp(0.95f, 0.99f, dryFactor);
            float roomRolloff      = 0.4f;

            // Apply to OpenAL effect
            AL11.alSourcef(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER_GAIN_AUTO, AL11.AL_FALSE);
            AL11.alSourcef(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER_GAINHF_AUTO, AL11.AL_FALSE);
            EXTEfx.alFilterf(sendFilter, EXTEfx.AL_LOWPASS_GAIN, gain);
            EXTEfx.alFilterf(sendFilter, EXTEfx.AL_LOWPASS_GAINHF, 1.0f);
            alEffectf(reverbEffect, AL_EAXREVERB_DENSITY,                density);
            alEffectf(reverbEffect, AL_EAXREVERB_GAIN,                   gain);
            alEffectf(reverbEffect, AL_EAXREVERB_AIR_ABSORPTION_GAINHF,  airAbsorptionHF);
            alEffectf(reverbEffect, AL_EAXREVERB_ROOM_ROLLOFF_FACTOR,    roomRolloff);
            alEffectf(reverbEffect, AL_EAXREVERB_DECAY_TIME,         decayTime);
            alEffectf(reverbEffect, AL_EAXREVERB_DECAY_HFRATIO,      decayHfRatio);
            alEffectf(reverbEffect, AL_EAXREVERB_DIFFUSION,          diffusion);
            alEffectf(reverbEffect, AL_EAXREVERB_GAINHF,             gainHF);
            alEffectf(reverbEffect, AL_EAXREVERB_REFLECTIONS_DELAY,  reflectionsDelay);
            alEffectf(reverbEffect, AL_EAXREVERB_LATE_REVERB_DELAY,  lateReverbDelay);
            alEffectf(reverbEffect, AL_EAXREVERB_REFLECTIONS_GAIN,   reflectionsGain);
            alEffectf(reverbEffect, AL_EAXREVERB_LATE_REVERB_GAIN,   lateReverbGain);
            if (outdoorLeakPercent < 0.95)
                AL11.alSource3i(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, auxFXSlot, 0, sendFilter);        } catch (Exception e) {
        }
    }

    // ================================================================================================================
    // Setup / teardown
    // ================================================================================================================

    public void initializeReverb() { // get default context
        if (efxInitialized) cleanupEFXResources();
        try {
            long currentContext = ALC10.alcGetCurrentContext();
            long device = ALC10.alcGetContextsDevice(currentContext);
            initializeReverb(currentContext, device);
        } catch (Exception e) {
            if(Config.getInstance().debug != DebugType.OFF)
                System.err.println("Failed to initialize reverb: " + e.getMessage());
        }
    }

    public void initializeReverb(long currentContext, long currentDevice) {
        if (efxInitialized) cleanupEFXResources(); // restart audio engine ig

        try {
            // Check if EFX is available
            if (!ALC10.alcIsExtensionPresent(currentDevice, "ALC_EXT_EFX")) {
                System.out.println("EFX Extension not available - reverb disabled");
                return;
            }

            // Create auxiliary effect slot
            auxFXSlot = EXTEfx.alGenAuxiliaryEffectSlots();
            EXTEfx.alAuxiliaryEffectSloti(auxFXSlot, EXTEfx.AL_EFFECTSLOT_AUXILIARY_SEND_AUTO, AL11.AL_TRUE);
            logALError("Failed creating aux");

            // Create reverb effect
            reverbEffect = EXTEfx.alGenEffects();
            EXTEfx.alEffecti(reverbEffect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB);
            logALError("Failed creating reverb effect");

            muffleFilter = EXTEfx.alGenFilters();
            EXTEfx.alFilteri(muffleFilter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            logALError("Failed creating Muffle Filter");

            // Create send filter
            sendFilter = EXTEfx.alGenFilters();
            EXTEfx.alFilteri(sendFilter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            logALError("Failed creating Send Filter");

            // Silences the dry path of the voice chat "own voice" echo source
            directBlockFilter = EXTEfx.alGenFilters();
            EXTEfx.alFilteri(directBlockFilter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            EXTEfx.alFilterf(directBlockFilter, EXTEfx.AL_LOWPASS_GAIN, 0.0f);
            EXTEfx.alFilterf(directBlockFilter, EXTEfx.AL_LOWPASS_GAINHF, 0.0f);

            // Set basic reverb parameters (medium room)
            setBasicReverbParams();

            // Attach effect to slot
            EXTEfx.alAuxiliaryEffectSloti(auxFXSlot, EXTEfx.AL_EFFECTSLOT_EFFECT, reverbEffect);
            logALError("Failed attaching reverb to slot");

            loadedParams = null;
            legacyParamsLoaded = true;
            efxInitialized = true;
            System.out.println("Reverb system initialized successfully");

        } catch (Exception e) {
//            System.err.println("Failed to initialize reverb: " + e.getMessage());
        }
    }

    /**
     * The parameters loaded at start-up. The legacy modes only ever changed the send level, so this is
     * the reverb they were always heard with.
     */
    private void setBasicReverbParams() {
        EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_EAXREVERB_DENSITY, 0.5f);
        EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_EAXREVERB_DIFFUSION, 0.8f);
        EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_EAXREVERB_GAIN, 0.3f);
        EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_EAXREVERB_GAINHF, 0.8f);
        EXTEfx.alEffectf(reverbEffect, AL_EAXREVERB_DECAY_TIME, 15f);
        EXTEfx.alEffectf(reverbEffect, AL_EAXREVERB_DECAY_HFRATIO, 0.7f);
        EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_EAXREVERB_REFLECTIONS_GAIN, 0.2f);
        EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_EAXREVERB_LATE_REVERB_GAIN, 0.4f);
        EXTEfx.alEffectf(reverbEffect, AL_EAXREVERB_LATE_REVERB_DELAY, 0.03f);
        EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_EAXREVERB_AIR_ABSORPTION_GAINHF, 0.99f);
        EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_EAXREVERB_ROOM_ROLLOFF_FACTOR, 0.0f);
    }

    public void cleanupEFXResources() {
        if (!efxInitialized) return;

        try {
            System.out.println("Cleaning up EFX resources...");

            // Delete OpenAL EFX objects if they exist
            if (auxFXSlot != 0) {
                EXTEfx.alDeleteAuxiliaryEffectSlots(auxFXSlot);
                auxFXSlot = 0;
            }

            if (reverbEffect != 0) {
                EXTEfx.alDeleteEffects(reverbEffect);
                reverbEffect = 0;
            }

            if (muffleFilter != 0) {
                EXTEfx.alDeleteFilters(muffleFilter);
                muffleFilter = 0;
            }

            if (sendFilter != 0) {
                EXTEfx.alDeleteFilters(sendFilter);
                sendFilter = 0;
            }

            if (directBlockFilter != 0) {
                EXTEfx.alDeleteFilters(directBlockFilter);
                directBlockFilter = 0;
            }

            efxInitialized = false;
            loadedParams = null;
            System.out.println("EFX resources cleaned up successfully");

        } catch (Exception e) {
            System.err.println("Error cleaning up EFX resources: " + e.getMessage());
            // Reset everything anyway to prevent issues
            auxFXSlot = 0;
            reverbEffect = 0;
            muffleFilter = 0;
            sendFilter = 0;
            efxInitialized = false;
            directBlockFilter = 0;
            loadedParams = null;
        }
    }

    /**
     * Muffle filter stuff
     **/
    public void applyMuffleToSource(int sourceId, float muffleStrength) {
        applyMuffleToSource(sourceId, muffleStrength, lerp(1.0f, 0.1f, clamp(muffleStrength, 0.0f, 1.0f)));
    }

    /**
     * @param muffleStrength 0 = clear, 1 = fully muffled (sets the overall filter gain)
     * @param hfRatio        treble that gets through relative to bass, from the materials in the way
     */
    public void applyMuffleToSource(int sourceId, float muffleStrength, float hfRatio) {
        if (!efxInitialized) return;
        try {
            // Clamp muffle strength between 0.0 (no muffling) and 1.0 (maximum muffling)
            muffleStrength = clamp(muffleStrength, 0.0f, 1.0f);

            // Calculate filter parameters based on muffle strength
            float lowpassGain = lerp(1.0f, 0.2f, muffleStrength);     // Overall volume reduction
            float lowpassGainHF = clamp(hfRatio, 0.01f, 1.0f);        // High frequency attenuation

            EXTEfx.alFilterf(muffleFilter, EXTEfx.AL_LOWPASS_GAIN, lowpassGain);
            EXTEfx.alFilterf(muffleFilter, EXTEfx.AL_LOWPASS_GAINHF, lowpassGainHF);
            AL11.alSourcei(sourceId, EXTEfx.AL_DIRECT_FILTER, muffleFilter);
        } catch (Exception e) {
            // Handle errors silently like the original function
        }
    }

    private static float clamp(float a, float b, float c) {
        return Math.min(Math.max(a,b),c);
    }

    public static void logALError(String errorMessage) {
        int error = AL11.alGetError();

        if (error == AL11.AL_NO_ERROR) {
            return;
        }

        String errorName = switch (error) {
            case AL11.AL_INVALID_NAME -> "AL_INVALID_NAME";
            case AL11.AL_INVALID_ENUM -> "AL_INVALID_ENUM";
            case AL11.AL_INVALID_VALUE -> "AL_INVALID_VALUE";
            case AL11.AL_INVALID_OPERATION -> "AL_INVALID_OPERATION";
            case AL11.AL_OUT_OF_MEMORY -> "AL_OUT_OF_MEMORY";
            default -> Integer.toString(error);
        };

        SoundPhysicsPerfected.DEBUG_LOGGER.error("{}: OpenAL error {}", errorMessage, errorName);
    }
}
