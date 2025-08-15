package com.redsmods.sound_physics_perfected.mixin.client;

import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.sounds.ChannelAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChannelAccess.ChannelHandle.class)
public interface SourceManagerAccessor {
    /**
     * Accessor for the private `sources` map in SourceManager,
     * which maps SoundInstance → Channel.
     */
    @Accessor("channel")
    Channel getChannel();
}