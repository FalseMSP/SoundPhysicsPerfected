package redsmods.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import redsmods.RedPositionedSoundInstance;
import redsmods.RedSoundInstance;
import redsmods.SoundData;
import java.util.HashSet;
import static redsmods.RaycastingHelper.soundQueue;

@Mixin(SoundSystem.class)
public class SoundSystemMixin {
    private static final float BLOCKED_VOLUME_MULTIPLIER = 5f; // bigger number = quieter, its a distance factor
    private static final double BOX_RADIUS = 1;

    private static final int MAX_SOUNDS = 100; // Limit queue size to prevent memory issues
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 5;
    private HashSet<RedSoundInstance> replayList = new HashSet<>();

    long currentContext = ALC10.alcGetCurrentContext();
    long currentDevice = ALC10.alcGetContextsDevice(currentContext);
    int reverb0 = EXTEfx.alGenEffects();

    private static int auxFXSlot = 0;
    private static int reverbEffect = 0;
    private static int sendFilter = 0;
    private static boolean efxInitialized = false;

    @Shadow
    private SoundManager loader;

    @Shadow @Final private SoundEngine soundEngine;

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void onSoundPlay(SoundInstance sound, CallbackInfo ci) {
//        if (!efxInitialized) {
//            initializeReverb();
//        }
//
//        if (!efxInitialized) return; // Skip if initialization failed
//
//        try {
//            // Get the OpenAL source ID for this sound
//            int sourceId = getSourceIdForSound(soundInstance);
//            if (sourceId != -1) {
//                applyReverbToSource(sourceId);
//            }
//        } catch (Exception e) {
//            // Silently continue if reverb application fails
//        }

        MinecraftClient client = MinecraftClient.getInstance();
        // Add null checks
        if (client == null || client.player == null || client.world == null || sound == null) {
            return;
        }

        try {
            if (!(sound instanceof RedPositionedSoundInstance) && sound.getAttenuationType() != SoundInstance.AttenuationType.NONE) { // !replayList.contains(redSoundData)
                // Get sound coordinates
                WeightedSoundSet weightedSoundSet = sound.getSoundSet(this.loader);
                double soundX = sound.getX();
                double soundY = sound.getY();
                double soundZ = sound.getZ();
                Vec3d soundPos = new Vec3d(soundX, soundY, soundZ);

                // Create rectangular bounding box with 0.6m radius
                Box boundingBox = new Box(
                        soundX - BOX_RADIUS, // minX
                        soundY - BOX_RADIUS, // minY
                        soundZ - BOX_RADIUS, // minZ
                        soundX + BOX_RADIUS, // maxX
                        soundY + BOX_RADIUS, // maxY
                        soundZ + BOX_RADIUS  // maxZ
                );

                // Get sound ID
                String soundId = sound.getId().toString();

                // Create sound data object
                RedSoundInstance redSoundData = new RedSoundInstance(sound);
                SoundData soundData = new SoundData(redSoundData, soundPos, boundingBox, soundId);

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
        if (paused) {
            return;
        }



        // You'll need to create another accessor for SoundSystem to access its playing sounds
        // This approach is different from the original because the structure changed
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
        EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_EAXREVERB_DECAY_TIME, 1.5f);
        EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_EAXREVERB_DECAY_HFRATIO, 0.7f);
        EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_EAXREVERB_REFLECTIONS_GAIN, 0.2f);
        EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_EAXREVERB_LATE_REVERB_GAIN, 0.4f);
        EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_EAXREVERB_LATE_REVERB_DELAY, 0.03f);
        EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_EAXREVERB_AIR_ABSORPTION_GAINHF, 0.99f);
        EXTEfx.alEffectf(reverbEffect, EXTEfx.AL_EAXREVERB_ROOM_ROLLOFF_FACTOR, 0.0f);
    }

    /**
     * Update all currently active sources with reverb
     * This is a brute-force approach that works when source tracking is difficult
     */
    private void updateActiveSources() {
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
            // Set reverb send gain (how much reverb to apply)
            float reverbGain = 0.6f; // Adjust this value (0.0 to 1.0)

            // Configure the send filter
            EXTEfx.alFilterf(sendFilter, EXTEfx.AL_LOWPASS_GAIN, reverbGain);
            EXTEfx.alFilterf(sendFilter, EXTEfx.AL_LOWPASS_GAINHF, 1.0f);

            // Connect source to reverb effect slot
            AL11.alSource3i(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, auxFXSlot, 0, sendFilter);

        } catch (Exception e) {
            // Ignore errors to prevent crashes
        }
    }
}
