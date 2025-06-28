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
    SoundInstance original;
    public RedSoundInstance(SoundInstance original) {
        if (original == null) {
            throw new IllegalArgumentException("Original SoundInstance cannot be null");
        }
        this.original = original;
    }

    @Override
    public Identifier getId() {
        return original.getId();
    }

    @Override
    public @Nullable WeightedSoundSet getSoundSet(SoundManager soundManager) {
        return original.getSoundSet(soundManager);
    }

    @Override
    public Sound getSound() {
        return original.getSound();
    }

    @Override
    public SoundCategory getCategory() {
        return original.getCategory();
    }

    @Override
    public boolean isRepeatable() {
        return original.isRepeatable();
    }

    @Override
    public boolean isRelative() {
        return original.isRelative();
    }

    @Override
    public int getRepeatDelay() {
        return original.getRepeatDelay();
    }

    @Override
    public float getVolume() {
        return original.getVolume();
    }

    @Override
    public float getPitch() {
        return original.getPitch();
    }

    @Override
    public double getX() {
        return original.getX();
    }

    @Override
    public double getY() {
        return original.getY();
    }

    @Override
    public double getZ() {
        return original.getZ();
    }

    @Override
    public SoundInstance.AttenuationType getAttenuationType() {
        return original.getAttenuationType();
    }

    @Override
    public String toString() {
        return String.format("RedSoundInstance{id=%s, category=%s, pos=[%.2f,%.2f,%.2f], vol=%.2f, pitch=%.2f}",
                getId(), getCategory(), getX(), getY(), getZ(), getVolume(), getPitch());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getCategory(), getVolume(), getPitch());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        RedSoundInstance that = (RedSoundInstance) obj;
        return  Double.compare(that.getVolume(), getVolume()) == 0 &&
                Double.compare(that.getPitch(), getPitch()) == 0 &&
                Objects.equals(getId(), getId()) &&
                Objects.equals(getCategory(), that.getCategory());
    }
}