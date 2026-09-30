package com.redsmods.sound_physics_perfected.mixin.client;

import com.mojang.blaze3d.audio.Channel;
import com.redsmods.sound_physics_perfected.RaycastingHelper;
import com.redsmods.sound_physics_perfected.Doppler;
import com.redsmods.sound_physics_perfected.RedSoundInstance;
import com.redsmods.sound_physics_perfected.SourceEffects;
import com.redsmods.sound_physics_perfected.ReverbHelpers.LegacyReverb;
import com.redsmods.sound_physics_perfected.acoustics.ReverbParams;
import com.redsmods.sound_physics_perfected.config.Config;
import com.redsmods.sound_physics_perfected.config.DebugType;
import com.redsmods.sound_physics_perfected.storageclasses.SoundData;
import com.redsmods.sound_physics_perfected.wrappers.RedPermeatedSoundInstance;
import com.redsmods.sound_physics_perfected.wrappers.RedPositionedSoundInstance;
import com.redsmods.sound_physics_perfected.wrappers.RedTickableInstance;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.redsmods.sound_physics_perfected.RaycastingHelper.*;

@Mixin(value = SoundEngine.class, priority = 9001) // its over 9000!!!!
public abstract class SoundSystemMixin {

    @Shadow
    private SoundManager soundManager;
    @Shadow
    private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

    @Unique
    private int spp$debugTicks;
    @Unique
    private double spp$tickMillis;
    @Unique
    private boolean spp$wasUnderwater;

    //? if <1.21.6 {
    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void onSoundPlay(SoundInstance sound, CallbackInfo ci) {
    //?} else {
    /*@Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;", at = @At("HEAD"), cancellable = true)
    private void onSoundPlay(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
     *///?}
        if (sound == null) return;
        if (!fxHandler.efxInitialized && !fxHandler.efxUnsupported) {
            fxHandler.initializeReverb();
        }

        if (bypass.contains(sound)) return; // handed back to vanilla after a failed trace
        if (isSoundBlacklisted(sound.toString())) return; // skip if in the overall blacklist

        if (!fxHandler.efxInitialized) return; // Skip if initialization failed

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null || soundManager == null) {
            return;
        }

        // ignore UI sounds
        if (Config.getInstance().disableFXOnUI && sound.getSource() == SoundSource.MASTER) {
            return;
        }

        try {
            sound.resolve(soundManager); // load pitches and whatnot into the sound data
            // Relative sounds are positioned around the listener rather than in the world, so they cannot be traced.
            if (!(sound instanceof RedPositionedSoundInstance || sound instanceof RedTickableInstance)
                    && sound.getAttenuation() != SoundInstance.Attenuation.NONE && !sound.isRelative()) {
                Vec3 soundPos = new Vec3(sound.getX(), sound.getY(), sound.getZ());
                // Where it really is (a sound on a Sable sub-level is positioned in the sub-level's plot).
                Vec3 worldPos = RaycastingHelper.subLevelToWorld(client.level, soundPos);
                // The listener's own sounds (steps, splashes, swimming, eating) come from their own body: nothing
                // is in the way, so they play as in vanilla (still with reverb, and muffled with the head under water).
                if (client.player.getBoundingBox().inflate(0.3).contains(worldPos)) {
                    return;
                }
                if (Config.getInstance().procRange != -1 && worldPos.subtract(playerEyePos).length() > Config.getInstance().procRange) { // if sounds are too far, let it proc through vanilla means.
                    return;
                }

                // ignore if size too big
                if (soundQueue.size() > Config.getInstance().maxSounds) {
                    return;
                }
                soundQueue.offer(new SoundData(new RedSoundInstance(sound), soundPos, sound.getLocation().toString()));
                notProcd.add(sound);

                /*? if >= 1.21.6 {*/ /*cir.setReturnValue(SoundEngine.PlayResult.STARTED); *//*?} else {*/ ci.cancel(); /*?}*/
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
                    /*target = "Lnet/minecraft/client/sounds/SoundEngine;tickInGameSound()V",*/
                    //? if <1.21.6
                    target = "Lnet/minecraft/client/sounds/SoundEngine;tickNonPaused()V",
                    shift = At.Shift.AFTER)
    )
    private void onSoundTick(boolean paused, CallbackInfo ci) {
        long start = System.nanoTime();
        try {
            spp$tick();
        } catch (RuntimeException e) {
            RaycastingHelper.logError("Error in the sound physics tick", e); // never break vanilla's sound tick
        }
        spp$tickMillis += ((System.nanoTime() - start) / 1.0e6 - spp$tickMillis) * 0.05; // ~1 s average
    }

    @Unique
    private void spp$tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return;
        }
        RaycastingHelper.onSoundEngineTick(client.level, client.player);

        if (!fxHandler.ensureValid()) return; // no EFX on this device

        Config config = Config.getInstance();
        LegacyReverb mode = config.legacyReverb;
        boolean reverb = config.reverb;
        if (reverb) {
            if (mode == LegacyReverb.MODERN) {
                ReverbParams environment = RaycastingHelper.getCurrentReverb();
                fxHandler.applyEnvironment(environment);
                spp$debugReverb(client, config, environment);
            } else {
                fxHandler.useLegacyParams();
            }
        }

        RaycastingHelper.updateZones(instanceToChannel.keySet());
        Doppler.tick(client.player.getEyePosition(), instanceToChannel.keySet(), config.dopplerStrength / 100.0f,
                config.soundSpeed);
        boolean underwater = RaycastingHelper.isUnderwater();
        // Clear the underwater filter once from sounds that only had it because the listener was under water.
        SourceEffects.Frame frame = SourceEffects.Frame.now(spp$wasUnderwater && !underwater);
        spp$wasUnderwater = underwater;
        for (Map.Entry<SoundInstance, ChannelAccess.ChannelHandle> entry : instanceToChannel.entrySet()) {
            SoundInstance instance = entry.getKey();
            ChannelAccess.ChannelHandle handle = entry.getValue();
            if (instance == null || handle == null) continue;
            Consumer<Channel> effects = SourceEffects.plan(instance, frame, false);
            if (effects != null) {
                handle.execute(effects); // on the sound thread, in order with the channel's own commands
            }
        }
        SourceEffects.forgetAllBut(instanceToChannel.keySet());

        RaycastingHelper.pruneFinished(instanceToChannel::containsKey);
    }

    @Inject(method = "updateSource(Lnet/minecraft/client/Camera;)V", at = @At("TAIL"))
    private void onFrame(Camera camera, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;
        try {
            spp$frame(client, camera);
        } catch (RuntimeException e) {
            RaycastingHelper.logError("Error in the sound physics frame update", e);
        }
    }

    @Unique
    private void spp$frame(Minecraft client, Camera camera) {
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector(), look = camera.getLookVector();
        RaycastingHelper.setListenerBasis(left.x(), left.y(), left.z(), up.x(), up.y(), up.z(), look.x(), look.y(), look.z());
        RaycastingHelper.onFrame(client.level, client.player);

        // Reverb direction is relative to where the camera faces: keep it current between ticks.
        Config config = Config.getInstance();
        if (config.reverb && config.directionalReverb && config.legacyReverb == LegacyReverb.MODERN && fxHandler.efxInitialized) {
            fxHandler.applyEnvironment(RaycastingHelper.getCurrentReverb());
            RaycastingHelper.refreshZones();
        }
    }

    @Unique
    private void spp$debugReverb(Minecraft client, Config config, ReverbParams environment) {
        if (config.reverbTuning == DebugType.OFF || environment == null || client.player == null) return;
        boolean actionBar = config.reverbTuning == DebugType.ACTION_BAR;
        if (!actionBar && spp$debugTicks++ % 20 != 0) return; // chat: once a second
        client.player.displayClientMessage(Component.literal(String.format(java.util.Locale.ROOT, "%s | %s, client thread %.2f ms/tick",
                environment, RaycastingHelper.lastTraceSummary(), spp$tickMillis)), actionBar);
    }

    @ModifyVariable(method = "stop(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"), argsOnly = true)
    private SoundInstance modifySoundParameter(SoundInstance sound) {
        if (sound == null) return sound; // sorry, if some other mod kills their sound by using a mixin, i am not finna be held responsible, that's their own fault.
        RaycastingHelper.onOriginalStopped(sound);
        SoundInstance customSound = soundInstanceMap.remove(sound);
        RedPermeatedSoundInstance soundPermeation = soundPermInstanceMap.remove(sound);
        if (soundPermeation != null)
            soundPermeation.setStopped(true);

        // remove the permeation manually before using the default stop method
        ChannelAccess.ChannelHandle sourceManager = soundPermeation == null ? null : instanceToChannel.get(soundPermeation);
        if (sourceManager != null) {
            sourceManager.execute(Channel::stop);
        }

        // remove custom sounds
        ChannelAccess.ChannelHandle sourceManagerNormal = customSound == null ? null : instanceToChannel.get(customSound);
        if (sourceManagerNormal != null) {
            sourceManagerNormal.execute(Channel::stop);
        }

        return sound;
    }

    @Inject(method = "stopAll()V", at = @At("HEAD"))
    private void onStopAll(CallbackInfo ci) {
        RaycastingHelper.onStopAll();
    }

    @Inject(method = "destroy()V", at = @At("HEAD"))
    private void onAudioEngineStop(CallbackInfo ci) {
        fxHandler.forgetResources(); // the context is destroyed right after, with everything in it
    }

    @Inject(method = "loadLibrary()V", at = @At("TAIL"))
    private void onAudioEngineStart(CallbackInfo ci) {
        Doppler.reset();
        fxHandler.efxInitialized = false;
        fxHandler.efxUnsupported = false;
        fxHandler.initializeReverb();
    }

    @Unique
    private boolean isSoundBlacklisted(String soundName) {
        if (soundName == null || soundName.isEmpty()) {
            return false;
        }

        List<String> blacklist = Config.getInstance().soundBlacklist;
        if (blacklist == null || blacklist.isEmpty()) {
            return false;
        }

        String lower = soundName.toLowerCase();
        for (String entry : blacklist) {
            if (entry != null && !entry.isBlank() && lower.contains(entry.toLowerCase().trim())) {
                return true;
            }
        }
        return false;
    }
}
