package redsmods.mixin.client;

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
import redsmods.*;
import redsmods.storageclasses.SoundData;
import redsmods.wrappers.RedPermeatedSoundInstance;
import redsmods.wrappers.RedPositionedSoundInstance;
import redsmods.wrappers.RedTickableInstance;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import static java.lang.Math.clamp;
import static org.joml.Math.lerp;
import static org.lwjgl.openal.EXTEfx.*;
import static redsmods.RaycastingHelper.*;

@Mixin(SoundSystem.class)
public abstract class SoundSystemMixin {

    private static final int MAX_SOUNDS = 100; // Limit queue size to prevent memory issues

    private static int auxFXSlot = 0;
    private static int reverbEffect = 0;
    private static int muffleFilter = 0;
    private static int sendFilter = 0;
    private static boolean efxInitialized = false;

    private static final Queue<SoundInstance> FXQueue = new LinkedList<>();
    private static final Queue<RedTickableInstance> FXTickQueue = new LinkedList<>();
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
            if (!(sound instanceof RedPositionedSoundInstance || sound instanceof RedTickableInstance || sound instanceof RedPermeatedSoundInstance) && sound.getAttenuationType() != SoundInstance.AttenuationType.NONE) { // !replayList.contains(redSoundData)
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
                FXQueue.add(sound);
            } else if (TICK_RATE == 0 && sound instanceof RedTickableInstance) {
                FXTickQueue.add((RedTickableInstance) sound);
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
                SoundInstance sound = FXQueue.poll();
                Channel.SourceManager manager = sources.get(sound);
                SourceManagerAccessor accessor = (SourceManagerAccessor) manager;
                Source source = accessor.getSource();
                int id = ((SourceAccessor) source).getPointer();
                applyMuffleToSource(id,1f);
                //            System.out.println("Accessor Source: " + id);
            } catch (Exception e) {
                System.out.println("sourceID is invalid for a sound, non-issue");
            }
        }

        while(!FXTickQueue.isEmpty()) {
            RedTickableInstance sound = FXTickQueue.poll();
            try {
                Channel.SourceManager manager = sources.get(sound);
                SourceManagerAccessor accessor = (SourceManagerAccessor) manager;
                Source source = accessor.getSource();
                int id = ((SourceAccessor) source).getPointer();
                tickMap.put(id,sound);
            } catch (Exception e) {
                if (tickMap.containsValue(sound))
                    System.out.println("Critical error (mem leak prolly)");
                System.out.println("sourceID is invalid for a sound, non-issue");
            }
        }

        if (ENABLE_REVERB)
            updateActiveSources(); // Brute force reverb to ALL sounds
    }

    @ModifyVariable(method = "stop(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), argsOnly = true)
    private SoundInstance modifySoundParameter(SoundInstance sound) {
        if (!efxInitialized) return sound; // fx aren't init, most likely permeation isn't playing
        System.out.println("Stop called for: " + sound.getSound().getLocation());
        soundQueue.remove(sound);
        SoundInstance customSound = soundInstanceMap.get(sound);
        SoundInstance soundPermeation = soundPermInstanceMap.get(sound);
        soundInstanceMap.remove(sound);
        soundPermInstanceMap.remove(sound);

        // remove the permeation manually before using the default stop method
        Channel.SourceManager sourceManager = sources.get(soundPermeation);
        if (sourceManager != null) {
            sourceManager.run(Source::stop);
        }

        // Return the custom sound if it exists, otherwise return the original
        return customSound != null ? customSound : sound;
    }

    // Add method to clean up orphaned sounds
    @Inject(method = "stopAll()V", at = @At("HEAD"))
    private void onStopAll(CallbackInfo ci) {
        soundQueue.clear();
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
                } else if (tickMap.containsKey(sourceId)){
                    tickMap.get(sourceId).stop();
                    tickMap.remove(sourceId);
                    System.out.println(tickMap);
                }
//                System.out.println(tickMap);
//                System.out.println(tickQueue.peek().getId());
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
            if (distanceFromWallEchoDenom == 0 || reverbDenom == 0 || outdoorLeakDenom == 0)
                return;

            float wallDistance = (float) (RaycastingHelper.distanceFromWallEcho / RaycastingHelper.distanceFromWallEchoDenom);
            float occlusionPercent = (float) RaycastingHelper.reverbStrength / RaycastingHelper.reverbDenom;
            float outdoorLeakPercent = (float) RaycastingHelper.outdoorLeak / RaycastingHelper.outdoorLeakDenom;

//            System.out.println("REVERB DEBUG: " + occlusionPercent +" " + wallDistance + " " + outdoorLeakPercent);

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
            if (outdoorLeakPercent < 0.95)
                AL11.alSource3i(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, auxFXSlot, 0, sendFilter);        } catch (Exception e) {
        }
    }

    private static void applyMuffleToSource(int sourceId, float muffleStrength) {
        try {
            // Clamp muffle strength between 0.0 (no muffling) and 1.0 (maximum muffling)
            muffleStrength = clamp(muffleStrength, 0.0f, 1.0f);

            // Calculate filter parameters based on muffle strength
            float lowpassGain = lerp(1.0f, 0.2f, muffleStrength);     // Overall volume reduction
            float lowpassGainHF = lerp(1.0f, 0.1f, muffleStrength);   // High frequency attenuation

            // Apply low-pass filter (main muffling effect)
            if (muffleFilter != -1) {
                EXTEfx.alFilterf(muffleFilter, EXTEfx.AL_LOWPASS_GAIN, lowpassGain);
                EXTEfx.alFilterf(muffleFilter, EXTEfx.AL_LOWPASS_GAINHF, lowpassGainHF);
                AL11.alSourcei(sourceId, EXTEfx.AL_DIRECT_FILTER, muffleFilter);
            }

        } catch (Exception e) {
            // Handle errors silently like the original function
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
}