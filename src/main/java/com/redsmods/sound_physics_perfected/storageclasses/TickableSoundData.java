package com.redsmods.sound_physics_perfected.storageclasses;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.phys.Vec3;

public class TickableSoundData extends SoundData {
    public TickableSoundData(SoundInstance sound, Vec3 position, String soundId) {
        super(sound, position, soundId);
    }
}
