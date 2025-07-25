package redsmods.storageclasses;

import net.minecraft.util.math.Vec3d;

public class RaycastResult {
    public final double totalDistance;
    public final Vec3d initialDirection;
    public final SoundData hitEntity;

    public RaycastResult(double totalDistance, Vec3d initialDirection, SoundData hitEntity, Vec3d finalPosition) { // here bc old code i don't wanna update everywhere lmao
        this.totalDistance = totalDistance;
        this.initialDirection = initialDirection;
        this.hitEntity = hitEntity;
    }

    public RaycastResult(double totalDistance, Vec3d initialDirection, SoundData hitEntity) {
        this.totalDistance = totalDistance;
        this.initialDirection = initialDirection;
        this.hitEntity = hitEntity;
    }
}

