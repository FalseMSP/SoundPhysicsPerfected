package com.redsmods.sound_physics_perfected.ReverbHelpers;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class EnhancedReverbData {
    public final double rt60; // Reverberation time
    public final double earlyReflectionDelay;
    public final double lateReflectionStrength;
    public final double roomSize;
    public final double absorption;
    public final String acousticProfile;
    public final boolean isIndoors;
}