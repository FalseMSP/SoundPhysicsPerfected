package com.redsmods.sound_physics_perfected.config;

import com.redsmods.sound_physics_perfected.RedsAttenuationType;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.autogen.*;
import dev.isxander.yacl3.config.v2.api.autogen.Boolean;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import dev.isxander.yacl3.platform.YACLPlatform;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

public class Config {
    public static final ConfigClassHandler<Config> CONFIG = ConfigClassHandler.createBuilder(Config.class)
            .id(YACLPlatform.rl("sound_physics_perfected", "config"))
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(YACLPlatform.getConfigDir().resolve("SoundPhysicsPerfected.json5"))
                    .setJson5(true)
                    .build())
            .build();

    public static Screen configScreen(@Nullable Screen parent) {
        return CONFIG.generateGui().generateScreen(parent);
    }

    public static Config getInstance() {
        return CONFIG.instance();
    }

    // Original settings
    @AutoGen(category = "general", group = "main")
    @IntField(min = 0, max = 2000)
    @FormatTranslation("sound_physics_perfected.config.unit.rays")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.raysCast.description")
    @SerialEntry public int raysCast = 1000;

    @AutoGen(category = "general", group = "main")
    @IntSlider(min = 0, max = 16, step = 1)
    @FormatTranslation("sound_physics_perfected.config.unit.rays")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.raysBounced.description")
    @SerialEntry public int raysBounced = 3;

    @AutoGen(category = "general", group = "main")
    @IntSlider(min = 2, max = 16, step = 1)
    @FormatTranslation("sound_physics_perfected.config.unit.rays")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.maxRayLength.description")
    @SerialEntry public int maxRayLength = 8; // chunks

    @AutoGen(category = "general", group = "main")
    @IntSlider(min = 0, max = 20, step = 1)
    @FormatTranslation("sound_physics_perfected.config.unit.ticks")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.tickRate.description")
    @SerialEntry public int tickRate = 2; // once every 2 ticks bc i want poor people's pcs to burn

    @AutoGen(category = "general", group = "main")
    @FloatSlider(min = 0, max = 100, step = 0.1f)
    @FormatTranslation("sound_physics_perfected.config.unit.multiplier")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.soundMult.description")
    @SerialEntry public float soundMult = 2; // make it just work like default Minecraft for lag helping :)

    @AutoGen(category = "general", group = "main")
    @Boolean(formatter = Boolean.Formatter.ON_OFF, colored = true)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.reverb.description")
    @SerialEntry public boolean reverb = true;

    @AutoGen(category = "general", group = "main")
    @Boolean(formatter = Boolean.Formatter.ON_OFF, colored = true)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.permeation.description")
    @SerialEntry public boolean permeation = true;

    @AutoGen(category = "general", group = "main")
    @DoubleSlider(min = 0, max = 100, step = 0.05)
    @FormatTranslation("sound_physics_perfected.config.unit.blocks")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.permeationStepSize.description")
    @SerialEntry public double permeationStepSize = 0.01;

    @AutoGen(category = "general", group = "main")
    @EnumCycler
    @CustomDescription("yacl3.config.sound_physics_perfected:config.attenuationType.description")
    @SerialEntry public RedsAttenuationType attenuationType = RedsAttenuationType.INVERSE_SQUARE;


    // === REVERB TUNING CONSTANTS ===
    // Global Controls
    @AutoGen(category = "reverb_tuning", group = "global")
    @FloatField(min = 0, max = 10000)
    @FormatTranslation("sound_physics_perfected.config.unit.multiplier")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.globalReverbIntensity.description")
    @SerialEntry public float globalReverbIntensity = 1.0f;

    @AutoGen(category = "reverb_tuning", group = "global")
    @FloatField(min = 0, max = 10000)
    @FormatTranslation("sound_physics_perfected.config.unit.multiplier")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.indoorBias.description")
    @SerialEntry public float indoorBias = 1.0f;

    @AutoGen(category = "reverb_tuning", group = "global")
    @FloatField(min = 0, max = 10000)
    @FormatTranslation("sound_physics_perfected.config.unit.multiplier")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.outdoorBias.description")
    @SerialEntry public float outdoorBias = 1.0f;

    @AutoGen(category = "reverb_tuning", group = "global")
    @FloatField(min = 0, max = 10000)
    @FormatTranslation("sound_physics_perfected.config.unit.multiplier")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.smallRoomEmphasis.description")
    @SerialEntry public float smallRoomEmphasis = 1.0f;

    @AutoGen(category = "reverb_tuning", group = "global")
    @FloatField(min = 0, max = 10000)
    @FormatTranslation("sound_physics_perfected.config.unit.multiplier")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.largeRoomEmphasis.description")
    @SerialEntry public float largeRoomEmphasis = 1.0f;

    // Distance & Attenuation
    @AutoGen(category = "reverb_tuning", group = "distance_and_attenuation")
    @FloatField(min = 0, max = 10000)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.distanceAttenuationLinear.description")
    @SerialEntry public float distanceAttenuationLinear = 0.02f;

    @AutoGen(category = "reverb_tuning", group = "distance_and_attenuation")
    @FloatField(min = 0, max = 10000)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.distanceAttenuationQuadratic.description")
    @SerialEntry public float distanceAttenuationQuadratic = 0.0001f;

    @AutoGen(category = "reverb_tuning", group = "distance_and_attenuation")
    @FloatField(min = 0, max = 10000)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.airAbsorptionRate.description")
    @SerialEntry public float airAbsorptionRate = 0.003f;

    @AutoGen(category = "reverb_tuning", group = "distance_and_attenuation")
    @FloatField(min = 0, max = 10000)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.minAirAbsorption.description")
    @SerialEntry public float minAirAbsorption = 0.2f;

    // Room Size & Volume
    @AutoGen(category = "reverb_tuning", group = "room_size_and_volume")
    @FloatField(min = 0, max = 10000)
    @FormatTranslation("sound_physics_perfected.config.unit.multiplier")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.roomVolumeMultiplier.description")
    @SerialEntry public float roomVolumeMultiplier = 0.7f;

    @AutoGen(category = "reverb_tuning", group = "room_size_and_volume")
    @FloatField(min = 0, max = 10000)
    @FormatTranslation("sound_physics_perfected.config.unit.multiplier")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.surfaceAreaMultiplier.description")
    @SerialEntry public float surfaceAreaMultiplier = 6.0f;

    @AutoGen(category = "reverb_tuning", group = "room_size_and_volume")
    @FloatField(min = 0, max = 10000)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.roomComplexityDivisor.description")
    @SerialEntry public float roomComplexityDivisor = 2.0f;

    // Reverb Timing
    @AutoGen(category = "reverb_tuning", group = "reverb_timing")
    @FloatField(min = 0, max = 10000)
    @FormatTranslation("sound_physics_perfected.config.unit.multiplier")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.lateReverbDelayMultiplier.description")
    @SerialEntry public float lateReverbDelayMultiplier = 2.0f;

    @AutoGen(category = "reverb_tuning", group = "reverb_timing")
    @FloatField(min = 0, max = 10000)
    @FormatTranslation("sound_physics_perfected.config.unit.meters_per_second")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.soundSpeed.description")
    @SerialEntry public float soundSpeed = 343.0f;

    // Gain & Strength
    @AutoGen(category = "reverb_tuning", group = "gain_and_strength")
    @FloatField(min = 0, max = 10000)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.baseReverbGain.description")
    @SerialEntry public float baseReverbGain = 0.1f;

    @AutoGen(category = "reverb_tuning", group = "gain_and_strength")
    @FloatField(min = 0, max = 10000)
    @FormatTranslation("sound_physics_perfected.config.unit.multiplier")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.reverbGainMultiplier.description")
    @SerialEntry public float reverbGainMultiplier = 0.4f;

    @AutoGen(category = "reverb_tuning", group = "gain_and_strength")
    @FloatField(min = 0, max = 10000)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.maxOverallGain.description")
    @SerialEntry public float maxOverallGain = 0.5f;

    @AutoGen(category = "reverb_tuning", group = "gain_and_strength")
    @FloatField(min = 0, max = 10000)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.sendFilterHfReduction.description")
    @SerialEntry public float sendFilterHfReduction = 0.7f;

    // Diffusion & Density
    @AutoGen(category = "reverb_tuning", group = "diffusion")
    @FloatField(min = 0, max = 1)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.minDiffusion.description")
    @SerialEntry public float minDiffusion = 0.3f;

    @AutoGen(category = "reverb_tuning", group = "diffusion")
    @FloatField(min = 0, max = 1)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.maxDiffusion.description")
    @SerialEntry public float maxDiffusion = 0.95f;

    @AutoGen(category = "reverb_tuning", group = "density")
    @FloatField(min = 0, max = 1)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.minDensity.description")
    @SerialEntry public float minDensity = 0.3f;

    @AutoGen(category = "reverb_tuning", group = "density")
    @FloatField(min = 0, max = 10000)
    @FormatTranslation("sound_physics_perfected.config.unit.multiplier")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.densityRoomSizeFactor.description")
    @SerialEntry public float densityRoomSizeFactor = 100.0f;

    // Frequency Response
    @AutoGen(category = "reverb_tuning", group = "frequency_response")
    @FloatField(min = 0, max = 10000)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.dynamicAbsorptionLfFactor.description")
    @SerialEntry public float dynamicAbsorptionLfFactor = 0.5f;

    @AutoGen(category = "reverb_tuning", group = "frequency_response")
    @FloatField(min = 0, max = 10000)
    @FormatTranslation("sound_physics_perfected.config.unit.multiplier")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.decayLfMultiplier.description")
    @SerialEntry public float decayLfMultiplier = 1.2f;

    @AutoGen(category = "reverb_tuning", group = "frequency_response")
    @FloatField(min = 0, max = 10000)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.outdoorHfLeak.description")
    @SerialEntry public float outdoorHfLeak = 0.7f;

    // Echo & Modulation
    @AutoGen(category = "reverb_tuning", group = "echo")
    @FloatField(min = 0, max = 10000)
    @FormatTranslation("sound_physics_perfected.config.unit.multiplier")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.echoTimeMultiplier.description")
    @SerialEntry public float echoTimeMultiplier = 0.002f;

    @AutoGen(category = "reverb_tuning", group = "echo")
    @FloatField(min = 0, max = 10000)
    @FormatTranslation("sound_physics_perfected.config.unit.multiplier")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.echoDepthMultiplier.description")
    @SerialEntry public float echoDepthMultiplier = 0.1f;

    @AutoGen(category = "reverb_tuning", group = "modulation")
    @FloatField(min = 0, max = 10000)
    @FormatTranslation("sound_physics_perfected.config.unit.multiplier")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.modulationTimeMultiplier.description")
    @SerialEntry public float modulationTimeMultiplier = 0.01f;

    @AutoGen(category = "reverb_tuning", group = "modulation")
    @FloatField(min = 0, max = 10000)
    @FormatTranslation("sound_physics_perfected.config.unit.multiplier")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.modulationDepthMultiplier.description")
    @SerialEntry public float modulationDepthMultiplier = 0.1f;

    // Frequency References
    @AutoGen(category = "reverb_tuning", group = "frequency_references")
    @FloatField(min = 0, max = 10000)
    @FormatTranslation("sound_physics_perfected.config.unit.hertz")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.baseHfReference.description")
    @SerialEntry public float baseHfReference = 5000.0f;

    @AutoGen(category = "reverb_tuning", group = "frequency_references")
    @FloatField(min = 0, max = 10000)
    @FormatTranslation("sound_physics_perfected.config.unit.multiplier")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.hfRoomSizeFactor.description")
    @SerialEntry public float hfRoomSizeFactor = 20.0f;

    @AutoGen(category = "reverb_tuning", group = "frequency_references")
    @FloatField(min = 0, max = 10000)
    @FormatTranslation("sound_physics_perfected.config.unit.hertz")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.baseLfReference.description")
    @SerialEntry public float baseLfReference = 250.0f;

    @AutoGen(category = "reverb_tuning", group = "frequency_references")
    @FloatField(min = 0, max = 10000)
    @FormatTranslation("sound_physics_perfected.config.unit.multiplier")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.lfRoomSizeFactor.description")
    @SerialEntry public float lfRoomSizeFactor = 2.0f;

    // Rolloff
    @AutoGen(category = "reverb_tuning", group = "rolloff")
    @FloatField(min = 0, max = 10000)
    @FormatTranslation("sound_physics_perfected.config.unit.multiplier")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.roomRolloffSizeFactor.description")
    @SerialEntry public float roomRolloffSizeFactor = 50.0f;

    // RT60 Calculation
    @AutoGen(category = "reverb_tuning", group = "rt60")
    @FloatField(min = 0, max = 10000)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.rt60SabineConstant.description")
    @SerialEntry public float rt60SabineConstant = 0.161f;

    @AutoGen(category = "reverb_tuning", group = "rt60")
    @FloatField(min = 0, max = 10000)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.minTotalAbsorption.description")
    @SerialEntry public float minTotalAbsorption = 0.1f;

    // Interpolation Weights
    @AutoGen(category = "reverb_tuning", group = "interpolation_weights")
    @FloatField(min = 0, max = 10000)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.diffusionComplexityWeight.description")
    @SerialEntry public float diffusionComplexityWeight = 1.0f;

    @AutoGen(category = "reverb_tuning", group = "interpolation_weights")
    @FloatField(min = 0, max = 10000)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.diffusionEnclosureWeight.description")
    @SerialEntry public float diffusionEnclosureWeight = 1.0f;

    @AutoGen(category = "reverb_tuning", group = "interpolation_weights")
    @FloatField(min = 0, max = 10000)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.diffusionReverbWeight.description")
    @SerialEntry public float diffusionReverbWeight = 1.0f;

}
