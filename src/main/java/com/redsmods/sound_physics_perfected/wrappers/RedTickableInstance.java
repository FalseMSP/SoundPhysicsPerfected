package com.redsmods.sound_physics_perfected.wrappers;

import com.redsmods.sound_physics_perfected.RaycastingHelper;
import com.redsmods.sound_physics_perfected.acoustics.ReverbZones;
import com.redsmods.sound_physics_perfected.config.Config;
import com.redsmods.sound_physics_perfected.config.RedsAttenuationType;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Delegate;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
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
    /** Reverb of the space this sound is in, when that may differ from the listener's (see ReverbZones). */
    @Setter private ReverbZones.SourceReverb sourceReverb;
    /**
     * Energy loudness (see ListenerTrace#energyGain): how much of the sound gets past what is in the way, per band.
     * Applied as an OpenAL filter, so the sound keeps its vanilla volume and range.
     */
    private boolean energyGain;
    private float energyLf = 1.0f, energyHf = 1.0f, targetEnergyLf = 1.0f, targetEnergyHf = 1.0f;

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
        isBlacklisted = isSoundTickBlacklisted(location.toString()); // the event id, as the main blacklist uses
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
        updateWrapped();

        tickCount++;
        if (stopped || Config.getInstance().tickRate == 0) return; // DONE or ticking sounds is off
        // Blacklisted sounds are not re-traced, but still follow their original's position, volume and pitch.
        if (!isBlacklisted && tickCount % Config.getInstance().tickRate == 0) // only update once every .1 second
            if (!RaycastingHelper.tickQueue.contains(this))
                RaycastingHelper.tickQueue.add(this);
        updatePos();
        updateVolume();
        updatePitch();
        updateEnergyGain();
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
            // Follow the source's movement, keeping the offset the last trace gave it (it used to reset the target
            // to the current position every tick, so a moving sound never took a new traced position).
            x += delta.x;
            y += delta.y;
            z += delta.z;
            targetPosition = targetPosition.add(delta);
        }
    }

    /** Whether this sound is kept out of re-tracing by the tick blacklist. */
    public boolean isTickBlacklisted() {
        return isBlacklisted;
    }

    /**
     * The original was already resolved when it was intercepted; resolving it again (vanilla does when this plays)
     * would pick another random variant and change its pitch under the playing sound.
     */
    @Override
    public WeighedSoundEvents resolve(SoundManager soundManager) {
        return soundManager.getSoundEvent(location);
    }

    @Override
    public Attenuation getAttenuation() {
        if (energyGain) return wrapped.getAttenuation(); // loudness is in the filter: distance falls off as in vanilla
        if (Config.getInstance().experimentalReverb) return Attenuation.LINEAR;
        else return Attenuation.NONE;
    }

    public void stop() {
        this.stopped = true;
    }

    /** Switches this sound to energy loudness; {@code immediately} for a sound that has not started yet. */
    public void setEnergyGain(float low, float high, boolean immediately) {
        energyGain = true;
        targetEnergyLf = Math.max(0.0f, Math.min(1.0f, low));
        targetEnergyHf = Math.max(0.0f, Math.min(1.0f, high));
        if (immediately) {
            energyLf = targetEnergyLf;
            energyHf = targetEnergyHf;
        }
    }

    /** Back to the older loudness modes (the attenuation mode was switched while this played). */
    public void clearEnergyGain() {
        energyGain = false;
        energyLf = energyHf = targetEnergyLf = targetEnergyHf = 1.0f;
    }

    /** Treble that gets through relative to bass, for the energy loudness filter. */
    public float getEnergyHfRatio() {
        return energyLf > 1.0E-4f ? Math.min(1.0f, energyHf / energyLf) : 1.0f;
    }

    /** Eases the energy gain like the volume, so a sound walking behind a wall does not click. */
    public void updateEnergyGain() {
        if (!energyGain) return;
        float step = 1.0f - (float) Math.pow(0.001, 1.0 / Math.max(Config.getInstance().tickRate, 1));
        energyLf += (targetEnergyLf - energyLf) * step;
        energyHf += (targetEnergyHf - energyHf) * step;
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
        if (energyGain) return originalVolume * Config.getInstance().volumeMultiplier; // follows the sound's own volume
        float vol = volume * Config.getInstance().volumeMultiplier;
        if (!Config.getInstance().experimentalReverb) return vol;
        if (attenuationMultiplier == 0) return vol; // if no rays make it, disable sound
        if (Config.getInstance().attenuationType.equals(RedsAttenuationType.VERCIDIUM_LINEAR) || Config.getInstance().attenuationType.equals(RedsAttenuationType.VERCIDIUM_INVERSE_SQUARE)) return (float) (vol * 1.0/attenuationMultiplier); // get volume without attenuation LINEAR
        return vol;
    }

    protected void updatePitch() {
        if (!(wrapped instanceof TickableSoundInstance)) return; // a one-shot's pitch never changes
        pitch = wrapped.getPitch();
    }
}