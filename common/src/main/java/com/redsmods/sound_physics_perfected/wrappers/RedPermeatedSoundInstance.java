package com.redsmods.sound_physics_perfected.wrappers;

import com.redsmods.sound_physics_perfected.RaycastingHelper;
import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.EXTEfx;

import static com.redsmods.sound_physics_perfected.RaycastingHelper.TICK_RATE;
import static java.lang.Math.clamp;
import static org.joml.Math.lerp;

public class RedPermeatedSoundInstance extends RedTickableInstance {
    float permeationIndex;
    int tickCount = 0;
    private int id;
    public static Integer muffleFilter = -1;
    private boolean sourceSet = false;

    public RedPermeatedSoundInstance(Identifier soundID, Sound sound, SoundCategory category, Vec3d position, float volume, float pitch, SoundInstance wrapped, Vec3d originalPos, float originalVolume,float permeationIndex) {
        super(soundID, sound, category,position, volume, pitch, wrapped, originalPos, originalVolume);
        this.permeationIndex = permeationIndex;

    }

    public float getPermeationIndex() {
        return 1-permeationIndex;
    }

    public void setPermeationIndex(float permeationIndex) {
        if (super.isDone()) return;
        this.permeationIndex = permeationIndex;
        if (sourceSet && AL10.alIsSource(id))
            applyMuffleToSource(id,1-permeationIndex);
    }

    @Override
    public void tick() {
        tickCount++;
        if (super.isDone() || TICK_RATE == 0) return; // DONE or ticking sounds is off
        if (tickCount % TICK_RATE == 0) { // only update once every .1 second
            RaycastingHelper.permeatedTickQueue.add(this);
        }
        super.updatePos();
        super.updateVolume();
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
}
