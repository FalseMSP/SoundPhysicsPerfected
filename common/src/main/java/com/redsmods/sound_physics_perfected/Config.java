package com.redsmods.sound_physics_perfected;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.redsmods.sound_physics_perfected.ReverbHelpers.ReverbConstants;
import dev.architectury.platform.Platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class Config {
    private static Config INSTANCE;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = Platform.getConfigFolder().resolve("SoundPhysicsPerfected.json");

    // Original settings
    public int raysCast = 1000;
    public int raysBounced = 3;
    public boolean reverbEnabled = true;
    public boolean permeationEnabled = true;
    public int maxRayLength = 4; // chunks
    public float SoundMult = 1; // make it just work like default Minecraft for lag helping :)
    public int tickRate = 2; // once every 2 ticks bc i want poor people's pcs to burn
    public RedsAttenuationType attenuationType = RedsAttenuationType.INVERSE_SQUARE;
    public double permeationStepSize = 0.01;

    // === REVERB TUNING CONSTANTS ===
    // Global Controls
    public float globalReverbIntensity = 1.0f;
    public float indoorBias = 1.0f;
    public float outdoorBias = 1.0f;
    public float smallRoomEmphasis = 1.0f;
    public float largeRoomEmphasis = 1.0f;

    // Distance & Attenuation
    public float distanceAttenuationLinear = 0.02f;
    public float distanceAttenuationQuadratic = 0.0001f;
    public float airAbsorptionRate = 0.003f;
    public float minAirAbsorption = 0.2f;

    // Room Size & Volume
    public float roomVolumeMultiplier = 0.7f;
    public float surfaceAreaMultiplier = 6.0f;
    public float roomComplexityDivisor = 2.0f;

    // Reverb Timing
    public float lateReverbDelayMultiplier = 2.0f;
    public float soundSpeed = 343.0f;

    // Gain & Strength
    public float baseReverbGain = 0.1f;
    public float reverbGainMultiplier = 0.4f;
    public float maxOverallGain = 0.5f;
    public float sendFilterHfReduction = 0.7f;

    // Diffusion & Density
    public float minDiffusion = 0.3f;
    public float maxDiffusion = 0.95f;
    public float minDensity = 0.3f;
    public float densityRoomSizeFactor = 100.0f;

    // Frequency Response
    public float dynamicAbsorptionLfFactor = 0.5f;
    public float decayLfMultiplier = 1.2f;
    public float outdoorHfLeak = 0.7f;

    // Echo & Modulation
    public float echoTimeMultiplier = 0.002f;
    public float echoDepthMultiplier = 0.1f;
    public float modulationTimeMultiplier = 0.01f;
    public float modulationDepthMultiplier = 0.1f;

    // Frequency References
    public float baseHfReference = 5000.0f;
    public float hfRoomSizeFactor = 20.0f;
    public float baseLfReference = 250.0f;
    public float lfRoomSizeFactor = 2.0f;

    // Rolloff
    public float roomRolloffSizeFactor = 50.0f;

    // RT60 Calculation
    public float rt60SabineConstant = 0.161f;
    public float minTotalAbsorption = 0.1f;

    // Interpolation Weights
    public float diffusionComplexityWeight = 1.0f;
    public float diffusionEnclosureWeight = 1.0f;
    public float diffusionReverbWeight = 1.0f;

    public static Config getInstance() {
        if (INSTANCE == null) {
            INSTANCE = loadConfig();
        }
        return INSTANCE;
    }

    private static Config loadConfig() {
        Config config;

        if (Files.exists(CONFIG_FILE)) {
            try {
                String json = Files.readString(CONFIG_FILE);
                config = GSON.fromJson(json, Config.class);

                // Apply loaded config values to ReverbConstants
                config.applyToReverbConstants();
            } catch (Exception e) {
                System.err.println("Failed to load config, using defaults: " + e.getMessage());
                config = new Config();
            }
        } else {
            config = new Config();
            config.save(); // Create default config file
        }

        return config;
    }

    public void save() {
        try {
            // Ensure config directory exists
            Files.createDirectories(CONFIG_FILE.getParent());

            // Sync current ReverbConstants values to config before saving
            syncFromReverbConstants();

            String json = GSON.toJson(this);
            Files.writeString(CONFIG_FILE, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }

    public void reload() {
        INSTANCE = loadConfig();
    }

    /**
     * Apply config values to ReverbConstants (called after loading config)
     */
    public void applyToReverbConstants() {
        ReverbConstants.GLOBAL_REVERB_INTENSITY = this.globalReverbIntensity;
        ReverbConstants.INDOOR_BIAS = this.indoorBias;
        ReverbConstants.OUTDOOR_BIAS = this.outdoorBias;
        ReverbConstants.SMALL_ROOM_EMPHASIS = this.smallRoomEmphasis;
        ReverbConstants.LARGE_ROOM_EMPHASIS = this.largeRoomEmphasis;

        ReverbConstants.DISTANCE_ATTENUATION_LINEAR = this.distanceAttenuationLinear;
        ReverbConstants.DISTANCE_ATTENUATION_QUADRATIC = this.distanceAttenuationQuadratic;
        ReverbConstants.AIR_ABSORPTION_RATE = this.airAbsorptionRate;
        ReverbConstants.MIN_AIR_ABSORPTION = this.minAirAbsorption;

        ReverbConstants.ROOM_VOLUME_MULTIPLIER = this.roomVolumeMultiplier;
        ReverbConstants.SURFACE_AREA_MULTIPLIER = this.surfaceAreaMultiplier;
        ReverbConstants.ROOM_COMPLEXITY_DIVISOR = this.roomComplexityDivisor;

        ReverbConstants.LATE_REVERB_DELAY_MULTIPLIER = this.lateReverbDelayMultiplier;
        ReverbConstants.SOUND_SPEED = this.soundSpeed;

        ReverbConstants.BASE_REVERB_GAIN = this.baseReverbGain;
        ReverbConstants.REVERB_GAIN_MULTIPLIER = this.reverbGainMultiplier;
        ReverbConstants.MAX_OVERALL_GAIN = this.maxOverallGain;
        ReverbConstants.SEND_FILTER_HF_REDUCTION = this.sendFilterHfReduction;

        ReverbConstants.MIN_DIFFUSION = this.minDiffusion;
        ReverbConstants.MAX_DIFFUSION = this.maxDiffusion;
        ReverbConstants.MIN_DENSITY = this.minDensity;
        ReverbConstants.DENSITY_ROOM_SIZE_FACTOR = this.densityRoomSizeFactor;

        ReverbConstants.DYNAMIC_ABSORPTION_LF_FACTOR = this.dynamicAbsorptionLfFactor;
        ReverbConstants.DECAY_LF_MULTIPLIER = this.decayLfMultiplier;
        ReverbConstants.OUTDOOR_HF_LEAK = this.outdoorHfLeak;

        ReverbConstants.ECHO_TIME_MULTIPLIER = this.echoTimeMultiplier;
        ReverbConstants.ECHO_DEPTH_MULTIPLIER = this.echoDepthMultiplier;
        ReverbConstants.MODULATION_TIME_MULTIPLIER = this.modulationTimeMultiplier;
        ReverbConstants.MODULATION_DEPTH_MULTIPLIER = this.modulationDepthMultiplier;

        ReverbConstants.BASE_HF_REFERENCE = this.baseHfReference;
        ReverbConstants.HF_ROOM_SIZE_FACTOR = this.hfRoomSizeFactor;
        ReverbConstants.BASE_LF_REFERENCE = this.baseLfReference;
        ReverbConstants.LF_ROOM_SIZE_FACTOR = this.lfRoomSizeFactor;

        ReverbConstants.ROOM_ROLLOFF_SIZE_FACTOR = this.roomRolloffSizeFactor;
        ReverbConstants.RT60_SABINE_CONSTANT = this.rt60SabineConstant;
        ReverbConstants.MIN_TOTAL_ABSORPTION = this.minTotalAbsorption;

        ReverbConstants.DIFFUSION_COMPLEXITY_WEIGHT = this.diffusionComplexityWeight;
        ReverbConstants.DIFFUSION_ENCLOSURE_WEIGHT = this.diffusionEnclosureWeight;
        ReverbConstants.DIFFUSION_REVERB_WEIGHT = this.diffusionReverbWeight;
    }

    /**
     * Sync current ReverbConstants values back to config (called before saving)
     */
    public void syncFromReverbConstants() {
        this.globalReverbIntensity = ReverbConstants.GLOBAL_REVERB_INTENSITY;
        this.indoorBias = ReverbConstants.INDOOR_BIAS;
        this.outdoorBias = ReverbConstants.OUTDOOR_BIAS;
        this.smallRoomEmphasis = ReverbConstants.SMALL_ROOM_EMPHASIS;
        this.largeRoomEmphasis = ReverbConstants.LARGE_ROOM_EMPHASIS;

        this.distanceAttenuationLinear = ReverbConstants.DISTANCE_ATTENUATION_LINEAR;
        this.distanceAttenuationQuadratic = ReverbConstants.DISTANCE_ATTENUATION_QUADRATIC;
        this.airAbsorptionRate = ReverbConstants.AIR_ABSORPTION_RATE;
        this.minAirAbsorption = ReverbConstants.MIN_AIR_ABSORPTION;

        this.roomVolumeMultiplier = ReverbConstants.ROOM_VOLUME_MULTIPLIER;
        this.surfaceAreaMultiplier = ReverbConstants.SURFACE_AREA_MULTIPLIER;
        this.roomComplexityDivisor = ReverbConstants.ROOM_COMPLEXITY_DIVISOR;

        this.lateReverbDelayMultiplier = ReverbConstants.LATE_REVERB_DELAY_MULTIPLIER;
        this.soundSpeed = ReverbConstants.SOUND_SPEED;

        this.baseReverbGain = ReverbConstants.BASE_REVERB_GAIN;
        this.reverbGainMultiplier = ReverbConstants.REVERB_GAIN_MULTIPLIER;
        this.maxOverallGain = ReverbConstants.MAX_OVERALL_GAIN;
        this.sendFilterHfReduction = ReverbConstants.SEND_FILTER_HF_REDUCTION;

        this.minDiffusion = ReverbConstants.MIN_DIFFUSION;
        this.maxDiffusion = ReverbConstants.MAX_DIFFUSION;
        this.minDensity = ReverbConstants.MIN_DENSITY;
        this.densityRoomSizeFactor = ReverbConstants.DENSITY_ROOM_SIZE_FACTOR;

        this.dynamicAbsorptionLfFactor = ReverbConstants.DYNAMIC_ABSORPTION_LF_FACTOR;
        this.decayLfMultiplier = ReverbConstants.DECAY_LF_MULTIPLIER;
        this.outdoorHfLeak = ReverbConstants.OUTDOOR_HF_LEAK;

        this.echoTimeMultiplier = ReverbConstants.ECHO_TIME_MULTIPLIER;
        this.echoDepthMultiplier = ReverbConstants.ECHO_DEPTH_MULTIPLIER;
        this.modulationTimeMultiplier = ReverbConstants.MODULATION_TIME_MULTIPLIER;
        this.modulationDepthMultiplier = ReverbConstants.MODULATION_DEPTH_MULTIPLIER;

        this.baseHfReference = ReverbConstants.BASE_HF_REFERENCE;
        this.hfRoomSizeFactor = ReverbConstants.HF_ROOM_SIZE_FACTOR;
        this.baseLfReference = ReverbConstants.BASE_LF_REFERENCE;
        this.lfRoomSizeFactor = ReverbConstants.LF_ROOM_SIZE_FACTOR;

        this.roomRolloffSizeFactor = ReverbConstants.ROOM_ROLLOFF_SIZE_FACTOR;
        this.rt60SabineConstant = ReverbConstants.RT60_SABINE_CONSTANT;
        this.minTotalAbsorption = ReverbConstants.MIN_TOTAL_ABSORPTION;

        this.diffusionComplexityWeight = ReverbConstants.DIFFUSION_COMPLEXITY_WEIGHT;
        this.diffusionEnclosureWeight = ReverbConstants.DIFFUSION_ENCLOSURE_WEIGHT;
        this.diffusionReverbWeight = ReverbConstants.DIFFUSION_REVERB_WEIGHT;
    }
}