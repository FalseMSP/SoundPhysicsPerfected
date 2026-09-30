package com.redsmods.sound_physics_perfected.wrappers;

import com.redsmods.sound_physics_perfected.RaycastingHelper;
import com.redsmods.sound_physics_perfected.config.Config;
import lombok.Getter;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * A traced sound heard (partly) through blocks. The muffle filter itself is applied to its OpenAL source by
 * SoundSystemMixin every tick; this class only eases the permeation value towards each new trace result.
 */
public class RedPermeatedSoundInstance extends RedTickableInstance {
    @Getter float permeationIndex;
    /** Like permeationIndex, for high frequencies (walls stop treble more than bass). */
    @Getter float permeationIndexHf;
    int tickCount = 0;
    private float targetMuffle;
    private float targetMuffleHf;

    public RedPermeatedSoundInstance(ResourceLocation soundID, Sound sound, SoundSource category, Vec3 position, float volume, float pitch, SoundInstance wrapped, float permeationIndex, float attenuationMultiplier) {
        this(soundID, sound, category, position, volume, pitch, wrapped, permeationIndex, permeationIndex, attenuationMultiplier);
    }

    public RedPermeatedSoundInstance(ResourceLocation soundID, Sound sound, SoundSource category, Vec3 position, float volume, float pitch, SoundInstance wrapped, float permeationIndex, float permeationIndexHf, float attenuationMultiplier) {
        super(soundID, sound, category,position, volume, pitch, wrapped, attenuationMultiplier);
        this.permeationIndex = permeationIndex;
        this.targetMuffle = permeationIndex;
        this.permeationIndexHf = permeationIndexHf;
        this.targetMuffleHf = permeationIndexHf;
        super.updatePos();
        super.updateVolume();
    }

    public void setPermeationIndex(float permeationIndex) {
        if (super.isStopped()) return;
        this.targetMuffle = permeationIndex;
    }

    public void setPermeationIndexHf(float permeationIndexHf) {
        if (super.isStopped()) return;
        this.targetMuffleHf = permeationIndexHf;
    }

    /** Treble that gets through relative to bass (1 = no extra high-frequency loss). */
    public float getHfRatio() {
        return permeationIndex > 1.0E-4f ? Math.min(1.0f, permeationIndexHf / permeationIndex) : 0.0f;
    }

    @Override
    public void tick() {
        super.updateWrapped();
        tickCount++;
        if (super.isStopped() || Config.getInstance().tickRate == 0) return; // DONE or ticking sounds is off
        if (tickCount % Config.getInstance().tickRate == 0) { // only update once every .1 second
            if (!RaycastingHelper.permeatedTickQueue.contains(this))
                RaycastingHelper.permeatedTickQueue.add(this);
        }
        super.updatePos();
        super.updateVolume();
        super.updatePitch();
        updateMuffle();
    }

    public void updateMuffle() {
        permeationIndex = ease(permeationIndex, targetMuffle);
        permeationIndexHf = ease(permeationIndexHf, targetMuffleHf);
    }

    private static float ease(float current, float target) {
        float delta = target - current;
        float maxChange = Math.max(Math.abs(delta / Math.max(Config.getInstance().tickRate, 1)), 0.3f);

        // Already there, or so far off that easing would lag audibly: jump
        if (Math.abs(delta) <= 0.001f || Math.abs(delta) > maxChange * Config.getInstance().tickRate) {
            return target;
        }

        float change = Math.min(maxChange, Math.abs(delta));
        return current + (delta > 0 ? change : -change);
    }
}
