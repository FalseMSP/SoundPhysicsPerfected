package com.redsmods.sound_physics_perfected.wrappers;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public class RedPositionedSoundInstance extends SimpleSoundInstance {
    public RedPositionedSoundInstance(ResourceLocation soundId, SoundSource category, float volume, float pitch, RandomSource random, boolean repeatable, int repeatDelay, Attenuation attenuationType, float x, float y, float z, boolean relative) {
        super(soundId,category,volume,pitch,random,repeatable,repeatDelay,attenuationType,x,y,z,relative);
    }
}
