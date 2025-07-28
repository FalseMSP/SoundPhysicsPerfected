package com.redsmods.sound_physics_perfected.storageclasses;

import net.minecraft.client.sound.SoundInstance;
import net.minecraft.util.math.Vec3d;

public class TickableSoundData extends SoundData {
    public TickableSoundData(SoundInstance sound, Vec3d position, String soundId) {
        super(sound, position, soundId);
    }
}
