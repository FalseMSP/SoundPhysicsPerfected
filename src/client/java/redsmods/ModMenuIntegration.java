package redsmods;

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
                .setTitle(Text.translatable("Sound Phsyics Perfected"))
                .setSavingRunnable(() -> {
                    // Save config when user clicks "Save"
                    config.save();
                });

        // Main Settings Category
        ConfigCategory general = builder.getOrCreateCategory(Text.translatable("General Configs"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        general.addEntry(entryBuilder
                .startIntSlider(Text.translatable("Rays Cast"), config.raysCast, 64, 1024)
                .setDefaultValue(512)
                .setTooltip(Text.translatable("# of Rays to cast from players (More = More Lag)"))
                .setSaveConsumer(newValue -> config.raysCast = newValue)
                .build());

        general.addEntry(entryBuilder
                .startIntSlider(Text.translatable("Ray Bounce #"), config.raysBounced, 1, 16)
                .setDefaultValue(4)
                .setTooltip(Text.translatable("Max # of times the ray will bounce before terminating (More Accurate)"))
                .setSaveConsumer(newValue -> config.raysBounced = newValue)
                .build());

        general.addEntry(entryBuilder
                .startBooleanToggle(Text.translatable("Enable Reverb"), config.reverbEnabled)
                .setDefaultValue(true)
                .setTooltip(Text.translatable("Cast Blue (Reverb Detecting) Rays and add reverb dynamically to sources"))
                .setSaveConsumer(newValue -> config.reverbEnabled = newValue)
                .build());

        general.addEntry(entryBuilder
                .startBooleanToggle(Text.translatable("Enable Permeation"), config.permeationEnabled)
                .setDefaultValue(true)
                .setTooltip(Text.translatable("Cast Red (Permeating) Rays and add muffle dynamically to permeated sources"))
                .setSaveConsumer(newValue -> config.permeationEnabled = newValue)
                .build());


        return builder.build();
    }
}