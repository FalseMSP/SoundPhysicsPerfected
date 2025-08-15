package com.redsmods.sound_physics_perfected.storageclasses;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.phys.Vec3;

// Inner class to store sound data
@AllArgsConstructor @EqualsAndHashCode
public class SoundData {
    public final SoundInstance sound;
    public final Vec3 position;
    public final String soundId;
}