package redsmods.storageclasses;

import net.minecraft.util.math.Vec3d;

public class RayHitData {
    public final RaycastResult rayResult;
    public final Vec3d direction;
    public final double weight; // Based on inverse square law

    public RayHitData(RaycastResult rayResult, Vec3d direction, double weight) {
        this.rayResult = rayResult;
        this.direction = direction;
        this.weight = weight;
    }
}
