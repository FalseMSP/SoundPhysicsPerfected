package redsmods;

import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public class VolumeAdjustedSFX implements SoundInstance {
    private final SoundInstance original;
    private final float modifiedVolume;

    public VolumeAdjustedSFX(SoundInstance original, float modifiedVolume) {
        this.original = original;
        this.modifiedVolume = modifiedVolume;
    }

    @Override
    public net.minecraft.util.Identifier getId() {
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
    public float getVolume() {
        return modifiedVolume;
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
    public AttenuationType getAttenuationType() {
        return original.getAttenuationType();
    }

    @Override
    public boolean isRepeatable() {
        return original.isRepeatable();
    }

    @Override
    public int getRepeatDelay() {
        return original.getRepeatDelay();
    }

    @Override
    public boolean canPlay() {
        return original.canPlay();
    }

    @Override
    public boolean isRelative() {
        return original.isRelative();
    }
}