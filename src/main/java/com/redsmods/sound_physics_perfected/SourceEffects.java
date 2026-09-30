package com.redsmods.sound_physics_perfected;

import com.mojang.blaze3d.audio.Channel;
import com.redsmods.sound_physics_perfected.ReverbHelpers.LegacyReverb;
import com.redsmods.sound_physics_perfected.config.Config;
import com.redsmods.sound_physics_perfected.mixin.client.SourceAccessor;
import com.redsmods.sound_physics_perfected.wrappers.RedPermeatedSoundInstance;
import com.redsmods.sound_physics_perfected.wrappers.RedTickableInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;

import java.util.function.Consumer;

/**
 * What one playing source gets in OpenAL: the direct-path filter (energy loudness, permeation muffle, under
 * water), the reverb send, the zone send and air absorption. Worked out on the client thread; the returned
 * action runs on the sound thread, in order with the channel's own commands.
 */
public final class SourceEffects {
    private SourceEffects() {
    }

    /** Per-tick state shared by every source. */
    public record Frame(boolean reverb, LegacyReverb mode, float airAbsorption, boolean zones, boolean underwater,
                        boolean leftWater, boolean disableFxOnUi) {
        public static Frame now(boolean leftWater) {
            Config config = Config.getInstance();
            LegacyReverb mode = config.legacyReverb;
            return new Frame(config.reverb, mode, mode == LegacyReverb.MODERN ? config.airAbsorption : 0.0f,
                    RaycastingHelper.fxHandler.zonesAvailable(), RaycastingHelper.isUnderwater(), leftWater,
                    config.disableFXOnUI);
        }
    }

    /** The effects for one source, or null when it needs none. */
    public static Consumer<Channel> plan(SoundInstance instance, Frame frame) {
        OpenALEffectsHandler fx = RaycastingHelper.fxHandler;
        // Music (and optionally UI) is not "in the world": it gets neither reverb nor air absorption.
        SoundSource category = instance.getSource();
        boolean inWorld = category != SoundSource.MUSIC && !(frame.disableFxOnUi && category == SoundSource.MASTER);

        // What gets past walls: energy loudness, the older permeation muffle, or nothing.
        float gain = 1.0f, hfRatio = 1.0f;
        boolean filtered = false;
        if (instance instanceof RedTickableInstance red && red.isEnergyGain()) {
            gain = red.getEnergyLf();
            hfRatio = red.getEnergyHfRatio();
            filtered = true;
        } else if (instance instanceof RedPermeatedSoundInstance permeated) {
            gain = 1.0f - 0.8f * (1.0f - permeated.getPermeationIndex()); // at most -14 dB, as before
            hfRatio = permeated.getHfRatio();
            filtered = true;
        }
        boolean muffleForWater = frame.underwater && inWorld;
        float directGain = gain, sendGain = gain, sendHf = hfRatio;
        float directHf = muffleForWater ? hfRatio * RaycastingHelper.UNDERWATER_HF : hfRatio;
        boolean directFilter = filtered || muffleForWater;
        boolean clearWaterFilter = frame.leftWater && inWorld && !filtered;
        boolean send = frame.reverb && inWorld;
        boolean air = inWorld && frame.airAbsorption > 0.0f && !instance.isRelative();
        float airAbsorption = Math.min(frame.airAbsorption, 10.0f);
        int zone = send ? RaycastingHelper.zoneOf(instance) : -1;
        boolean zones = frame.zones;
        LegacyReverb mode = frame.mode;
        if (!directFilter && !send && !air && !clearWaterFilter) return null;

        return channel -> {
            int source = ((SourceAccessor) channel).getSource();
            if (directFilter) {
                fx.setDirectFilter(source, directGain, directHf);
            } else if (clearWaterFilter) {
                AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
            }
            if (send) {
                switch (mode) {
                    case VERSION140 -> fx.applyLegacyReverbToSource(source);
                    case VERSION100 -> fx.applyInitalLegacyReverbToSource(source);
                    default -> fx.setReverbSend(source, sendGain, sendHf);
                }
            }
            if (air) {
                AL10.alSourcef(source, EXTEfx.AL_AIR_ABSORPTION_FACTOR, airAbsorption);
            }
            if (zones) {
                fx.attachZoneSend(source, zone);
            }
        };
    }
}
