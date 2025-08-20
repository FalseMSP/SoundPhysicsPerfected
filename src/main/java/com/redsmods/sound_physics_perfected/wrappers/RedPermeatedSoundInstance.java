package com.redsmods.sound_physics_perfected.wrappers;

import com.redsmods.sound_physics_perfected.RaycastingHelper;
import com.redsmods.sound_physics_perfected.config.Config;
import lombok.Getter;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.EXTEfx;

import static org.joml.Math.lerp;

public class RedPermeatedSoundInstance extends RedTickableInstance {
    @Getter float permeationIndex;
    int tickCount = 0;
    private int id;
    public static Integer muffleFilter = -1;
    private boolean sourceSet = false;
    private float targetMuffle;

    public RedPermeatedSoundInstance(ResourceLocation soundID, Sound sound, SoundSource category, Vec3 position, float volume, float pitch, SoundInstance wrapped, Vec3 originalPos, float originalVolume, float permeationIndex) {
        super(soundID, sound, category,position, volume, pitch, wrapped, originalPos, originalVolume);
        this.permeationIndex = permeationIndex;

    }

    public void setPermeationIndex(float permeationIndex) {
        if (super.isStopped()) return;
        this.targetMuffle = permeationIndex;
    }

    @Override
    public void tick() {
        tickCount++;
        if (super.isStopped() || Config.getInstance().tickRate == 0) return; // DONE or ticking sounds is off
        if (tickCount % Config.getInstance().tickRate == 0) { // only update once every .1 second
            RaycastingHelper.permeatedTickQueue.add(this);
        }
        super.updatePos();
        super.updateVolume();
        updateMuffle();
    }

    public void updateMuffle() {

        // Calculate the difference between current and target volume
        float deltaVolume = targetMuffle - permeationIndex;
        float maxVolumeChange = Math.abs(deltaVolume / Config.getInstance().tickRate);

        // If we're already at the target or very close, set volume directly
        if (Math.abs(deltaVolume) <= 0.001f || Math.abs(deltaVolume) > maxVolumeChange * Config.getInstance().tickRate) {
            permeationIndex = targetMuffle;
            if (sourceSet && AL10.alIsSource(id))
                applyMuffleToSource(id,permeationIndex);
            return;
        }

        // Maximum volume change per tick

        // Calculate how much we can change this tick
        float volumeChange = Math.min(maxVolumeChange, Math.abs(deltaVolume));

        // Apply the change in the correct direction
        if (deltaVolume > 0) {
            permeationIndex += volumeChange;
        } else {
            permeationIndex -= volumeChange;
        }

        if (sourceSet && AL10.alIsSource(id))
            applyMuffleToSource(id,1-permeationIndex);
    }

    public void setSource(int id) {
        sourceSet = true;
        this.id = id;
    }

    public void applyMuffleToSource(int sourceId, float muffleStrength) {
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

    private static float clamp(float a, float b, float c) {
        return Math.min(Math.max(a,b),c);
    }
}
