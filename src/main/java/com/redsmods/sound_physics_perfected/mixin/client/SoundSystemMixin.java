package com.redsmods.sound_physics_perfected.mixin.client;

import com.mojang.blaze3d.audio.Channel;
import com.redsmods.sound_physics_perfected.RaycastingHelper;
import com.redsmods.sound_physics_perfected.RedSoundInstance;
import com.redsmods.sound_physics_perfected.ReverbHelpers.EnhancedReverbData;
import com.redsmods.sound_physics_perfected.ReverbHelpers.ReverbConstants;
import com.redsmods.sound_physics_perfected.config.Config;
import com.redsmods.sound_physics_perfected.config.DebugType;
import com.redsmods.sound_physics_perfected.storageclasses.SoundData;
import com.redsmods.sound_physics_perfected.wrappers.RedPermeatedSoundInstance;
import com.redsmods.sound_physics_perfected.wrappers.RedPositionedSoundInstance;
import com.redsmods.sound_physics_perfected.wrappers.RedTickableInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.redsmods.sound_physics_perfected.RaycastingHelper.*;
import static org.joml.Math.lerp;
import static org.lwjgl.openal.EXTEfx.*;

@Mixin(SoundEngine.class)
public abstract class SoundSystemMixin {

    private static final int MAX_SOUNDS = 100; // Limit queue size to prevent memory issues

    private static int auxFXSlot = 0;
    private static int reverbEffect = 0;
    private static int muffleFilter = 0;
    private static int sendFilter = 0;
    private static boolean efxInitialized = false;
    private static final Map<Integer, RedTickableInstance> tickMap = new HashMap<>();


    @Shadow
    private SoundManager soundManager;
    @Shadow
    private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

    @Shadow public abstract void destroy();

    @Shadow public abstract void tick(boolean paused);

    //? if <1.21.6 {
    /*@Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void onSoundPlay(SoundInstance sound, CallbackInfo ci) {
    *///?} else {
    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;", at = @At("HEAD"), cancellable = true)
    private void onSoundPlay(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
     //?}
        if (!efxInitialized) {
            initializeReverb();
        }
//        Minecraft client = Minecraft.getInstance();
//        if (sound instanceof TickableSoundInstance) {
//            client.particleEngine.createParticle(ParticleTypes.NOTE,
//                    sound.getX(), sound.getY(), sound.getZ(), 0.0D, 0.0D, 0.0D);
//            System.out.println("modified sound location" + sound.getX() + " " + sound.getY() + " " + sound.getZ());
//        } else {
//            client.particleEngine.createParticle(ParticleTypes.ANGRY_VILLAGER,
//                    sound.getX(), sound.getY(), sound.getZ(), 0.0D, 0.0D, 0.0D);
//            System.out.println("original sound location" + sound.getX() + " " + sound.getY() + " " + sound.getZ());
//        }
        if (isSoundBlacklisted(sound.toString())) return; // skip if in the overall blacklist

        if (!efxInitialized) return; // Skip if initialization failed

        Minecraft client = Minecraft.getInstance();
        // Add null checks
        if (client == null || client.player == null || client.level == null || sound == null || soundManager == null) {
            return;
        }

        try {
            WeighedSoundEvents weightedSoundSet = sound.resolve(soundManager); // load pitches and whatnot into the sound data
            if (!(sound instanceof RedPositionedSoundInstance || sound instanceof TickableSoundInstance || sound instanceof RedPermeatedSoundInstance) && sound.getAttenuation() != SoundInstance.Attenuation.NONE) { // !replayList.contains(redSoundData)
                // Get sound coordinates
                double soundX = sound.getX();
                double soundY = sound.getY();
                double soundZ = sound.getZ();
                Vec3 soundPos = new Vec3(soundX, soundY, soundZ);

                // Get sound ID
                String soundId = sound.getLocation().toString();

                // Create sound data object
                RedSoundInstance redSoundData = new RedSoundInstance(sound);
                SoundData soundData = new SoundData(redSoundData, soundPos, soundId);

                // Add to queue
                soundQueue.offer(soundData);

                // Remove the oldest sounds if queue is too large
                while (soundQueue.size() > MAX_SOUNDS) {
                    soundQueue.poll();
                }

                /*? if >= 1.21.6 {*/ cir.cancel(); /*?} else {*/ /*ci.cancel(); *//*?}*/
            } else if (Config.getInstance().permeation && sound instanceof RedPermeatedSoundInstance) {
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
                    //? if >=1.21.6
                    target = "Lnet/minecraft/client/sounds/SoundEngine;tickInGameSound()V",
                    //? if <1.21.6
                    /*target = "Lnet/minecraft/client/sounds/SoundEngine;tickNonPaused()V",*/
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
                ChannelAccess.ChannelHandle manager = instanceToChannel.get(sound);
                SourceManagerAccessor accessor = (SourceManagerAccessor) manager;
                Channel source = accessor.getChannel();
                int id = ((SourceAccessor) source).getSource();
                sound.setSource(id);
                sound.applyMuffleToSource(id,sound.getPermeationIndex());
            } catch (Exception e) {
                if (Config.getInstance().debug != DebugType.OFF)
                    System.out.println("sourceID is invalid for a sound, non-issue" + e);
            }
        }

        if (Config.getInstance().reverb)
            updateActiveSources(); // Brute force reverb to ALL sounds
    }

    @ModifyVariable(method = "stop(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"), argsOnly = true)
    private SoundInstance modifySoundParameter(SoundInstance sound) {
        if (!efxInitialized) return sound; // fx aren't init, most likely permeation isn't playing
        if(sound == null) return sound; // sorry, if some other mod kills their sound by using a mixin, i am not finna be held responsible, that's their own fault.
        soundQueue.remove(sound);
        SoundInstance customSound = soundInstanceMap.get(sound);
        RedPermeatedSoundInstance soundPermeation = soundPermInstanceMap.get(sound);
        if (soundPermeation != null)
            soundPermeation.setStopped(true);
        soundInstanceMap.remove(sound);
        soundPermInstanceMap.remove(sound);

        // remove the permeation manually before using the default stop method
        ChannelAccess.ChannelHandle sourceManager = instanceToChannel.get(soundPermeation);
        if (sourceManager != null) {
            sourceManager.execute(Channel::stop);
        }

        // remove custom sounds
        ChannelAccess.ChannelHandle sourceManagerNormal = instanceToChannel.get(customSound);
        if (sourceManagerNormal != null) {
            sourceManagerNormal.execute(Channel::stop);
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
                        if (Config.getInstance().legacyReverb)
                            applyLegacyReverbToSource(sourceId);
                        else
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
            if (Config.getInstance().reverbTuning == DebugType.CHAT) {
                Minecraft client = Minecraft.getInstance();
                client.player.displayClientMessage(Component.literal(reverbData.toString()), false);
            } else if (Config.getInstance().reverbTuning == DebugType.ACTION_BAR) {
                Minecraft client = Minecraft.getInstance();
                client.player.displayClientMessage(Component.literal(reverbData.toString()), true);
            }

            if (RaycastingHelper.getDistanceFromWallEchoDenom() == 0 ||
                    RaycastingHelper.getReverbDenom() == 0 ||
                    RaycastingHelper.getOutdoorLeakDenom() == 0) {
                return;
            }

            float wallDistance = (float) (RaycastingHelper.getDistanceFromWallEcho() / RaycastingHelper.getDistanceFromWallEchoDenom());
            float reverbStrength = (float) RaycastingHelper.getReverbStrength() / RaycastingHelper.getReverbDenom();
            float weightedReverbStrength = (float) RaycastingHelper.getWeightedReverbStrength() / RaycastingHelper.getReverbDenom();
            float outdoorLeakPercent = (float) RaycastingHelper.getOutdoorLeak() / RaycastingHelper.getOutdoorLeakDenom();
            float earlyReflectionRatio = (float) RaycastingHelper.getEarlyReflectionRatio();

            float rt60 = (float) reverbData.rt60;
            float roomSize = (float) reverbData.roomSize;
            float absorption = (float) reverbData.absorption;
            float earlyReflectionDelay = (float) reverbData.earlyReflectionDelay;
            float lateReflectionStrength = (float) reverbData.lateReflectionStrength;
            boolean isIndoors = reverbData.isIndoors;

            wallDistance = clamp(wallDistance, ReverbConstants.MIN_WALL_DISTANCE, ReverbConstants.MAX_WALL_DISTANCE);
            reverbStrength = clamp(reverbStrength, 0.0f, 1.0f);
            weightedReverbStrength = clamp(weightedReverbStrength, 0.0f, 1.0f);
            outdoorLeakPercent = clamp(outdoorLeakPercent, 0.0f, 1.0f);
            earlyReflectionRatio = clamp(earlyReflectionRatio, 0.0f, 1.0f);
//            rt60 = clamp(rt60, ReverbConstants.MIN_RT60, ReverbConstants.MAX_RT60);
            roomSize = clamp(roomSize, ReverbConstants.MIN_ROOM_SIZE, ReverbConstants.MAX_ROOM_SIZE);
            absorption = clamp(absorption, ReverbConstants.MIN_ABSORPTION, ReverbConstants.MAX_ABSORPTION);
            earlyReflectionDelay = clamp(earlyReflectionDelay, ReverbConstants.MIN_EARLY_REFLECTION_DELAY, ReverbConstants.MAX_EARLY_REFLECTION_DELAY);
            lateReflectionStrength = clamp(lateReflectionStrength, 0.0f, 1.0f);

            float enclosureFactor = isIndoors ? (1.0f - outdoorLeakPercent) * Config.getInstance().indoorBias :
                    Math.max(0.0f, (reverbStrength - outdoorLeakPercent)) * Config.getInstance().outdoorBias;
            enclosureFactor = clamp(enclosureFactor, 0.0f, 1.0f);

            float opennessFactor = 1.0f - enclosureFactor;

            float estimatedVolume = roomSize * roomSize * roomSize * Config.getInstance().roomVolumeMultiplier;
            float estimatedSurfaceArea = Config.getInstance().surfaceAreaMultiplier * roomSize * roomSize;
            float surfaceToVolumeRatio = estimatedSurfaceArea / Math.max(estimatedVolume, 1.0f);
            surfaceToVolumeRatio = clamp(surfaceToVolumeRatio, ReverbConstants.MIN_SURFACE_TO_VOLUME_RATIO, ReverbConstants.MAX_SURFACE_TO_VOLUME_RATIO);

// Material-based absorption (WIP)
            float dynamicAbsorption = absorption;
            float totalAbsorption = estimatedSurfaceArea * dynamicAbsorption;

            float calculatedRT60 = rt60;
            if (calculatedRT60 < ReverbConstants.MIN_RT60 || calculatedRT60 > ReverbConstants.MAX_RT60) {
                calculatedRT60 = Config.getInstance().rt60SabineConstant * estimatedVolume / Math.max(totalAbsorption, Config.getInstance().minTotalAbsorption);
                calculatedRT60 = clamp(calculatedRT60, ReverbConstants.MIN_CALCULATED_RT60, ReverbConstants.MAX_CALCULATED_RT60);
            }

            float effectiveDecayTime = calculatedRT60 * enclosureFactor;
            effectiveDecayTime = clamp(effectiveDecayTime, ReverbConstants.MIN_EFFECTIVE_DECAY_TIME, ReverbConstants.MAX_EFFECTIVE_DECAY_TIME);

            float distanceAttenuation = 1.0f / (1.0f + wallDistance * Config.getInstance().distanceAttenuationLinear +
                    wallDistance * wallDistance * Config.getInstance().distanceAttenuationQuadratic);

            float airAbsorptionFactor = 1.0f - (wallDistance * Config.getInstance().airAbsorptionRate);
            airAbsorptionFactor = clamp(airAbsorptionFactor, Config.getInstance().minAirAbsorption, 1.0f);

            float decayHfRatio = (1.0f - dynamicAbsorption) * airAbsorptionFactor * enclosureFactor;
            decayHfRatio = clamp(decayHfRatio, ReverbConstants.MIN_DECAY_HF_RATIO, ReverbConstants.MAX_DECAY_HF_RATIO);

            float reflectionsDelay = earlyReflectionDelay;
            float reflectionsGain = earlyReflectionRatio * reverbStrength * enclosureFactor * distanceAttenuation * Config.getInstance().globalReverbIntensity;
            reflectionsGain = clamp(reflectionsGain, ReverbConstants.MIN_REFLECTIONS_GAIN, ReverbConstants.MAX_REFLECTIONS_GAIN);

            float lateReverbDelay = earlyReflectionDelay * Config.getInstance().lateReverbDelayMultiplier + (roomSize / Config.getInstance().soundSpeed);
            lateReverbDelay = clamp(lateReverbDelay, ReverbConstants.MIN_LATE_REVERB_DELAY, ReverbConstants.MAX_LATE_REVERB_DELAY);

            float lateReverbGain = lateReflectionStrength * enclosureFactor * distanceAttenuation * Config.getInstance().globalReverbIntensity;
            lateReverbGain = clamp(lateReverbGain, ReverbConstants.MIN_LATE_REVERB_GAIN, ReverbConstants.MAX_LATE_REVERB_GAIN);

            float roomComplexity = Math.min(1.0f, surfaceToVolumeRatio / Config.getInstance().roomComplexityDivisor);

// NEW: Room size scaling factors
            float roomSizeScaling = 1.0f;
            float smallRoomReduction = 1.0f;
            float volumeBasedReduction = 1.0f;

            if (roomSize < 0.5f) {
                // Tiny rooms (closets, small bathrooms): dramatic reduction
                roomSizeScaling = 0.05f;
                smallRoomReduction = 0.1f;
                volumeBasedReduction = 0.1f;
            } else if (roomSize < 2.0f) {
                // Very small rooms: significant reduction
                roomSizeScaling = 0.15f;
                smallRoomReduction = 0.25f;
                volumeBasedReduction = 0.3f;
            } else if (roomSize < 5.0f) {
                // Small rooms: moderate reduction
                roomSizeScaling = 0.4f;
                smallRoomReduction = 0.5f;
                volumeBasedReduction = 0.6f;
            } else if (roomSize < 20.0f) {
                // Medium rooms: slight reduction
                roomSizeScaling = 0.7f;
                smallRoomReduction = 0.8f;
                volumeBasedReduction = 0.8f;
            }
            float actualVolumeReduction = Math.max(0.1f, Math.min(1.0f, estimatedVolume / 500.0f));
            volumeBasedReduction *= actualVolumeReduction;

            float roomSizeEmphasis = roomSize < 10.0f ? Config.getInstance().smallRoomEmphasis * smallRoomReduction : Config.getInstance().largeRoomEmphasis;

            float diffusion = lerp(Config.getInstance().minDiffusion, Config.getInstance().maxDiffusion,
                    roomComplexity * Config.getInstance().diffusionComplexityWeight *
                            enclosureFactor * Config.getInstance().diffusionEnclosureWeight *
                            weightedReverbStrength * Config.getInstance().diffusionReverbWeight *
                            roomSizeEmphasis);
            diffusion = clamp(diffusion, 0.1f, 1.0f);

            float density = lerp(Config.getInstance().minDensity, 1.0f,
                    enclosureFactor * (1.0f - roomSize / Config.getInstance().densityRoomSizeFactor) * roomSizeEmphasis);
            density = clamp(density, 0.1f, 1.0f);

            float overallGain = enclosureFactor * distanceAttenuation * roomSizeScaling * volumeBasedReduction *
                    (Config.getInstance().baseReverbGain + reverbStrength * Config.getInstance().reverbGainMultiplier) *
                    Config.getInstance().globalReverbIntensity;
            overallGain = clamp(overallGain, 0.0f, Config.getInstance().maxOverallGain);

            float gainHF = (1.0f - dynamicAbsorption) * airAbsorptionFactor * enclosureFactor;
            gainHF = clamp(gainHF, 0.1f, 1.0f);

            float airAbsorptionHF = airAbsorptionFactor * enclosureFactor + opennessFactor * Config.getInstance().outdoorHfLeak;
            airAbsorptionHF = clamp(airAbsorptionHF, ReverbConstants.MIN_AIR_ABSORPTION_HF, ReverbConstants.MAX_AIR_ABSORPTION_HF);

            float roomRolloff = lerp(1.0f, 0.0f, enclosureFactor) * (roomSize / Config.getInstance().roomRolloffSizeFactor);
            roomRolloff = clamp(roomRolloff, ReverbConstants.MIN_ROOM_ROLLOFF, ReverbConstants.MAX_ROOM_ROLLOFF);

            float sendFilterGain = overallGain;
            float sendFilterGainHF = gainHF * Config.getInstance().sendFilterHfReduction;

            float echoDensity = lerp(1.0f, 0.4f, roomSize / Config.getInstance().densityRoomSizeFactor) * enclosureFactor;
            echoDensity = clamp(echoDensity, 0.1f, 1.0f);

            float modulationTime = clamp(roomSize * Config.getInstance().modulationTimeMultiplier, ReverbConstants.MIN_MODULATION_TIME, ReverbConstants.MAX_MODULATION_TIME);
            float modulationDepth = clamp(enclosureFactor * Config.getInstance().modulationDepthMultiplier, ReverbConstants.MIN_MODULATION_DEPTH, ReverbConstants.MAX_MODULATION_DEPTH);

            float hfReference = clamp(Config.getInstance().baseHfReference - roomSize * Config.getInstance().hfRoomSizeFactor,
                    ReverbConstants.MIN_HF_REFERENCE, ReverbConstants.MAX_HF_REFERENCE);
            float lfReference = clamp(Config.getInstance().baseLfReference - roomSize * Config.getInstance().lfRoomSizeFactor,
                    ReverbConstants.MIN_LF_REFERENCE, ReverbConstants.MAX_LF_REFERENCE);

            float gainLF = clamp(1.0f - dynamicAbsorption * Config.getInstance().dynamicAbsorptionLfFactor, ReverbConstants.MIN_GAIN_LF, ReverbConstants.MAX_GAIN_LF);

            float decayLfRatio = clamp(decayHfRatio * Config.getInstance().decayLfMultiplier,
                    ReverbConstants.MIN_DECAY_HF_RATIO, ReverbConstants.MAX_DECAY_HF_RATIO);

            float echoTime = clamp(roomSize * Config.getInstance().echoTimeMultiplier,
                    ReverbConstants.MIN_ECHO_TIME, ReverbConstants.MAX_ECHO_TIME);
            float echoDepth = clamp(echoDensity * Config.getInstance().echoDepthMultiplier,
                    ReverbConstants.MIN_ECHO_DEPTH, ReverbConstants.MAX_ECHO_DEPTH);

            // FIXED: Apply room size scaling to reflection gains and decay time
            reflectionsGain *= roomSizeScaling;
            reflectionsGain = clamp(reflectionsGain, ReverbConstants.MIN_REFLECTIONS_GAIN, ReverbConstants.MAX_REFLECTIONS_GAIN);

            lateReverbGain *= roomSizeScaling;
            lateReverbGain = clamp(lateReverbGain, ReverbConstants.MIN_LATE_REVERB_GAIN, ReverbConstants.MAX_LATE_REVERB_GAIN);

            effectiveDecayTime *= roomSizeScaling;
            effectiveDecayTime = clamp(effectiveDecayTime, ReverbConstants.MIN_EFFECTIVE_DECAY_TIME, ReverbConstants.MAX_EFFECTIVE_DECAY_TIME);

// Apply all parameters to OpenAL
            EXTEfx.alFilterf(sendFilter, EXTEfx.AL_LOWPASS_GAIN, sendFilterGain);
            EXTEfx.alFilterf(sendFilter, EXTEfx.AL_LOWPASS_GAINHF, sendFilterGainHF);

            alEffectf(reverbEffect, AL_EAXREVERB_DENSITY, density);
            alEffectf(reverbEffect, AL_EAXREVERB_DIFFUSION, diffusion);
            alEffectf(reverbEffect, AL_EAXREVERB_GAIN, overallGain);
            alEffectf(reverbEffect, AL_EAXREVERB_GAINHF, gainHF);
            alEffectf(reverbEffect, AL_EAXREVERB_GAINLF, gainLF);
            alEffectf(reverbEffect, AL_EAXREVERB_DECAY_TIME, effectiveDecayTime);
            alEffectf(reverbEffect, AL_EAXREVERB_DECAY_HFRATIO, decayHfRatio);
            alEffectf(reverbEffect, AL_EAXREVERB_DECAY_LFRATIO, decayLfRatio);
            alEffectf(reverbEffect, AL_EAXREVERB_REFLECTIONS_GAIN, reflectionsGain);
            alEffectf(reverbEffect, AL_EAXREVERB_REFLECTIONS_DELAY, reflectionsDelay);
            alEffectf(reverbEffect, AL_EAXREVERB_LATE_REVERB_GAIN, lateReverbGain);
            alEffectf(reverbEffect, AL_EAXREVERB_LATE_REVERB_DELAY, lateReverbDelay);
            alEffectf(reverbEffect, AL_EAXREVERB_AIR_ABSORPTION_GAINHF, airAbsorptionHF);
            alEffectf(reverbEffect, AL_EAXREVERB_ROOM_ROLLOFF_FACTOR, roomRolloff);
            alEffectf(reverbEffect, AL_EAXREVERB_ECHO_TIME, echoTime);
            alEffectf(reverbEffect, AL_EAXREVERB_ECHO_DEPTH, echoDepth);
            alEffectf(reverbEffect, AL_EAXREVERB_MODULATION_TIME, modulationTime);
            alEffectf(reverbEffect, AL_EAXREVERB_MODULATION_DEPTH, modulationDepth);
            alEffectf(reverbEffect, AL_EAXREVERB_HFREFERENCE, hfReference);
            alEffectf(reverbEffect, AL_EAXREVERB_LFREFERENCE, lfReference);

            AL11.alSource3i(sourceId, EXTEfx.AL_AUXILIARY_SEND_FILTER, auxFXSlot, 0, sendFilter);

        } catch (Exception e) {
            System.err.println("Error applying dynamic reverb: " + e.getMessage());
        }
    }

    private static void applyLegacyReverbToSource(int sourceId) {
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

    private static void debugSourceCount() {
        int sourcesInUse = 0;
        for (int i = 1; i < 1000; i++) { // Check first 1000 IDs
            if (AL10.alIsSource(i)) {
                sourcesInUse++;
            }
        }
        System.out.println("Sources currently in use: " + sourcesInUse);
    }

    @Inject(method = "destroy()V", at = @At("HEAD"))
    private void onAudioEngineStop(CallbackInfo ci) {
        cleanupEFXResources();
    }

    @Inject(method = "loadLibrary()V", at = @At("TAIL"))
    private void onAudioEngineStart(CallbackInfo ci) {
        efxInitialized = false;
        initializeReverb();
    }

    private static float clamp(float a, float b, float c) {
        return Math.min(Math.max(a,b),c);
    }

    private boolean isSoundBlacklisted(String soundName) {
        if (soundName == null || soundName.isEmpty()) {
            return false;
        }

        List<String> blacklist = Config.getInstance().soundBlacklist;
        if (blacklist == null || blacklist.isEmpty()) {
            return false;
        }

        // Check if any blacklist entry is contained in the sound name
        return blacklist.stream()
                .filter(entry -> entry != null && !entry.trim().isEmpty()) // filter out null/empty entries
                .anyMatch(entry -> soundName.toLowerCase().contains(entry.toLowerCase().trim()));
    }
}