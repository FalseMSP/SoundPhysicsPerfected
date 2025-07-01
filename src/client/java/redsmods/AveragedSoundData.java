package redsmods;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class AveragedSoundData {
    public final SoundData soundEntity;
    public final Vec3d averageDirection;
    public final double averageDistance;
    public final double totalWeight;
    public final int rayCount;
    public final List<RayHitData> individualRays;

    public AveragedSoundData(SoundData soundEntity, Vec3d averageDirection, double averageDistance,
                             double totalWeight, int rayCount, List<RayHitData> individualRays) {
        this.soundEntity = soundEntity;
        this.averageDirection = averageDirection;
        this.averageDistance = averageDistance;
        this.totalWeight = totalWeight;
        this.rayCount = rayCount;
        this.individualRays = new ArrayList<>(individualRays);
    }
}
