package com.redsmods.sound_physics_perfected.ReverbHelpers;

public class ReverbSurfaceData {
    public final double absorptionCoefficient;
    public final double reflectivityCoefficient;
    public final String surfaceType;

    public ReverbSurfaceData(double absorption, double reflectivity, String type) {
        this.absorptionCoefficient = absorption;
        this.reflectivityCoefficient = reflectivity;
        this.surfaceType = type;
    }
}