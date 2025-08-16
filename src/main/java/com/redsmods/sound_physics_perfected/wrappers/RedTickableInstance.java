package com.redsmods.sound_physics_perfected.wrappers;

import com.redsmods.sound_physics_perfected.RaycastingHelper;
import com.redsmods.sound_physics_perfected.config.Config;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Delegate;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

@Getter
public class RedTickableInstance implements TickableSoundInstance {
    private final ResourceLocation location;
    private final Sound sound;
    private final SoundSource source;
    @Delegate private SoundInstance wrapped;
    private double x;
    private double y;
    private double z;
    @Setter private boolean stopped;
    private float volume;
    private float pitch;
    private int tickCount;
    @Setter private Vec3 targetPosition;
    private float targetVolume;

    public RedTickableInstance(ResourceLocation location, Sound sound, SoundSource source, Vec3 position, float volume, float pitch, SoundInstance wrapped, Vec3 originalPosition, float originalVolume) {
        this.location = location;
        this.sound = sound;
        this.source = source;
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
        this.stopped = false;
        this.volume = volume;
        this.pitch = pitch;
        this.wrapped = wrapped;
        tickCount = 0;
        targetPosition = position;
        targetVolume = volume;
    }

    @Override
    public void tick() {
        tickCount++;
        if (stopped || Config.getInstance().tickRate == 0) return; // DONE or ticking sounds is off
        if (!this.location.toString().contains("rain")) {
            if(tickCount % Math.max(Config.getInstance().tickRate, 8) == 0) // no way I shouldn't be having nesting like this oh noes.
                RaycastingHelper.tickQueue.add(this);
        } else if (tickCount % Config.getInstance().tickRate == 0) // only update once every .1 second
            RaycastingHelper.tickQueue.add(this);
        if (wrapped instanceof TickableSoundInstance)
            ((TickableSoundInstance) wrapped).tick();
        updatePos();
        updateVolume();
    }

    @Override
    public Attenuation getAttenuation() {
        return Attenuation.NONE;
    }

    public void stop() {
        this.stopped = true;
    }

    public void updatePos() {
        // Calculate the direction vector to the target
        double deltaX = targetPosition.x() - x;
        double deltaY = targetPosition.y() - y;
        double deltaZ = targetPosition.z() - z;

        // Calculate the distance to the target
        double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        double maxSpeed = 17.15; // m per tick, speed of sound

        // If we're already at the target or very close, set position directly
        if (distance <= 0.001 || distance > maxSpeed * Config.getInstance().tickRate) {
            x = targetPosition.x();
            y = targetPosition.y();
            z = targetPosition.z();
            return;
        }

        // Maximum speed in blocks per tick

        // Calculate how far we can move this tick
        double moveDistance = Math.min(maxSpeed, distance);

        // Normalize the direction vector and scale by move distance
        double moveX = (deltaX / distance) * moveDistance;
        double moveY = (deltaY / distance) * moveDistance;
        double moveZ = (deltaZ / distance) * moveDistance;

        // Update position
        x += moveX;
        y += moveY;
        z += moveZ;
    }

    public void setVolume(float targetVolume) {
        this.targetVolume = targetVolume;
    }
    public void updateVolume() {
        // Calculate the difference between current and target volume
        float deltaVolume = targetVolume - volume;
        float maxVolumeChange = Math.abs(deltaVolume / Config.getInstance().tickRate);

        // If we're already at the target or very close, set volume directly
        if (Math.abs(deltaVolume) <= 0.001f || Math.abs(deltaVolume) > maxVolumeChange * Config.getInstance().tickRate) {
            volume = targetVolume;
            return;
        }

        // Maximum volume change per tick

        // Calculate how much we can change this tick
        float volumeChange = Math.min(maxVolumeChange, Math.abs(deltaVolume));

        // Apply the change in the correct direction
        if (deltaVolume > 0) {
            volume += volumeChange;
        } else {
            volume -= volumeChange;
        }
    }

    @Override
    public Sound getSound() {
        return this.sound;
    }

    public float getOriginalVolume() {
        return wrapped.getVolume();
    }

    public Vec3 getOriginalPosition() {
        return new Vec3(wrapped.getX(),wrapped.getY(),wrapped.getZ());
    }
}