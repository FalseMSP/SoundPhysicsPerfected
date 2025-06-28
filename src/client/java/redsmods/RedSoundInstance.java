package redsmods;

import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class RedSoundInstance implements SoundInstance {
    private final Identifier id;
    private final Sound sound;
    private final SoundCategory category;
    private final boolean repeatable;
    private final boolean relative;
    private final int repeatDelay;
    private final float volume;
    private final float pitch;
    private final double x;
    private final double y;
    private final double z;
    private final AttenuationType attenuationType;

    // Constructor for deep copying from another SoundInstance
    public RedSoundInstance(SoundInstance original) {
        if (original == null) {
            throw new IllegalArgumentException("Original SoundInstance cannot be null");
        }

        this.id = original.getId();
        this.category = original.getCategory();
        this.repeatable = original.isRepeatable();
        this.relative = original.isRelative();
        this.repeatDelay = original.getRepeatDelay();
        if (original.getSound() != null) {
            this.volume = original.getVolume();
            this.pitch = original.getPitch();
        }
        else {
            this.volume = 1.0f;
            this.pitch = 1.0f;
        }
        this.x = original.getX();
        this.y = original.getY();
        this.z = original.getZ();
        this.attenuationType = AttenuationType.NONE;

        // Handle potentially null sound-related properties
        this.sound = original.getSound();
    }

    // Static factory method for safe deep copying
    public static RedSoundInstance deepCopy(SoundInstance original) {
        if (original == null) {
            return null;
        }

        try {
            return new RedSoundInstance(original);
        } catch (Exception e) {
            System.err.println("Failed to create deep copy of SoundInstance: " + e.getMessage());
            return null;
        }
    }

    // Validation method
    public boolean isValid() {
        return id != null && category != null && attenuationType != null;
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public @Nullable WeightedSoundSet getSoundSet(SoundManager soundManager) {
        return null;
    }

    @Override
    public Sound getSound() {
        return sound;
    }

    @Override
    public SoundCategory getCategory() {
        return category;
    }

    @Override
    public boolean isRepeatable() {
        return repeatable;
    }

    @Override
    public boolean isRelative() {
        return relative;
    }

    @Override
    public int getRepeatDelay() {
        return repeatDelay;
    }

    @Override
    public float getVolume() {
        return volume;
    }

    @Override
    public float getPitch() {
        return pitch;
    }

    @Override
    public double getX() {
        return x;
    }

    @Override
    public double getY() {
        return y;
    }

    @Override
    public double getZ() {
        return z;
    }

    @Override
    public SoundInstance.AttenuationType getAttenuationType() {
        return attenuationType;
    }

    @Override
    public String toString() {
        return String.format("RedSoundInstance{id=%s, category=%s, pos=[%.2f,%.2f,%.2f], vol=%.2f, pitch=%.2f}",
                id, category, x, y, z, volume, pitch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, category, volume, pitch);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        RedSoundInstance that = (RedSoundInstance) obj;
        return  Double.compare(that.volume, volume) == 0 &&
                Double.compare(that.pitch, pitch) == 0 &&
                Objects.equals(id, id) &&
                Objects.equals(category, that.category);
    }
}