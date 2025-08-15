package com.redsmods.sound_physics_perfected.storageclasses;

import lombok.AllArgsConstructor;
import net.minecraft.world.phys.Vec3;

@AllArgsConstructor
public class BlueRayResult {
    public boolean arrived;
    public Vec3 directionFromPlayer;
    public double distance;
}
