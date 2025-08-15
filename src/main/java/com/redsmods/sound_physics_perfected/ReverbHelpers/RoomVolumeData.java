package com.redsmods.sound_physics_perfected.ReverbHelpers;

import lombok.AllArgsConstructor;

import java.util.List;

@AllArgsConstructor
public class RoomVolumeData {
    public final double volume;
    public final double surfaceArea;
    public final List<String> dominantMaterials;
    public final boolean isEnclosed;
}
