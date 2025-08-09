package com.redsmods.sound_physics_perfected.mixin.client;

import com.redsmods.sound_physics_perfected.RaycastingHelper;
import com.redsmods.sound_physics_perfected.RedSoundInstance;
import com.redsmods.sound_physics_perfected.ReverbHelpers.EnhancedReverbData;
import com.redsmods.sound_physics_perfected.storageclasses.SoundData;
import com.redsmods.sound_physics_perfected.wrappers.RedPermeatedSoundInstance;
import com.redsmods.sound_physics_perfected.wrappers.RedPositionedSoundInstance;
import com.redsmods.sound_physics_perfected.wrappers.RedTickableInstance;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.*;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import static com.redsmods.sound_physics_perfected.RaycastingHelper.*;
import static org.joml.Math.lerp;
import static org.lwjgl.openal.EXTEfx.*;

@Mixin(SoundSystem.class)
public abstract class SoundSystemMixin {

    private static final int MAX_SOUNDS = 100; // Limit queue size to prevent memory issues

    private static int auxFXSlot = 0;
    private static int reverbEffect = 0;
    private static int muffleFilter = 0;
    private static int sendFilter = 0;
    private static boolean efxInitialized = false;
    private static final Map<Integer, RedTickableInstance> tickMap = new HashMap<>();

    @Shadow
    private SoundManager loader;
    @Shadow
    private Map<SoundInstance, Channel.SourceManager> sources;

//    @Shadow @Final private SoundEngine soundEngine;

    @Shadow public abstract void stop();

    @Shadow public abstract void tick(boolean paused);

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void onSoundPlay(SoundInstance sound, CallbackInfo ci) {
        if (!efxInitialized) {
            initializeReverb();
        }

        if (!efxInitialized) return; // Skip if initialization failed

        MinecraftClient client = MinecraftClient.getInstance();
        // Add null checks
        if (client == null || client.player == null || client.world == null || sound == null || this.loader == null) {
            return;
        }

        try {
            WeightedSoundSet weightedSoundSet = sound.getSoundSet(this.loader); // load pitches and whatnot into the sound data
            if (!(sound instanceof RedPositionedSoundInstance || sound instanceof TickableSoundInstance || sound instanceof RedPermeatedSoundInstance) && sound.getAttenuationType() != SoundInstance.AttenuationType.NONE) { // !replayList.contains(redSoundData)
                // Get sound coordinates
                double soundX = sound.getX();
                double soundY = sound.getY();
                double soundZ = sound.getZ();
                Vec3d soundPos = new Vec3d(soundX, soundY, soundZ);

                // Get sound ID
                String soundId = sound.getId().toString();

                // Create sound data object
                RedSoundInstance redSoundData = new RedSoundInstance(sound);
                SoundData soundData = new SoundData(redSoundData, soundPos, soundId);

                // Add to queue
                soundQueue.offer(soundData);

                // Remove the oldest sounds if queue is too large
                while (soundQueue.size() > MAX_SOUNDS) {
                    soundQueue.poll();
                }

                ci.cancel();
            } else if (ENABLE_PERMEATION && sound instanceof RedPermeatedSoundInstance) {
//                System.out.println(sound);
                FXQueue.add((RedPermeatedSoundInstance) sound);
            } else {
//                System.out.println(sound);
            }
        } catch (Exception e) {
            // Log error but don't crash
            System.err.println("Error tracking sound: " + e.getMessage());
        }
    }

    @Inject(
            method = "tick(Z)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/sound/SoundSystem;tick()V",
                    shift = At.Shift.AFTER),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void onSoundTick(boolean paused, CallbackInfo ci) {
        if (!efxInitialized) {
            initializeReverb();
        }

        if (!efxInitialized) return; // Skip if initialization failed

        if (paused) {
            return;
        }
        while(!FXQueue.isEmpty()) {
            try {
                RedPermeatedSoundInstance sound = FXQueue.poll();
                Channel.SourceManager manager = sources.get(sound);
                SourceManagerAccessor accessor = (SourceManagerAccessor) manager;
                Source source = accessor.getSource();
                int id = ((SourceAccessor) source).getPointer();
                sound.setSource(id);
                sound.applyMuffleToSource(id,sound.getPermeationIndex());
            } catch (Exception e) {
                System.out.println("sourceID is invalid for a sound, non-issue" + e);
            }
        }

        if (ENABLE_REVERB)
            updateActiveSources(); // Brute force reverb to ALL sounds
    }

    @ModifyVariable(method = "stop(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), argsOnly = true)
    private SoundInstance modifySoundParameter(SoundInstance sound) {
        if (!efxInitialized) return sound; // fx aren't init, most likely permeation isn't playing
        if(sound == null) return sound; // sorry, if some other mod kills their sound by using a mixin, i am not finna be held responsible, that's their own fault.
        soundQueue.remove(sound);
        SoundInstance customSound = soundInstanceMap.get(sound);
        RedPermeatedSoundInstance soundPermeation = soundPermInstanceMap.get(sound);
        if (soundPermeation != null)
            soundPermeation.setDone(true);
        soundInstanceMap.remove(sound);
        soundPermInstanceMap.remove(sound);

        // remove the permeation manually before using the default stop method
        Channel.SourceManager sourceManager = sources.get(soundPermeation);
        if (sourceManager != null) {
            sourceManager.run(Source::stop);
        }

        // remove custom sounds
        Channel.SourceManager sourceManagerNormal = sources.get(customSound);
        if (sourceManagerNormal != null) {
            sourceManagerNormal.run(Source::stop);
        }

        // Return the custom sound if it exists, otherwise return the original
        return sound; // if its null, welp. minecraft's code does handle if the custom sound ended up being ended properly so it should be fine, will fix null pointers hopefully though :)
    }

    // Add method to clean up orphaned sounds
    @Inject(method = "stopAll()V", at = @At("HEAD"))
    private void onStopAll(CallbackInfo ci) {
        soundQueue.clear();
    }

    private static void cleanupEFXResources() {
        if (!efxInitialized) return;

        try {
            System.out.println("Cleaning up EFX resources...");

            // Clear queues and maps
            FXQueue.clear();
            tickMap.clear();

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

            efxInitialized = false;
            System.out.println("EFX resources cleaned up successfully");

        } catch (Exception e) {
            System.err.println("Error cleaning up EFX resources: " + e.getMessage());
            // Reset everything anyway to prevent issues
            auxFXSlot = 0;
            reverbEffect = 0;
            muffleFilter = 0;
            sendFilter = 0;
            efxInitialized = false;
        }
    }


    /**
     * Initialize EFX reverb system once
     */
    private static void initializeReverb() {
        if (efxInitialized) return;

        try {
            // Get current OpenAL context
            long currentContext = ALC10.alcGetCurrentContext();
            long currentDevice = ALC10.alcGetContextsDevice(currentContext);

            // Check if EFX is available
            if (!ALC10.alcIsExtensionPresent(currentDevice, "ALC_EXT_EFX")) {
                System.out.println("EFX Extension not available - reverb disabled");
                return;
            }

            // Create auxiliary effect slot
            auxFXSlot = EXTEfx.alGenAuxiliaryEffectSlots();
            EXTEfx.alAuxiliaryEffectSloti(auxFXSlot, EXTEfx.AL_EFFECTSLOT_AUXILIARY_SEND_AUTO, AL11.AL_TRUE);

            // Create reverb effect
            reverbEffect = EXTEfx.alGenEffects();
            EXTEfx.alEffecti(reverbEffect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB);

            muffleFilter = EXTEfx.alGenFilters();
            EXTEfx.alFilteri(muffleFilter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            RedPermeatedSoundInstance.muffleFilter = muffleFilter;

            // Create send filter
            sendFilter = EXTEfx.alGenFilters();
            EXTEfx.alFilteri(sendFilter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);

            // Set basic reverb parameters (medium room)
            setBasicReverbParams();

            // Attach effect to slot
            EXTEfx.alAuxiliaryEffectSloti(auxFXSlot, EXTEfx.AL_EFFECTSLOT_EFFECT, reverbEffect);

            efxInitialized = true;
            System.out.println("Reverb system initialized successfully");

        } catch (Exception e) {
            System.err.println("Failed to initialize reverb: " + e.getMessage());
        }
    }

    /**
     * Set basic reverb parameters for a medium-sized room
     */
    private static void setBasicReverbParams() {
        // Basic medium room reverb settings
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

    /**
     * Update all currently active sources with reverb
     * This is a brute-force approach that works when source tracking is difficult
     */
    private static void updateActiveSources() {
        // Check if OpenAL context is available
        long context = ALC10.alcGetCurrentContext();
        if (context == 0) {
            return; // No context available
        }

        // Clear any existing errors
        AL10.alGetError();

        try {
            // Get all generated OpenAL sources and apply reverb
            // This requires keeping track of source IDs or iterating through all possible sources

            // Brute force approach - check source IDs 1-256 (typical range)
            for (int sourceId = 1; sourceId <= 256; sourceId++) {
                if (AL10.alIsSource(sourceId)) {
                    // Check if source is playing
                    int state = AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE);
                    if (state == AL10.AL_PLAYING || state == AL10.AL_PAUSED) {
                        applyReverbToSource(sourceId);
//                        System.out.println("Source ID: " + sourceId);
                    }
                }
            }
        } catch (Exception e) {
            // Ignore errors
        }
    }
    /**
     * Apply reverb settings to a specific OpenAL source
     */
    private static void applyReverbToSource(int sourceId) {
        try {
            // Get enhanced reverb data
            EnhancedReverbData reverbData = RaycastingHelper.getEnhancedReverbData();

            if (RaycastingHelper.getDistanceFromWallEchoDenom() == 0 ||
                    RaycastingHelper.getReverbDenom() == 0 ||
                    RaycastingHelper.getOutdoorLeakDenom() == 0) {
                return;
            }

            // Raw data extraction
            float wallDistance = (float) (RaycastingHelper.getDistanceFromWallEcho() / RaycastingHelper.getDistanceFromWallEchoDenom());
            float reverbStrength = (float) RaycastingHelper.getReverbStrength() / RaycastingHelper.getReverbDenom();
            float weightedReverbStrength = (float) RaycastingHelper.getWeightedReverbStrength() / RaycastingHelper.getReverbDenom();
            float outdoorLeakPercent = (float) RaycastingHelper.getOutdoorLeak() / RaycastingHelper.getOutdoorLeakDenom();
            float earlyReflectionRatio = (float) RaycastingHelper.getEarlyReflectionRatio();

            // Enhanced reverb parameters
            float rt60 = (float) reverbData.rt60;
            float roomSize = (float) reverbData.roomSize;
            float absorption = (float) reverbData.absorption;
            float earlyReflectionDelay = (float) reverbData.earlyReflectionDelay;
            float lateReflectionStrength = (float) reverbData.lateReflectionStrength;
            boolean isIndoors = reverbData.isIndoors;

            // Normalize and clamp values
            wallDistance = clamp(wallDistance, 0.5f, 200.0f);
            reverbStrength = clamp(reverbStrength, 0.0f, 1.0f);
            weightedReverbStrength = clamp(weightedReverbStrength, 0.0f, 1.0f);
            outdoorLeakPercent = clamp(outdoorLeakPercent, 0.0f, 1.0f);
            earlyReflectionRatio = clamp(earlyReflectionRatio, 0.0f, 1.0f);
            rt60 = clamp(rt60, 0.1f, 10.0f);
            roomSize = clamp(roomSize, 1.0f, 100.0f);
            absorption = clamp(absorption, 0.01f, 0.9f);
            earlyReflectionDelay = clamp(earlyReflectionDelay, 0.001f, 0.1f);
            lateReflectionStrength = clamp(lateReflectionStrength, 0.0f, 1.0f);

            // Calculate environmental factors
            float enclosureFactor = isIndoors ? (1.0f - outdoorLeakPercent) :
                    Math.max(0.0f, (reverbStrength - outdoorLeakPercent));
            enclosureFactor = clamp(enclosureFactor, 0.0f, 1.0f);

            float opennessFactor = 1.0f - enclosureFactor;

            // Size-based calculations (dynamic room volume estimation)
            float estimatedVolume = roomSize * roomSize * roomSize * 0.7f; // More realistic volume estimation
            float estimatedSurfaceArea = 6.0f * roomSize * roomSize;
            float surfaceToVolumeRatio = estimatedSurfaceArea / Math.max(estimatedVolume, 1.0f);
            surfaceToVolumeRatio = clamp(surfaceToVolumeRatio, 0.1f, 5.0f);

            // Material-based absorption (dynamic based on measured absorption)
            float dynamicAbsorption = absorption;
            float totalAbsorption = estimatedSurfaceArea * dynamicAbsorption;

            // RT60 with fallback calculation if enhanced data is unreliable
            float calculatedRT60 = rt60;
            if (calculatedRT60 < 0.1f || calculatedRT60 > 10.0f) {
                calculatedRT60 = 0.161f * estimatedVolume / Math.max(totalAbsorption, 0.1f);
                calculatedRT60 = clamp(calculatedRT60, 0.1f, 8.0f);
            }

            // Apply enclosure effect to decay time
            float effectiveDecayTime = calculatedRT60 * enclosureFactor;
            effectiveDecayTime = clamp(effectiveDecayTime, 0.1f, 8.0f);

            // Distance-based attenuation (more sophisticated)
            float distanceAttenuation = 1.0f / (1.0f + wallDistance * 0.02f + wallDistance * wallDistance * 0.0001f);

            // Air absorption based on distance and humidity (simplified)
            float airAbsorptionFactor = 1.0f - (wallDistance * 0.003f);
            airAbsorptionFactor = clamp(airAbsorptionFactor, 0.2f, 1.0f);

            // High frequency decay ratio (material and air absorption dependent)
            float decayHfRatio = (1.0f - dynamicAbsorption) * airAbsorptionFactor * enclosureFactor;
            decayHfRatio = clamp(decayHfRatio, 0.1f, 2.0f);

            // Early reflections timing and gain
            float reflectionsDelay = earlyReflectionDelay;
            float reflectionsGain = earlyReflectionRatio * reverbStrength * enclosureFactor * distanceAttenuation;
            reflectionsGain = clamp(reflectionsGain, 0.0f, 0.8f);

            // Late reverb timing and gain
            float lateReverbDelay = earlyReflectionDelay * 2.0f + (roomSize / 343.0f);
            lateReverbDelay = clamp(lateReverbDelay, 0.005f, 0.1f);

            float lateReverbGain = lateReflectionStrength * enclosureFactor * distanceAttenuation;
            lateReverbGain = clamp(lateReverbGain, 0.0f, 1.0f);

            // Diffusion based on room shape complexity and size
            float roomComplexity = Math.min(1.0f, surfaceToVolumeRatio / 2.0f); // Higher S/V ratio = more complex
            float diffusion = lerp(0.3f, 0.95f, roomComplexity * enclosureFactor * weightedReverbStrength);
            diffusion = clamp(diffusion, 0.1f, 1.0f);

            // Density based on reflection pattern
            float density = lerp(0.3f, 1.0f, enclosureFactor * (1.0f - roomSize / 100.0f));
            density = clamp(density, 0.1f, 1.0f);

            // Overall gain with realistic falloff
            float overallGain = enclosureFactor * distanceAttenuation * (0.1f + reverbStrength * 0.4f);
            overallGain = clamp(overallGain, 0.0f, 0.5f);

            // High frequency gain (affected by air absorption and materials)
            float gainHF = (1.0f - dynamicAbsorption) * airAbsorptionFactor * enclosureFactor;
            gainHF = clamp(gainHF, 0.1f, 1.0f);

            // Air absorption high frequency
            float airAbsorptionHF = airAbsorptionFactor * enclosureFactor + opennessFactor * 0.7f;
            airAbsorptionHF = clamp(airAbsorptionHF, 0.892f, 1.0f);

            // Room rolloff factor (how quickly sound drops off with distance in the room)
            float roomRolloff = lerp(1.0f, 0.0f, enclosureFactor) * (roomSize / 50.0f);
            roomRolloff = clamp(roomRolloff, 0.0f, 1.0f);

            // Low-pass filtering for send (occlusion/obstruction effects)
            float sendFilterGain = overallGain;
            float sendFilterGainHF = gainHF * 0.7f; // Additional HF cut for realism

            // Echo density (packed reflections in small rooms, sparse in large rooms)
            float echoDensity = lerp(1.0f, 0.4f, roomSize / 100.0f) * enclosureFactor;
            echoDensity = clamp(echoDensity, 0.1f, 1.0f);

            // Modulation for more natural sound (subtle)
            float modulationTime = clamp(roomSize * 0.01f, 0.04f, 4.0f);
            float modulationDepth = clamp(enclosureFactor * 0.1f, 0.0f, 1.0f);

            // Apply all parameters to OpenAL
            EXTEfx.alFilterf(sendFilter, EXTEfx.AL_LOWPASS_GAIN, sendFilterGain);
            EXTEfx.alFilterf(sendFilter, EXTEfx.AL_LOWPASS_GAINHF, sendFilterGainHF);

            alEffectf(reverbEffect, AL_EAXREVERB_DENSITY, density);
            alEffectf(reverbEffect, AL_EAXREVERB_DIFFUSION, diffusion);
            alEffectf(reverbEffect, AL_EAXREVERB_GAIN, overallGain);
            alEffectf(reverbEffect, AL_EAXREVERB_GAINHF, gainHF);
            alEffectf(reverbEffect, AL_EAXREVERB_GAINLF, clamp(1.0f - dynamicAbsorption * 0.5f, 0.1f, 1.0f));
            alEffectf(reverbEffect, AL_EAXREVERB_DECAY_TIME, effectiveDecayTime);
            alEffectf(reverbEffect, AL_EAXREVERB_DECAY_HFRATIO, decayHfRatio);
            alEffectf(reverbEffect, AL_EAXREVERB_DECAY_LFRATIO, clamp(decayHfRatio * 1.2f, 0.1f, 2.0f));
            alEffectf(reverbEffect, AL_EAXREVERB_REFLECTIONS_GAIN, reflectionsGain);
            alEffectf(reverbEffect, AL_EAXREVERB_REFLECTIONS_DELAY, reflectionsDelay);
            alEffectf(reverbEffect, AL_EAXREVERB_LATE_REVERB_GAIN, lateReverbGain);
            alEffectf(reverbEffect, AL_EAXREVERB_LATE_REVERB_DELAY, lateReverbDelay);
            alEffectf(reverbEffect, AL_EAXREVERB_AIR_ABSORPTION_GAINHF, airAbsorptionHF);
            alEffectf(reverbEffect, AL_EAXREVERB_ROOM_ROLLOFF_FACTOR, roomRolloff);
            alEffectf(reverbEffect, AL_EAXREVERB_ECHO_TIME, clamp(roomSize * 0.002f, 0.075f, 0.25f));
            alEffectf(reverbEffect, AL_EAXREVERB_ECHO_DEPTH, clamp(echoDensity * 0.1f, 0.0f, 1.0f));
            alEffectf(reverbEffect, AL_EAXREVERB_MODULATION_TIME, modulationTime);
            alEffectf(reverbEffect, AL_EAXREVERB_MODULATION_DEPTH, modulationDepth);
            alEffectf(reverbEffect, AL_EAXREVERB_HFREFERENCE, clamp(5000.0f - roomSize * 20.0f, 1000.0f, 20000.0f));
            alEffectf(reverbEffect, AL_EAXREVERB_LFREFERENCE, clamp(250.0f - roomSize * 2.0f, 20.0f, 1000.0f));

            AL11.alSource3i(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, auxFXSlot, 0, sendFilter);

        } catch (Exception e) {
            System.err.println("Error applying dynamic reverb: " + e.getMessage());
        }
    }

    private static void debugSourceCount() {
        int sourcesInUse = 0;
        for (int i = 1; i < 1000; i++) { // Check first 1000 IDs
            if (AL10.alIsSource(i)) {
                sourcesInUse++;
            }
        }
        System.out.println("Sources currently in use: " + sourcesInUse);
    }

    @Inject(method = "stop()V", at = @At("HEAD"))
    private void onAudioEngineStop(CallbackInfo ci) {
        cleanupEFXResources();
    }

    @Inject(method = "start()V", at = @At("TAIL"))
    private void onAudioEngineStart(CallbackInfo ci) {
        efxInitialized = false;
        initializeReverb();
    }

    private static float clamp(float a, float b, float c) {
        return Math.min(Math.max(a,b),c);
    }
}