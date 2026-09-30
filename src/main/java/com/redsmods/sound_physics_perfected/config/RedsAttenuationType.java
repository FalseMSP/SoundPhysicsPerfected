package com.redsmods.sound_physics_perfected.config;

public enum RedsAttenuationType {
    /** Share of the listener's rays that reach a sound, per band (Vercidium); distance falls off as in vanilla. */
    ENERGY,
    INVERSE_SQUARE,
    LINEAR,
    VERCIDIUM_INVERSE_SQUARE,
    VERCIDIUM_LINEAR
}
