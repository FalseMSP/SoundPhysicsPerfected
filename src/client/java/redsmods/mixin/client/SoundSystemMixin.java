package redsmods.mixin.client;

import net.fabricmc.fabric.mixin.client.rendering.AtlasSourceManagerAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import redsmods.RaycastingHelper;
import redsmods.RedPositionedSoundInstance;
import redsmods.RedSoundInstance;
import redsmods.SoundData;

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import static java.lang.Math.clamp;
import static org.joml.Math.lerp;
import static org.lwjgl.openal.EXTEfx.*;
import static redsmods.RaycastingHelper.soundQueue;

@Mixin(SoundSystem.class)
public class SoundSystemMixin {

    private static final int MAX_SOUNDS = 100; // Limit queue size to prevent memory issues

    int reverb0 = EXTEfx.alGenEffects();

    private static int auxFXSlot = 0;
    private static int reverbEffect = 0;
    private static int sendFilter = 0;
    private static boolean efxInitialized = false;

    private static final Queue<SoundInstance> FXQueue = new LinkedList<>();

    @Shadow
    private SoundManager loader;
    @Shadow
    private Map<SoundInstance, Channel.SourceManager> sources;

//    @Shadow @Final private SoundEngine soundEngine;

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void onSoundPlay(SoundInstance sound, CallbackInfo ci) {
        if (!efxInitialized) {
            initializeReverb();
        }

        if (!efxInitialized) return; // Skip if initialization failed

        MinecraftClient client = MinecraftClient.getInstance();
        // Add null checks
        if (client == null || client.player == null || client.world == null || sound == null) {
            return;
        }

        try {
            WeightedSoundSet weightedSoundSet = sound.getSoundSet(this.loader); // load pitches and whatnot into the sound data
            if (!(sound instanceof RedPositionedSoundInstance) && sound.getAttenuationType() != SoundInstance.AttenuationType.NONE && !sound.isRepeatable()) { // !replayList.contains(redSoundData)
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

                // Remove oldest sounds if queue is too large
                while (soundQueue.size() > MAX_SOUNDS) {
                    soundQueue.poll();
                }

                // Get player position for debug message
                Vec3d playerPos = client.player.getEyePos();
                String coordinates = String.format("(%.2f, %.2f, %.2f)", soundX, soundY, soundZ);
                double distance = playerPos.distanceTo(soundPos);
                String distanceStr = String.format("%.2fm", distance);
                String message = String.format("Sound: %s at %s [%s] - Queued (%d total)",
                        soundId, coordinates, distanceStr, soundQueue.size());
                client.player.sendMessage(Text.literal(message), true);
                ci.cancel();
            } else if (sound instanceof RedPositionedSoundInstance) {
                // do something to post-proc sounds ig maybe if u want :p
                FXQueue.add(sound);
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
            SoundInstance sound = FXQueue.poll();
            Channel.SourceManager manager = sources.get(sound);
            SourceManagerAccessor accessor = (SourceManagerAccessor) manager;
            Source source = accessor.getSource();
            int id = ((SourceAccessor) source).getPointer();
            System.out.println("Accessor Source: " + id);
        }

        updateActiveSources(); // Brute force reverb to ALL sounds
    }

    @Inject(method = "stop(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("TAIL"))
    private void onSoundStop(SoundInstance sound, CallbackInfo ci) {
        soundQueue.remove(sound);
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
                        System.out.println("Source ID: " + sourceId);
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
            float wallDistance = (float) (RaycastingHelper.distanceFromWallEcho / RaycastingHelper.distanceFromWallEchoDenom);
            float occlusionPercent = (float) RaycastingHelper.reverbStrength / RaycastingHelper.reverbDenom;
            float outdoorLeakPercent = (float) RaycastingHelper.outdoorLeak / RaycastingHelper.outdoorLeakDenom;

            System.out.println(occlusionPercent +" " + wallDistance + " " + outdoorLeakPercent);

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
}