package com.redsmods.sound_physics_perfected.mixin.client;

import com.mojang.blaze3d.audio.Channel;
import com.redsmods.sound_physics_perfected.OpenALEffectsHandler;
import com.redsmods.sound_physics_perfected.RaycastingHelper;
import com.redsmods.sound_physics_perfected.RedSoundInstance;
import com.redsmods.sound_physics_perfected.ReverbHelpers.EnhancedReverbData;
import com.redsmods.sound_physics_perfected.ReverbHelpers.LegacyReverb;
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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.redsmods.sound_physics_perfected.OpenALEffectsHandler.*;
import static com.redsmods.sound_physics_perfected.RaycastingHelper.*;
import static org.joml.Math.lerp;
import static org.lwjgl.openal.EXTEfx.*;

@Mixin(SoundEngine.class)
public abstract class SoundSystemMixin {

    @Shadow
    private SoundManager soundManager;
    @Shadow
    private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

    @Shadow public abstract void destroy();

    @Shadow public abstract void tick(boolean paused);

    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;", at = @At("HEAD"), cancellable = true)
    private void onSoundPlay(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (!fxHandler.efxInitialized) {
            fxHandler.initializeReverb();
        }

        if (isSoundBlacklisted(sound.toString())) return; // skip if in the overall blacklist

        if (!fxHandler.efxInitialized) return; // Skip if initialization failed

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
                if(Config.getInstance().procRange != -1 && soundPos.subtract(playerEyePos).length() > Config.getInstance().procRange) { // if sounds are too far, let it proc through vanilla means.
                    return;
                }

                // Get sound ID
                String soundId = sound.getLocation().toString();

                // Create sound data object
                RedSoundInstance redSoundData = new RedSoundInstance(sound);
                SoundData soundData = new SoundData(redSoundData, soundPos, soundId);

                // Add to queue
                soundQueue.offer(soundData);

                // Remove the oldest sounds if queue is too large
                if (soundQueue.size() > Config.getInstance().maxSounds) {
                    return;
                }

                cir.cancel();
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
                    target = "Lnet/minecraft/client/sounds/SoundEngine;tickInGameSound()V",
                    shift = At.Shift.AFTER),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void onSoundTick(boolean paused, CallbackInfo ci) {
        // Cast rays from player and detect entities
        Minecraft client = Minecraft.getInstance();
        if(client.level == null || client.player == null) {
            return;
        } // some error occurred
        RaycastingHelper.castBouncingRaysAndDetectSFX(client.level, client.player);
        RaycastingHelper.playQueuedObjects();

        if (!fxHandler.efxInitialized) {
            fxHandler.initializeReverb();
        }

        if (!fxHandler.efxInitialized) return; // Skip if initialization failed

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
                fxHandler.applyMuffleToSource(id,1-sound.getPermeationIndex());
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
        if (!fxHandler.efxInitialized) return sound; // fx aren't init, most likely permeation isn't playing
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
                        if (Config.getInstance().legacyReverb == LegacyReverb.VERSION140)
                            fxHandler.applyLegacyReverbToSource(sourceId);
                        else if (Config.getInstance().legacyReverb == LegacyReverb.VERSION100) {
                            fxHandler.applyInitalLegacyReverbToSource(sourceId);
                        } else
                            fxHandler.applyReverbToSource(sourceId);

//                        System.out.println("Source ID: " + sourceId);
                    }
                }
            }
        } catch (Exception e) {
            // Ignore errors
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
        fxHandler.cleanupEFXResources();
    }

    @Inject(method = "loadLibrary()V", at = @At("TAIL"))
    private void onAudioEngineStart(CallbackInfo ci) {
        fxHandler.efxInitialized = false;
        fxHandler.initializeReverb();
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