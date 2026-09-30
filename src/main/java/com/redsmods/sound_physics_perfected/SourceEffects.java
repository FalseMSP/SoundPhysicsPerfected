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

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
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
            boolean zones = RaycastingHelper.fxHandler.zonesAvailable() && config.reverb && config.reverbZones
                    && mode == LegacyReverb.MODERN;
            return new Frame(config.reverb, mode, mode == LegacyReverb.MODERN ? config.airAbsorption : 0.0f, zones,
                    RaycastingHelper.isUnderwater(), leftWater, config.disableFXOnUI);
        }
    }

    /** What a playing source was last given, to send only changes and to take off effects that were switched off. */
    private record Applied(int generation, boolean directFilter, float directGain, float directHf, boolean send,
                           float sendGain, float sendHf, float air, int zone, float vx, float vy, float vz) {
    }

    /** Client thread only. */
    private static final Map<SoundInstance, Applied> APPLIED = new IdentityHashMap<>();

    /** Forget sources that are no longer playing (once per tick). */
    public static void forgetAllBut(Set<SoundInstance> playing) {
        APPLIED.keySet().retainAll(playing);
    }

    /**
     * The effects for one source, or null when nothing needs sending: effects are only sent when they change, and
     * ones that were switched off (reverb, air absorption, zones, water) are taken off again.
     *
     * @param fresh the source was just created (nothing on it yet)
     */
    public static Consumer<Channel> plan(SoundInstance instance, Frame frame, boolean fresh) {
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
        // Head-relative sounds (ambience loops, UI) are "in your head": the water does not muffle them again.
        boolean muffleForWater = frame.underwater && inWorld && !instance.isRelative();
        boolean directFilter = filtered || muffleForWater;
        float directGain = directFilter ? gain : 1.0f;
        float directHf = directFilter ? (muffleForWater ? hfRatio * RaycastingHelper.UNDERWATER_HF : hfRatio) : 1.0f;
        boolean send = frame.reverb && inWorld;
        float sendGain = send ? gain : 0.0f, sendHf = send ? hfRatio : 0.0f;
        float air = inWorld && !instance.isRelative() ? Math.min(frame.airAbsorption, 10.0f) : 0.0f;
        int zone = send && frame.zones ? RaycastingHelper.zoneOf(instance) : -1;
        float[] velocity = Doppler.velocityOf(instance); // measured from the sound's real movement
        float vx = velocity == null ? 0.0f : velocity[0];
        float vy = velocity == null ? 0.0f : velocity[1];
        float vz = velocity == null ? 0.0f : velocity[2];
        LegacyReverb mode = frame.mode;
        boolean legacy = send && mode != LegacyReverb.MODERN; // the legacy modes update their send every tick

        Applied now = new Applied(fx.generation(), directFilter, directGain, directHf, send, sendGain, sendHf, air,
                zone, vx, vy, vz);
        Applied before = fresh ? null : APPLIED.get(instance);
        APPLIED.put(instance, now);
        if (now.equals(before) && !legacy) return null; // nothing changed
        boolean known = before != null && before.generation == now.generation;

        boolean setFilter = directFilter;
        boolean clearFilter = !directFilter && (known ? before.directFilter : frame.leftWater && inWorld);
        boolean detachSend = !send && known && before.send;
        boolean setAir = air > 0.0f || (known && before.air > 0.0f);
        boolean setZone = zone >= 0 || (known && before.zone >= 0);
        boolean setVelocity = velocity != null || (known && (before.vx != 0 || before.vy != 0 || before.vz != 0));
        if (!setFilter && !clearFilter && !send && !detachSend && !setAir && !setZone && !setVelocity) return null;

        return channel -> {
            int source = ((SourceAccessor) channel).getSource();
            if (setFilter) {
                fx.setDirectFilter(source, directGain, directHf);
            } else if (clearFilter) {
                AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
            }
            if (send) {
                switch (mode) {
                    case VERSION140 -> fx.applyLegacyReverbToSource(source);
                    case VERSION100 -> fx.applyInitalLegacyReverbToSource(source);
                    default -> fx.setReverbSend(source, sendGain, sendHf);
                }
            } else if (detachSend) {
                fx.detachReverbSend(source);
            }
            if (setAir) {
                AL10.alSourcef(source, EXTEfx.AL_AIR_ABSORPTION_FACTOR, air);
            }
            if (setZone) {
                fx.attachZoneSend(source, zone);
            }
            if (setVelocity) {
                AL10.alSource3f(source, AL10.AL_VELOCITY, vx, vy, vz);
            }
        };
    }
}
