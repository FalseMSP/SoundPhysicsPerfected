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
        return builder.build();
    }
}