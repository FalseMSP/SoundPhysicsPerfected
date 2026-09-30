package com.redsmods.sound_physics_perfected.config;

import com.redsmods.sound_physics_perfected.ReverbHelpers.LegacyReverb;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.controller.ControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.ConfigField;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.autogen.*;
import dev.isxander.yacl3.config.v2.api.autogen.Boolean;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import dev.isxander.yacl3.platform.YACLPlatform;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    @SerialEntry public int raysCast = 200;

    @AutoGen(category = "general", group = "main")
    @IntSlider(min = 0, max = 16, step = 1)
    @FormatTranslation("sound_physics_perfected.config.unit.rays")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.raysBounced.description")
    @SerialEntry public int raysBounced = 3;

    @AutoGen(category = "general", group = "main")
    @IntSlider(min = 2, max = 16, step = 1)
    @FormatTranslation("sound_physics_perfected.config.unit.chunks")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.maxRayLength.description")
    @SerialEntry public int maxRayLength = 4; // chunks

    @AutoGen(category = "general", group = "main")
    @IntSlider(min = -1, max = 1000, step = 10)
    @FormatTranslation("sound_physics_perfected.config.unit.blocks")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.procRange.description")
    @SerialEntry public int procRange = 200; // blocks

    @AutoGen(category = "general", group = "main")
    @FloatSlider(min = 0, max = 10, step = 0.1f)
    @FormatTranslation("sound_physics_perfected.config.unit.multiplier")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.volumeMultiplier.description")
    @SerialEntry public float volumeMultiplier = 1;

    @AutoGen(category = "general", group = "main")
    @IntSlider(min = 0, max = 20, step = 1)
    @FormatTranslation("sound_physics_perfected.config.unit.ticks")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.tickRate.description")
    @SerialEntry public int tickRate = 10; // once every 2 ticks bc i want poor people's pcs to burn

    @AutoGen(category = "general", group = "permeation")
    @Boolean(formatter = Boolean.Formatter.ON_OFF, colored = true)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.permeation.description")
    @SerialEntry public boolean permeation = true;

    @AutoGen(category = "general", group = "permeation")
    @DoubleSlider(min = 0.01, max = 2, step = 0.05)
    @FormatTranslation("sound_physics_perfected.config.unit.blocks")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.permeationStepSize.description")
    @SerialEntry public double permeationStepSize = 0.0625; // changed to 1/16 of a block bc u don't *need* 0.01 precision lets be so fr

    @AutoGen(category = "general", group = "permeation")
    @Boolean(formatter = Boolean.Formatter.ON_OFF, colored = true)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.useExplosionResistance.description")
    @SerialEntry public boolean useExplosionResistance = true;

    @AutoGen(category = "general", group = "main")
    @EnumCycler
    @CustomDescription("yacl3.config.sound_physics_perfected:config.attenuationType.description")
    @SerialEntry public RedsAttenuationType attenuationType = RedsAttenuationType.VERCIDIUM_LINEAR;

    @AutoGen(category = "general", group = "permeation")
    @DoubleSlider(min = 1, max = 10, step = 0.1)
    @FormatTranslation("sound_physics_perfected.config.unit.blocks")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.maxBlocksPermeated.description")
    @SerialEntry public double maxBlocksPermeated = 3;

    @AutoGen(category = "general", group = "permeation")
    @DoubleSlider(min = 0.001, max = 1, step = 0.01)
    @FormatTranslation("sound_physics_perfected.config.unit.multiplier")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.permeationAbsorption.description")
    @SerialEntry public double permeationAbsorption = 0.4;

    @AutoGen(category = "general", group = "permeation")
    @Boolean(formatter = Boolean.Formatter.ON_OFF, colored = true)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.barrierAsAir.description")
    @SerialEntry public boolean barrierAsAir = true;

    @AutoGen(category = "general", group = "permeation")
    @Boolean(formatter = Boolean.Formatter.ON_OFF, colored = true)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.disableFXOnUI.description")
    @SerialEntry public boolean disableFXOnUI = false;

    @AutoGen(category = "general", group = "main")
    @DoubleSlider(min = 0.001, max = 1, step = 0.01)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.bounceAbsorptionMultiplier.description")
    @SerialEntry public double bounceAbsorptionMultiplier = 0.9;

    @AutoGen(category = "general", group = "main")
    @Boolean(formatter = Boolean.Formatter.ON_OFF, colored = true)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.shortcutDirectionality.description")
    @SerialEntry public boolean shortcutDirectionality = true; // bugs are now fixed.
    @AutoGen(category = "general", group = "main")
    @IntField(min = 0)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.maxSounds.description")
    @SerialEntry public int maxSounds = 100;

    @AutoGen(category = "general", group = "main")
    @Boolean(formatter = Boolean.Formatter.ON_OFF, colored = true)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.fastRaytracing.description")
    @SerialEntry public boolean fastRaytracing = true;

    @AutoGen(category = "general", group = "main")
    @IntSlider(min = 0, max = 20, step = 1)
    @FormatTranslation("sound_physics_perfected.config.unit.milliseconds")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.lowLatencyBudgetMs.description")
    @SerialEntry public int lowLatencyBudgetMs = 3;

    @AutoGen(category = "general", group = "main")
    @FloatSlider(min = 0, max = 10, step = 0.1f)
    @FormatTranslation("sound_physics_perfected.config.unit.multiplier")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.airAbsorption.description")
    @SerialEntry public float airAbsorption = 1.0f;

    public static class StringValueFactory implements ListGroup.ValueFactory<String> {
        @Override
        public String provideNewValue() {
            return ""; // Default value for new entries
        }
    }

    public static class StringControllerFactory implements ListGroup.ControllerFactory<String> {
        @Override
        public ControllerBuilder<String> createController(ListGroup listGroup, ConfigField<List<String>> configField, OptionAccess optionAccess, Option<String> option) {
            return StringControllerBuilder.create(option);
        }
    }

    @AutoGen(category = "blacklist", group = "main")
    @EnumCycler
    @CustomDescription("yacl3.config.sound_physics_perfected:config.debug.description")
    @SerialEntry public DebugType debug = DebugType.OFF;

    @AutoGen(category = "blacklist")
    @ListGroup(valueFactory = StringValueFactory.class, controllerFactory = StringControllerFactory.class)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.soundBlacklist.description")
    @SerialEntry public List<String> soundBlacklist = new ArrayList<>();

    @AutoGen(category = "blacklist")
    @ListGroup(valueFactory = StringValueFactory.class, controllerFactory = StringControllerFactory.class)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.soundTickBlacklist.description")
    @SerialEntry public List<String> soundTickBlacklist = new ArrayList<>();

    // === REVERB TUNING CONSTANTS ===
    // Global Controls

    @AutoGen(category = "reverb_tuning", group = "main")
    @Boolean(formatter = Boolean.Formatter.ON_OFF, colored = true)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.reverb.description")
    @SerialEntry public boolean reverb = true;

    @AutoGen(category = "reverb_tuning", group = "main")
    @EnumCycler
    @CustomDescription("yacl3.config.sound_physics_perfected:config.reverbTuning.description")
    @SerialEntry public DebugType reverbTuning = DebugType.OFF;

    @AutoGen(category = "reverb_tuning", group = "main")
    @EnumCycler
    @CustomDescription("yacl3.config.sound_physics_perfected:config.legacyReverb.description")
    @SerialEntry public LegacyReverb legacyReverb = LegacyReverb.MODERN;

    @AutoGen(category = "reverb_tuning", group = "main")
    @Boolean(formatter = Boolean.Formatter.ON_OFF, colored = true)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.experimentalReverb.description")
    @SerialEntry public boolean experimentalReverb = true;

    @AutoGen(category = "reverb_tuning", group = "main")
    @Boolean(formatter = Boolean.Formatter.ON_OFF, colored = true)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.directionalReverb.description")
    @SerialEntry public boolean directionalReverb = true;

    @AutoGen(category = "reverb_tuning", group = "main")
    @Boolean(formatter = Boolean.Formatter.ON_OFF, colored = true)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.reverbZones.description")
    @SerialEntry public boolean reverbZones = true;

    @AutoGen(category = "reverb_tuning", group = "main")
    @IntSlider(min = 0, max = 32, step = 1)
    @FormatTranslation("sound_physics_perfected.config.unit.rays")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.reverbBounces.description")
    @SerialEntry public int reverbBounces = 8;

    @AutoGen(category = "reverb_tuning", group = "global")
    @FloatField(min = 0, max = 10000)
    @FormatTranslation("sound_physics_perfected.config.unit.multiplier")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.globalReverbIntensity.description")
    @SerialEntry public float globalReverbIntensity = 2.0f;

    @AutoGen(category = "reverb_tuning", group = "global")
    @FloatSlider(min = 0, max = 2, step = 0.05f)
    @FormatTranslation("sound_physics_perfected.config.unit.seconds")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.reverbSmoothing.description")
    @SerialEntry public float reverbSmoothing = 0.25f;

    // Decay
    @AutoGen(category = "reverb_tuning", group = "decay")
    @FloatSlider(min = 0.1f, max = 3, step = 0.05f)
    @FormatTranslation("sound_physics_perfected.config.unit.multiplier")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.decayTimeMultiplier.description")
    @SerialEntry public float decayTimeMultiplier = 1.0f;

    @AutoGen(category = "reverb_tuning", group = "decay")
    @FloatSlider(min = 0.5f, max = 20, step = 0.5f)
    @FormatTranslation("sound_physics_perfected.config.unit.seconds")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.maxDecayTime.description")
    @SerialEntry public float maxDecayTime = 5.0f;

    // Gain & Strength
    @AutoGen(category = "reverb_tuning", group = "gain_and_strength")
    @FloatSlider(min = 0, max = 3, step = 0.05f)
    @FormatTranslation("sound_physics_perfected.config.unit.multiplier")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.reflectionsMultiplier.description")
    @SerialEntry public float reflectionsMultiplier = 1.0f;

    @AutoGen(category = "reverb_tuning", group = "gain_and_strength")
    @FloatSlider(min = 0, max = 3, step = 0.05f)
    @FormatTranslation("sound_physics_perfected.config.unit.multiplier")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.lateReverbMultiplier.description")
    @SerialEntry public float lateReverbMultiplier = 1.0f;

    @AutoGen(category = "reverb_tuning", group = "gain_and_strength")
    @FloatSlider(min = 0, max = 3, step = 0.05f)
    @FormatTranslation("sound_physics_perfected.config.unit.multiplier")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.echoMultiplier.description")
    @SerialEntry public float echoMultiplier = 1.0f;

    @AutoGen(category = "reverb_tuning", group = "gain_and_strength")
    @FloatField(min = 0, max = 10000)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.maxOverallGain.description")
    @SerialEntry public float maxOverallGain = 1f;

    // Reverb Timing
    @AutoGen(category = "reverb_tuning", group = "reverb_timing")
    @FloatField(min = 0, max = 10000)
    @FormatTranslation("sound_physics_perfected.config.unit.meters_per_second")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.soundSpeed.description")
    @SerialEntry public float soundSpeed = 343.0f;

    // Frequency References
    @AutoGen(category = "reverb_tuning", group = "frequency_references")
    @FloatField(min = 0, max = 10000)
    @FormatTranslation("sound_physics_perfected.config.unit.hertz")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.baseHfReference.description")
    @SerialEntry public float baseHfReference = 5000.0f;

    @AutoGen(category = "reverb_tuning", group = "frequency_references")
    @FloatField(min = 0, max = 10000)
    @FormatTranslation("sound_physics_perfected.config.unit.hertz")
    @CustomDescription("yacl3.config.sound_physics_perfected:config.baseLfReference.description")
    @SerialEntry public float baseLfReference = 250.0f;

    @AutoGen(category = "voicechat", group = "main")
    @Boolean(formatter = Boolean.Formatter.ON_OFF, colored = true)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.voicechatReverb.description")
    @SerialEntry public boolean voicechatReverb = true;

    @AutoGen(category = "voicechat", group = "main")
    @Boolean(formatter = Boolean.Formatter.ON_OFF, colored = true)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.voicechatReverbSelf.description")
    @SerialEntry public boolean voicechatReverbSelf = false;

    @AutoGen(category = "voicechat", group = "main")
    @Boolean(formatter = Boolean.Formatter.ON_OFF, colored = true)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.voicechatMuffle.description")
    @SerialEntry public boolean voicechatMuffle = true;

    @AutoGen(category = "voicechat", group = "main")
    @Boolean(formatter = Boolean.Formatter.ON_OFF, colored = true)
    @CustomDescription("yacl3.config.sound_physics_perfected:config.voicechatMuffleVolume.description")
    @SerialEntry public boolean voicechatMuffleVolume = true;
}