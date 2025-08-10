package com.redsmods.sound_physics_perfected.fabric.client;

import com.redsmods.sound_physics_perfected.Config;
import com.redsmods.sound_physics_perfected.RaycastingHelper;
import com.redsmods.sound_physics_perfected.RedsAttenuationType;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ModMenuIntegration::createConfigScreen;
    }

    private static Screen createConfigScreen(Screen parent) {
        // Load current config values
        Config config = Config.getInstance();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("Sound Physics Perfected"))
                .setSavingRunnable(() -> {
                    // Save config when user clicks "Save"
                    config.save();
                    RaycastingHelper.getConfig();
                });

        // Main Settings Category
        ConfigCategory general = builder.getOrCreateCategory(Text.translatable("General Configs"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        general.addEntry(entryBuilder
                .startIntField(Text.translatable("Rays Cast"), config.raysCast)
                .setDefaultValue(1000)
                .setTooltip(Text.translatable("# of Rays to cast from players\nPERFORMANCE IMPACT: HIGH"))
                .setSaveConsumer(newValue -> config.raysCast = newValue)
                .build());

        general.addEntry(entryBuilder
                .startIntSlider(Text.translatable("Ray Bounce #"), config.raysBounced, 1, 16)
                .setDefaultValue(3)
                .setTooltip(Text.translatable("Max # of times the ray will bounce before terminating\n(More accurate for hearing sounds after bouncing off walls, INCREASING THIS WILL INCREASE REVERB)\nPERFORMANCE IMPACT: HIGH"))
                .setSaveConsumer(newValue -> config.raysBounced = newValue)
                .build());

        general.addEntry(entryBuilder
                .startIntSlider(Text.translatable("Max Ray Length"), config.maxRayLength,2,16)
                .setDefaultValue(8)
                .setTooltip(Text.translatable("Max Length of a singular ray, if you don't get reverb, turn this value up. if you get too much reverb, turn this down (this is in chunks)\nPERFORMANCE IMPACT: LOW"))
                .setSaveConsumer(newValue -> config.maxRayLength = newValue)
                .build());

        general.addEntry(entryBuilder
                .startIntSlider(Text.translatable("Sound Updates (Tickable)"), config.tickRate, 0, 20)
                .setDefaultValue(2)
                .setTooltip(Text.translatable("How often sounds' positions should be updated\n(0 is off)\n(1 = every tick -> 20 = every second)\nPERFORMANCE IMPACT: MEDIUM"))
                .setSaveConsumer(newValue -> config.tickRate = newValue)
                .build());

        general.addEntry(entryBuilder
                .startFloatField(Text.translatable("Max Sound Distance Mult"), config.SoundMult)
                .setDefaultValue(2f)
                .setTooltip(Text.translatable("1 is just default Minecraft sound dist\nPERFORMANCE IMPACT: HIGH"))
                .setSaveConsumer(newValue -> config.SoundMult = newValue)
                .build());

        general.addEntry(entryBuilder
                .startBooleanToggle(Text.translatable("Enable Reverb"), config.reverbEnabled)
                .setDefaultValue(true)
                .setTooltip(Text.translatable("Cast Blue (Reverb Detecting) Rays and add reverb dynamically to sources\nPERFORMANCE IMPACT: MEDIUM"))
                .setSaveConsumer(newValue -> config.reverbEnabled = newValue)
                .build());

        general.addEntry(entryBuilder
                .startBooleanToggle(Text.translatable("Enable Permeation"), config.permeationEnabled)
                .setDefaultValue(true)
                .setTooltip(Text.translatable("Cast Red (Permeating) Rays and add muffle dynamically to permeated sources\nPERFORMANCE IMPACT: MEDIUM"))
                .setSaveConsumer(newValue -> config.permeationEnabled = newValue)
                .build());

        general.addEntry(entryBuilder
                .startDoubleField(Text.translatable("Permeation Step Size: "), config.permeationStepSize)
                .setDefaultValue(0.01)
                .setTooltip(Text.translatable("Measured in Blocks, lower = more performance hurt\nPERFORMANCE IMPACT: HIGH"))
                .setSaveConsumer(newValue -> config.permeationStepSize = newValue)
                .build());

        general.addEntry(entryBuilder
                .startEnumSelector(Text.translatable("Attenuation Mode"), RedsAttenuationType.class, config.attenuationType)
                .setDefaultValue(RedsAttenuationType.INVERSE_SQUARE)
                .setTooltip(Text.translatable("Inverse Square is realism, Linear is Minecraft\nPERFORMANCE IMPACT: NONE"))
                .setSaveConsumer(newValue -> config.attenuationType = newValue)
                .build());

        // ========================================
        // REVERB TUNING CATEGORY
        // ========================================
        ConfigCategory reverbTuning = builder.getOrCreateCategory(Text.translatable("Reverb Tuning"));

        // Global Controls Section
        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Global Reverb Intensity"), config.globalReverbIntensity)
                .setDefaultValue(1.0f)
                .setTooltip(Text.translatable("Overall reverb intensity multiplier\nHigher = more intense reverb"))
                .setSaveConsumer(newValue -> config.globalReverbIntensity = newValue)
                .build());

        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Indoor Bias"), config.indoorBias)
                .setDefaultValue(1.0f)
                .setTooltip(Text.translatable("Emphasis for indoor reverb characteristics\nHigher = more pronounced indoor reverb"))
                .setSaveConsumer(newValue -> config.indoorBias = newValue)
                .build());

        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Outdoor Bias"), config.outdoorBias)
                .setDefaultValue(1.0f)
                .setTooltip(Text.translatable("Emphasis for outdoor reverb characteristics\nHigher = more pronounced outdoor reverb"))
                .setSaveConsumer(newValue -> config.outdoorBias = newValue)
                .build());

        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Small Room Emphasis"), config.smallRoomEmphasis)
                .setDefaultValue(1.0f)
                .setTooltip(Text.translatable("Emphasis for small room characteristics\nHigher = more intimate small room feel"))
                .setSaveConsumer(newValue -> config.smallRoomEmphasis = newValue)
                .build());

        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Large Room Emphasis"), config.largeRoomEmphasis)
                .setDefaultValue(1.0f)
                .setTooltip(Text.translatable("Emphasis for large room characteristics\nHigher = more spacious large room feel"))
                .setSaveConsumer(newValue -> config.largeRoomEmphasis = newValue)
                .build());

        // Distance & Physics Section
        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Distance Attenuation (Linear)"), config.distanceAttenuationLinear)
                .setDefaultValue(0.02f)
                .setTooltip(Text.translatable("Linear distance attenuation factor\nHigher = sound drops off faster with distance"))
                .setSaveConsumer(newValue -> config.distanceAttenuationLinear = newValue)
                .build());

        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Distance Attenuation (Quadratic)"), config.distanceAttenuationQuadratic)
                .setDefaultValue(0.0001f)
                .setTooltip(Text.translatable("Quadratic distance attenuation factor\nHigher = more realistic distance falloff"))
                .setSaveConsumer(newValue -> config.distanceAttenuationQuadratic = newValue)
                .build());

        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Air Absorption Rate"), config.airAbsorptionRate)
                .setDefaultValue(0.003f)
                .setTooltip(Text.translatable("How quickly air absorbs sound\nHigher = more muffled distant sounds"))
                .setSaveConsumer(newValue -> config.airAbsorptionRate = newValue)
                .build());

        // Reverb Characteristics Section
        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Base Reverb Gain"), config.baseReverbGain)
                .setDefaultValue(0.1f)
                .setTooltip(Text.translatable("Minimum reverb gain\nHigher = always some reverb present"))
                .setSaveConsumer(newValue -> config.baseReverbGain = newValue)
                .build());

        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Reverb Gain Multiplier"), config.reverbGainMultiplier)
                .setDefaultValue(0.4f)
                .setTooltip(Text.translatable("How much reverb strength affects final gain\nHigher = more responsive to reverb conditions"))
                .setSaveConsumer(newValue -> config.reverbGainMultiplier = newValue)
                .build());

        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Max Overall Gain"), config.maxOverallGain)
                .setDefaultValue(0.5f)
                .setTooltip(Text.translatable("Maximum overall reverb gain\nPrevents reverb from being too loud"))
                .setSaveConsumer(newValue -> config.maxOverallGain = newValue)
                .build());

        // Diffusion & Density Section
        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Min Diffusion"), config.minDiffusion)
                .setDefaultValue(0.3f)
                .setTooltip(Text.translatable("Minimum reverb diffusion\nLower = more directional reflections"))
                .setSaveConsumer(newValue -> config.minDiffusion = newValue)
                .build());

        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Max Diffusion"), config.maxDiffusion)
                .setDefaultValue(0.95f)
                .setTooltip(Text.translatable("Maximum reverb diffusion\nHigher = more scattered reflections"))
                .setSaveConsumer(newValue -> config.maxDiffusion = newValue)
                .build());

        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Min Density"), config.minDensity)
                .setDefaultValue(0.3f)
                .setTooltip(Text.translatable("Minimum reverb density\nLower = more sparse reflections"))
                .setSaveConsumer(newValue -> config.minDensity = newValue)
                .build());

        // Frequency Response Section
        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Send Filter HF Reduction"), config.sendFilterHfReduction)
                .setDefaultValue(0.7f)
                .setTooltip(Text.translatable("High frequency reduction for send filter\nLower = more muffled reverb"))
                .setSaveConsumer(newValue -> config.sendFilterHfReduction = newValue)
                .build());

        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Outdoor HF Leak"), config.outdoorHfLeak)
                .setDefaultValue(0.7f)
                .setTooltip(Text.translatable("High frequency leak for outdoor spaces\nHigher = less muffled outdoor sound"))
                .setSaveConsumer(newValue -> config.outdoorHfLeak = newValue)
                .build());

        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Decay LF Multiplier"), config.decayLfMultiplier)
                .setDefaultValue(1.2f)
                .setTooltip(Text.translatable("Low frequency decay ratio multiplier\nHigher = longer low frequency decay"))
                .setSaveConsumer(newValue -> config.decayLfMultiplier = newValue)
                .build());

        // Room Size & Volume Section
        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Room Volume Multiplier"), config.roomVolumeMultiplier)
                .setDefaultValue(0.7f)
                .setTooltip(Text.translatable("Room volume estimation factor\nHigher = treats rooms as larger"))
                .setSaveConsumer(newValue -> config.roomVolumeMultiplier = newValue)
                .build());

        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Room Complexity Divisor"), config.roomComplexityDivisor)
                .setDefaultValue(2.0f)
                .setTooltip(Text.translatable("Surface/volume complexity factor\nLower = treats rooms as more complex"))
                .setSaveConsumer(newValue -> config.roomComplexityDivisor = newValue)
                .build());

        // Echo & Modulation Section
        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Echo Time Multiplier"), config.echoTimeMultiplier)
                .setDefaultValue(0.002f)
                .setTooltip(Text.translatable("Echo time based on room size\nHigher = longer echo times"))
                .setSaveConsumer(newValue -> config.echoTimeMultiplier = newValue)
                .build());

        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Echo Depth Multiplier"), config.echoDepthMultiplier)
                .setDefaultValue(0.1f)
                .setTooltip(Text.translatable("Echo depth based on density\nHigher = more pronounced echoes"))
                .setSaveConsumer(newValue -> config.echoDepthMultiplier = newValue)
                .build());

        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Modulation Time Multiplier"), config.modulationTimeMultiplier)
                .setDefaultValue(0.01f)
                .setTooltip(Text.translatable("Modulation time based on room size\nHigher = slower modulation"))
                .setSaveConsumer(newValue -> config.modulationTimeMultiplier = newValue)
                .build());

        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Modulation Depth Multiplier"), config.modulationDepthMultiplier)
                .setDefaultValue(0.1f)
                .setTooltip(Text.translatable("Modulation depth based on enclosure\nHigher = more modulation"))
                .setSaveConsumer(newValue -> config.modulationDepthMultiplier = newValue)
                .build());

        // Frequency Reference Section
        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Base HF Reference"), config.baseHfReference)
                .setDefaultValue(5000.0f)
                .setTooltip(Text.translatable("Base high frequency reference (Hz)\nHigher = affects higher frequencies"))
                .setSaveConsumer(newValue -> config.baseHfReference = newValue)
                .build());

        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("HF Room Size Factor"), config.hfRoomSizeFactor)
                .setDefaultValue(20.0f)
                .setTooltip(Text.translatable("How room size affects HF reference\nHigher = more room size influence"))
                .setSaveConsumer(newValue -> config.hfRoomSizeFactor = newValue)
                .build());

        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Base LF Reference"), config.baseLfReference)
                .setDefaultValue(250.0f)
                .setTooltip(Text.translatable("Base low frequency reference (Hz)\nHigher = affects higher low frequencies"))
                .setSaveConsumer(newValue -> config.baseLfReference = newValue)
                .build());

        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("LF Room Size Factor"), config.lfRoomSizeFactor)
                .setDefaultValue(2.0f)
                .setTooltip(Text.translatable("How room size affects LF reference\nHigher = more room size influence"))
                .setSaveConsumer(newValue -> config.lfRoomSizeFactor = newValue)
                .build());

        // Advanced Physics Section
        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("RT60 Sabine Constant"), config.rt60SabineConstant)
                .setDefaultValue(0.161f)
                .setTooltip(Text.translatable("Sabine's RT60 formula constant\nPhysics constant - usually don't change"))
                .setSaveConsumer(newValue -> config.rt60SabineConstant = newValue)
                .build());

        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Late Reverb Delay Multiplier"), config.lateReverbDelayMultiplier)
                .setDefaultValue(2.0f)
                .setTooltip(Text.translatable("Late reverb delay vs early reflections\nHigher = later late reverb"))
                .setSaveConsumer(newValue -> config.lateReverbDelayMultiplier = newValue)
                .build());

        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Room Rolloff Size Factor"), config.roomRolloffSizeFactor)
                .setDefaultValue(50.0f)
                .setTooltip(Text.translatable("How room size affects rolloff\nHigher = less room size influence on rolloff"))
                .setSaveConsumer(newValue -> config.roomRolloffSizeFactor = newValue)
                .build());

        // Diffusion Weight Section
        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Diffusion Complexity Weight"), config.diffusionComplexityWeight)
                .setDefaultValue(1.0f)
                .setTooltip(Text.translatable("Weight of room complexity in diffusion calculation\nHigher = complexity affects diffusion more"))
                .setSaveConsumer(newValue -> config.diffusionComplexityWeight = newValue)
                .build());

        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Diffusion Enclosure Weight"), config.diffusionEnclosureWeight)
                .setDefaultValue(1.0f)
                .setTooltip(Text.translatable("Weight of enclosure in diffusion calculation\nHigher = enclosure affects diffusion more"))
                .setSaveConsumer(newValue -> config.diffusionEnclosureWeight = newValue)
                .build());

        reverbTuning.addEntry(entryBuilder
                .startFloatField(Text.translatable("Diffusion Reverb Weight"), config.diffusionReverbWeight)
                .setDefaultValue(1.0f)
                .setTooltip(Text.translatable("Weight of reverb strength in diffusion calculation\nHigher = reverb strength affects diffusion more"))
                .setSaveConsumer(newValue -> config.diffusionReverbWeight = newValue)
                .build());

        return builder.build();
    }
}