package redsmods;

import net.minecraft.util.math.Vec3d;

// Inner class to store sound data
public class SoundData {
    public Vec3d position;
    public boolean hasLineOfSight;
    public float originalVolume;

    public SoundData(Vec3d position, boolean hasLineOfSight, float originalVolume) {
        this.position = position;
        this.hasLineOfSight = hasLineOfSight;
        this.originalVolume = originalVolume;
    }
}