package com.redsmods.sound_physics_perfected.ReverbHelpers;

public class EnhancedReverbData {
    public final double rt60; // Reverberation time
    public final double earlyReflectionDelay;
    public final double lateReflectionStrength;
    public final double roomSize;
    public final double absorption;
    public final String acousticProfile;
    public final boolean isIndoors;

    public EnhancedReverbData(double rt60, double earlyDelay, double lateStrength,
                              double roomSize, double absorption, String profile, boolean indoors) {
        this.rt60 = rt60;
        this.earlyReflectionDelay = earlyDelay;
        this.lateReflectionStrength = lateStrength;
        this.roomSize = roomSize;
        this.absorption = absorption;
        this.acousticProfile = profile;
        this.isIndoors = indoors;
    }
}