package com.redsmods.sound_physics_perfected.storageclasses;

import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.core.pattern.AbstractStyleNameConverter;

public class BlueRayResult {
    public boolean arrived;
    public Vec3d directionFromPlayer;
    public double distance;

    public BlueRayResult(boolean arrived, Vec3d directionFromPlayer, double distance) {
        this.arrived = arrived;
        this.directionFromPlayer = directionFromPlayer;
        this.distance = distance;
    }
}
