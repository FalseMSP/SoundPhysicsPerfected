package com.redsmods.sound_physics_perfected.mixin.client;

import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Channel.class)
public interface SourceAccessor {
    /**
     * Accessor for the private `sources` map in SourceManager,
     * which maps SoundInstance → Channel.
     */
    @Accessor("source")
    int getSource();
}