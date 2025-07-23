package redsmods.storageclasses;

import net.minecraft.util.math.Vec3d;

public class RaycastResult {
    public final double totalDistance;
    public final Vec3d initialDirection;
    public final SoundData hitEntity;
    public final Vec3d finalPosition;
    public final boolean completed;

    public RaycastResult(double totalDistance, Vec3d initialDirection, SoundData hitEntity, Vec3d finalPosition, boolean completed) {
        this.totalDistance = totalDistance;
        this.initialDirection = initialDirection;
        this.hitEntity = hitEntity;
        this.finalPosition = finalPosition;
        this.completed = completed;
    }
}

