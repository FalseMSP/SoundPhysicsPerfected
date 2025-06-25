package redsmods;

import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public class VolumeAdjustedSFX implements SoundInstance {
    private final SoundInstance original;
    private final double adjustedX;
    private final double adjustedY;
    private final double adjustedZ;

    public VolumeAdjustedSFX(SoundInstance original, Vec3d playerPos, float distanceMultiplier) {
        this.original = original;

        // Calculate direction from player to sound
        Vec3d soundPos = new Vec3d(original.getX(), original.getY(), original.getZ());
        Vec3d direction = soundPos.subtract(playerPos).normalize();

        // Move the sound further away in the same direction
        double currentDistance = playerPos.distanceTo(soundPos);
        double newDistance = currentDistance * distanceMultiplier;

        Vec3d newSoundPos = playerPos.add(direction.multiply(newDistance));

        this.adjustedX = newSoundPos.x;
        this.adjustedY = newSoundPos.y;
        this.adjustedZ = newSoundPos.z;
    }

    // Override position methods to return the adjusted position
    @Override
    public double getX() {
        return adjustedX;
    }

    @Override
    public double getY() {
        return adjustedY;
    }

    @Override
    public double getZ() {
        return adjustedZ;
    }

    // Delegate all other methods to the original sound
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
    public float getVolume() {
        return original.getVolume();
    }

    @Override
    public float getPitch() {
        return original.getPitch();
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
    public boolean isRelative() {
        return original.isRelative();
    }

    @Override
    public SoundCategory getCategory() {
        return original.getCategory();
    }
}