package com.redsmods.sound_physics_perfected.storageclasses;

import lombok.AllArgsConstructor;
import net.minecraft.world.phys.Vec3;

@AllArgsConstructor
public class RayHitData {
    public final RaycastResult rayResult;
    public final Vec3 direction;
    public final double weight; // Based on inverse square law
}
