package com.redsmods.sound_physics_perfected.storageclasses;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class AveragedSoundData {
    public final SoundData soundEntity;
    public final Vec3 averageDirection;
    public final double averageDistance;
    public final double totalWeight;
    public final int rayCount;
    public final List<RayHitData> individualRays;
    public final double averageMuffle;

    public AveragedSoundData(SoundData soundEntity, Vec3 averageDirection, double averageDistance,
                             double totalWeight, int rayCount, List<RayHitData> individualRays, double averageMuffle) {
        this.soundEntity = soundEntity;
        this.averageDirection = averageDirection;
        this.averageDistance = averageDistance;
        this.totalWeight = totalWeight;
        this.rayCount = rayCount;
        this.individualRays = new ArrayList<>(individualRays);
        this.averageMuffle = averageMuffle;
    }
    public AveragedSoundData(SoundData soundEntity, Vec3 averageDirection, double averageDistance,
                             double totalWeight, int rayCount, List<RayHitData> individualRays) {
        this(soundEntity,averageDirection,averageDistance,totalWeight,rayCount,individualRays,0);
    }
}
