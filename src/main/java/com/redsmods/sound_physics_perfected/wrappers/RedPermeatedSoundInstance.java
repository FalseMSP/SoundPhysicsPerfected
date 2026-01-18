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

import static com.redsmods.sound_physics_perfected.RaycastingHelper.fxHandler;
import static org.joml.Math.lerp;

public class RedPermeatedSoundInstance extends RedTickableInstance {
    @Getter float permeationIndex;
    int tickCount = 0;
    private int id;
    private boolean sourceSet = false;
    private float targetMuffle;

    public RedPermeatedSoundInstance(ResourceLocation soundID, Sound sound, SoundSource category, Vec3 position, float volume, float pitch, SoundInstance wrapped, float permeationIndex, float attenuationMultiplier) {
        super(soundID, sound, category,position, volume, pitch, wrapped, attenuationMultiplier);
        this.permeationIndex = permeationIndex;
        this.targetMuffle = permeationIndex;
        updateMuffle();
    }

    public void setPermeationIndex(float permeationIndex) {
        if (super.isStopped()) return;
        this.targetMuffle = permeationIndex;
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
        updateMuffle();
    }

    public void updateMuffle() {

        // Calculate the difference between current and target volume
        float deltaVolume = targetMuffle - permeationIndex;
        float maxVolumeChange = Math.max(Math.abs(deltaVolume / Config.getInstance().tickRate),0.3f); // yay magic number

        // If we're already at the target or very close, set volume directly
        if (Math.abs(deltaVolume) <= 0.001f || Math.abs(deltaVolume) > maxVolumeChange * Config.getInstance().tickRate) {
            permeationIndex = targetMuffle;
            if (sourceSet && AL10.alIsSource(id))
                fxHandler.applyMuffleToSource(id,1-permeationIndex);
            return;
        }

        // Calculate how much we can change this tick
        float volumeChange = Math.min(maxVolumeChange, Math.abs(deltaVolume));

        // Apply the change in the correct direction
        if (deltaVolume > 0) {
            permeationIndex += volumeChange;
        } else {
            permeationIndex -= volumeChange;
        }

        if (sourceSet && AL10.alIsSource(id))
            fxHandler.applyMuffleToSource(id,1-permeationIndex);
    }

    public void setSource(int id) {
        sourceSet = true;
        this.id = id;
    }

    private static float clamp(float a, float b, float c) {
        return Math.min(Math.max(a,b),c);
    }
}
