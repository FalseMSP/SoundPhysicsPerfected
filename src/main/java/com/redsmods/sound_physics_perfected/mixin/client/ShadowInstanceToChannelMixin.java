package com.redsmods.sound_physics_perfected.mixin.client;

import lombok.experimental.Delegate;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Set;

import static com.redsmods.sound_physics_perfected.RaycastingHelper.soundInstanceMap;
import static com.redsmods.sound_physics_perfected.RaycastingHelper.soundPermInstanceMap;

@Mixin(value = SoundEngine.class, priority = 9002)
public abstract class ShadowInstanceToChannelMixin {

    @Shadow
    @Mutable
    private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void simulated$wrapInstanceToChannel(CallbackInfo ci) {
        Map<SoundInstance, ChannelAccess.ChannelHandle> original = this.instanceToChannel;
        this.instanceToChannel = new java.util.AbstractMap<>() {

            @Override
            public boolean containsKey(Object key) {
                if (key == null) return false;
                if (original.containsKey(key)) return true;
                // Check SPP's remap — if original was wrapped, find the wrapper
                SoundInstance wrapped = soundInstanceMap.get((SoundInstance) key);
                if (wrapped == null) wrapped = soundPermInstanceMap.get((SoundInstance) key);
                return wrapped != null && original.containsKey(wrapped);
            }

            // Also handle get() so channel handles are still retrievable
            @Override
            public ChannelAccess.ChannelHandle get(Object key) {
                if (key == null) return null;
                ChannelAccess.ChannelHandle handle = original.get(key);
                if (handle != null) return handle;
                SoundInstance wrapped = soundInstanceMap.get((SoundInstance) key);
                if (wrapped == null) wrapped = soundPermInstanceMap.get((SoundInstance) key);
                return wrapped != null ? original.get(wrapped) : null;
            }

            // there's prolly a way to @Delegate, but I lowk don't understand how to do allat
            @Override
            public ChannelAccess.ChannelHandle put(SoundInstance key,
                                                   ChannelAccess.ChannelHandle value) {
                return original.put(key, value);
            }

            @Override
            public ChannelAccess.ChannelHandle remove(Object key) {
                return original.remove(key);
            }

            @Override
            public Set<Entry<SoundInstance, ChannelAccess.ChannelHandle>> entrySet() {
                return original.entrySet();
            }

            @Override
            public Set<SoundInstance> keySet() {
                return original.keySet();
            }

            @Override
            public int size() {
                return original.size();
            }

            @Override
            public void clear() {
                original.clear();
            }
        };
    }
}