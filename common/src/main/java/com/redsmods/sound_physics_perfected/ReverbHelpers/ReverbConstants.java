package com.redsmods.sound_physics_perfected.ReverbHelpers;

public class ReverbConstants {

    // === DISTANCE & ATTENUATION ===
    public static float DISTANCE_ATTENUATION_LINEAR = 0.02f;     // Linear distance factor
    public static float DISTANCE_ATTENUATION_QUADRATIC = 0.0001f; // Quadratic distance factor
    public static float AIR_ABSORPTION_RATE = 0.003f;             // How quickly air absorbs sound
    public static float MIN_AIR_ABSORPTION = 0.2f;                // Minimum air absorption factor

    // === ROOM SIZE & VOLUME ===
    public static float ROOM_VOLUME_MULTIPLIER = 0.7f;            // Room volume estimation factor
    public static float SURFACE_AREA_MULTIPLIER = 6.0f;           // Surface area calculation (6 sides)
    public static float ROOM_COMPLEXITY_DIVISOR = 2.0f;           // Surface/volume complexity factor

    // === REVERB TIMING ===
    public static float LATE_REVERB_DELAY_MULTIPLIER = 2.0f;      // Late reverb delay vs early reflections
    public static float SOUND_SPEED = 343.0f;                     // Speed of sound (m/s)

    // === GAIN & STRENGTH ===
    public static float BASE_REVERB_GAIN = 0.1f;                  // Minimum reverb gain
    public static float REVERB_GAIN_MULTIPLIER = 0.4f;            // How much reverb strength affects gain
    public static float MAX_OVERALL_GAIN = 0.5f;                  // Maximum overall reverb gain
    public static float SEND_FILTER_HF_REDUCTION = 0.7f;          // HF reduction for send filter

    // === DIFFUSION & DENSITY ===
    public static float MIN_DIFFUSION = 0.3f;                     // Minimum diffusion value
    public static float MAX_DIFFUSION = 0.95f;                    // Maximum diffusion value
    public static float MIN_DENSITY = 0.3f;                       // Minimum density value
    public static float DENSITY_ROOM_SIZE_FACTOR = 100.0f;        // How room size affects density

    // === FREQUENCY RESPONSE ===
    public static float DYNAMIC_ABSORPTION_LF_FACTOR = 0.5f;      // How absorption affects low freq
    public static float DECAY_LF_MULTIPLIER = 1.2f;               // LF decay ratio vs HF decay ratio
    public static float OUTDOOR_HF_LEAK = 0.7f;                   // HF leak factor for outdoor spaces

    // === ECHO & MODULATION ===
    public static float ECHO_TIME_MULTIPLIER = 0.002f;            // Echo time based on room size
    public static float ECHO_DEPTH_MULTIPLIER = 0.1f;             // Echo depth based on density
    public static float MODULATION_TIME_MULTIPLIER = 0.01f;       // Modulation time based on room size
    public static float MODULATION_DEPTH_MULTIPLIER = 0.1f;       // Modulation depth based on enclosure

    // === FREQUENCY REFERENCES ===
    public static float BASE_HF_REFERENCE = 5000.0f;              // Base high frequency reference
    public static float HF_ROOM_SIZE_FACTOR = 20.0f;              // How room size affects HF reference
    public static float BASE_LF_REFERENCE = 250.0f;               // Base low frequency reference
    public static float LF_ROOM_SIZE_FACTOR = 2.0f;               // How room size affects LF reference

    // === ROLLOFF ===
    public static float ROOM_ROLLOFF_SIZE_FACTOR = 50.0f;         // How room size affects rolloff

    // === RT60 CALCULATION CONSTANTS ===
    public static float RT60_SABINE_CONSTANT = 0.161f;            // Sabine's RT60 formula constant
    public static float MIN_TOTAL_ABSORPTION = 0.1f;              // Minimum absorption to prevent division by zero

    // === INTERPOLATION WEIGHTS ===
    public static float DIFFUSION_COMPLEXITY_WEIGHT = 1.0f;       // Weight of room complexity in diffusion calc
    public static float DIFFUSION_ENCLOSURE_WEIGHT = 1.0f;        // Weight of enclosure in diffusion calc
    public static float DIFFUSION_REVERB_WEIGHT = 1.0f;           // Weight of reverb strength in diffusion calc

    // === TUNING MULTIPLIERS ===
    // Adjust these to globally increase/decrease various aspects
    public static float GLOBAL_REVERB_INTENSITY = 1.0f;           // Global reverb intensity multiplier
    public static float INDOOR_BIAS = 1.0f;                       // Bias toward indoor reverb characteristics
    public static float OUTDOOR_BIAS = 1.0f;                      // Bias toward outdoor reverb characteristics
    public static float SMALL_ROOM_EMPHASIS = 1.0f;               // Emphasis for small room characteristics
    public static float LARGE_ROOM_EMPHASIS = 1.0f;               // Emphasis for large room characteristics

    // === CLAMPING RANGES ===
    // These define the valid ranges for various parameters
    public static float MIN_WALL_DISTANCE = 0.5f;
    public static float MAX_WALL_DISTANCE = 200.0f;
    public static float MIN_RT60 = 0.1f;
    public static float MAX_RT60 = 10.0f;
    public static float MIN_CALCULATED_RT60 = 0.1f;
    public static float MAX_CALCULATED_RT60 = 8.0f;
    public static float MIN_ROOM_SIZE = 1.0f;
    public static float MAX_ROOM_SIZE = 100.0f;
    public static float MIN_ABSORPTION = 0.01f;
    public static float MAX_ABSORPTION = 0.9f;
    public static float MIN_EARLY_REFLECTION_DELAY = 0.001f;
    public static float MAX_EARLY_REFLECTION_DELAY = 0.1f;
    public static float MIN_LATE_REVERB_DELAY = 0.005f;
    public static float MAX_LATE_REVERB_DELAY = 0.2f;
    public static float MIN_SURFACE_TO_VOLUME_RATIO = 0.1f;
    public static float MAX_SURFACE_TO_VOLUME_RATIO = 5.0f;
    public static float MIN_EFFECTIVE_DECAY_TIME = 0.1f;
    public static float MAX_EFFECTIVE_DECAY_TIME = 8.0f;
    public static float MIN_DECAY_HF_RATIO = 0.1f;
    public static float MAX_DECAY_HF_RATIO = 2.0f;
    public static float MIN_REFLECTIONS_GAIN = 0.0f;
    public static float MAX_REFLECTIONS_GAIN = 0.8f;
    public static float MIN_LATE_REVERB_GAIN = 0.0f;
    public static float MAX_LATE_REVERB_GAIN = 1.0f;
    public static float MIN_AIR_ABSORPTION_HF = 0.5f;
    public static float MAX_AIR_ABSORPTION_HF = 1.0f;
    public static float MIN_ROOM_ROLLOFF = 0.0f;
    public static float MAX_ROOM_ROLLOFF = 1.0f;
    public static float MIN_ECHO_TIME = 0.075f;
    public static float MAX_ECHO_TIME = 0.25f;
    public static float MIN_ECHO_DEPTH = 0.0f;
    public static float MAX_ECHO_DEPTH = 1.0f;
    public static float MIN_MODULATION_TIME = 0.04f;
    public static float MAX_MODULATION_TIME = 4.0f;
    public static float MIN_MODULATION_DEPTH = 0.0f;
    public static float MAX_MODULATION_DEPTH = 1.0f;
    public static float MIN_HF_REFERENCE = 1000.0f;
    public static float MAX_HF_REFERENCE = 20000.0f;
    public static float MIN_LF_REFERENCE = 20.0f;
    public static float MAX_LF_REFERENCE = 1000.0f;
    public static float MIN_GAIN_LF = 0.1f;
    public static float MAX_GAIN_LF = 1.0f;
}
