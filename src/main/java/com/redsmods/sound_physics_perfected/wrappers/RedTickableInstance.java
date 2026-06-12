package com.redsmods.sound_physics_perfected.wrappers;

import com.redsmods.sound_physics_perfected.RaycastingHelper;
import com.redsmods.sound_physics_perfected.config.Config;
import com.redsmods.sound_physics_perfected.config.RedsAttenuationType;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Delegate;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.List;

@Getter
public class RedTickableInstance implements TickableSoundInstance {
    private final ResourceLocation location;
    private final Sound sound;
    private final SoundSource source;
    @Setter private float attenuationMultiplier;
    @Delegate private SoundInstance wrapped;
    private double x;
    private double y;
    private double z;
    @Setter private boolean stopped;
    @Setter private float volume;
    private float pitch;
    private int tickCount;
    @Setter private Vec3 targetPosition;
    @Setter private float targetVolume;
    private final boolean isBlacklisted;
    @Getter private float originalVolume;
    @Getter private Vec3 originalPosition;

    public RedTickableInstance(ResourceLocation location, Sound sound, SoundSource source, Vec3 position, float volume, float pitch, SoundInstance wrapped, float attenuationMultiplier) {
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
        tickCount = 1;
        targetPosition = position;
        targetVolume = volume;
        isBlacklisted = isSoundTickBlacklisted(sound.toString());
        originalVolume = wrapped.getVolume();
        originalPosition = new Vec3(wrapped.getX(),wrapped.getY(),wrapped.getZ());
        this.attenuationMultiplier = attenuationMultiplier;
    }

    public boolean isStopped() {
        if (wrapped instanceof TickableSoundInstance) {
            stopped = ((TickableSoundInstance) wrapped).isStopped();
        }
        return stopped;
    };

    @Override
    public void tick() {
        System.out.println("[STAFF] vol=" + volume + " target=" + targetVolume
                + " wrappedVol=" + wrapped.getVolume()
                + " getVolume()=" + getVolume()
                + " blacklisted=" + isBlacklisted
                + " stopped=" + stopped
                + " tickRate=" + Config.getInstance().tickRate);
        updateWrapped();

        if (isBlacklisted) {
            return;
        }

        tickCount++;
        if (stopped || Config.getInstance().tickRate == 0) return; // DONE or ticking sounds is off
        if (tickCount % Config.getInstance().tickRate == 0) // only update once every .1 second
            if (!RaycastingHelper.tickQueue.contains(this))
                RaycastingHelper.tickQueue.add(this);
        updatePos();
        updateVolume();
    }
    public void updateWrapped() {
        if (wrapped instanceof TickableSoundInstance) {
            ((TickableSoundInstance) wrapped).tick();
            originalVolume = wrapped.getVolume();
            if (wrapped.getVolume() == 0.0) {
                volume = 0; // force muting sounds
                targetVolume = 0;
            }
            Vec3 delta = new Vec3(
                    wrapped.getX() - originalPosition.x,
                    wrapped.getY() - originalPosition.y,
                    wrapped.getZ() - originalPosition.z
            );
            originalPosition = new Vec3(wrapped.getX(), wrapped.getY(), wrapped.getZ());
            x += delta.x;
            y += delta.y;
            z += delta.z;
            targetPosition = new Vec3(x,y,z);
        }
    }

    @Override
    public Attenuation getAttenuation() {
        if (Config.getInstance().experimentalReverb) return Attenuation.LINEAR;
        else return Attenuation.NONE;
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

    public void updateVolume() {
        // Calculate the difference between current and target volume
        float deltaVolume = targetVolume - volume;

        // If we're already at the target or very close, set volume directly
        if (Math.abs(deltaVolume) <= 0.001f) {
            volume = targetVolume;
            return;
        }

        // Exponential smoothing for natural audio feel
        float ticksToTarget = Config.getInstance().tickRate; // number of ticks to reach target
        float deltaTime = 1.0f / 20.0f; // time per tick (20 ticks per second)
        float timeToTarget = ticksToTarget * deltaTime; // convert ticks to seconds

        // Use exponential interpolation - natural deceleration as we approach target
        float smoothingFactor = 1.0f - (float)Math.pow(0.001, deltaTime / timeToTarget);

        // Apply the smooth interpolation
        volume += deltaVolume * smoothingFactor;

        // Clamp to prevent overshooting due to floating point precision
        if (Math.abs(targetVolume - volume) < 0.001f) {
            volume = targetVolume;
        }
    }

    @Override
    public Sound getSound() {
        return this.sound;
    }

    private boolean isSoundTickBlacklisted(String soundName) {
        if (soundName == null || soundName.isEmpty()) {
            return false;
        }

        List<String> blacklist = Config.getInstance().soundTickBlacklist;
        if (blacklist == null || blacklist.isEmpty()) {
            return false;
        }

        // Check if any blacklist entry is contained in the sound name
        return blacklist.stream()
                .filter(entry -> entry != null && !entry.trim().isEmpty()) // filter out null/empty entries
                .anyMatch(entry -> soundName.toLowerCase().contains(entry.toLowerCase().trim()));
    }

    public float getVolume() {
        float vol = volume * Config.getInstance().volumeMultiplier;
        if (!Config.getInstance().experimentalReverb) return vol;
        if (attenuationMultiplier == 0) return vol; // if no rays make it, disable sound
        if (Config.getInstance().attenuationType.equals(RedsAttenuationType.VERCIDIUM_LINEAR) || Config.getInstance().attenuationType.equals(RedsAttenuationType.VERCIDIUM_INVERSE_SQUARE)) return (float) (vol * 1.0/attenuationMultiplier); // get volume without attenuation LINEAR
        return vol;
    }
}