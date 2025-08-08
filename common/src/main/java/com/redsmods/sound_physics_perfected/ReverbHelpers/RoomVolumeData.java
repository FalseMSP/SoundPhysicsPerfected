package com.redsmods.sound_physics_perfected.ReverbHelpers;

import java.util.List;

public class RoomVolumeData {
    public final double volume;
    public final double surfaceArea;
    public final List<String> dominantMaterials;
    public final boolean isEnclosed;

    public RoomVolumeData(double volume, double surfaceArea, List<String> materials, boolean enclosed) {
        this.volume = volume;
        this.surfaceArea = surfaceArea;
        this.dominantMaterials = materials;
        this.isEnclosed = enclosed;
    }
}
