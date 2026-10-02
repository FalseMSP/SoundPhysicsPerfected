package com.redsmods.sound_physics_perfected.gui;

import com.redsmods.sound_physics_perfected.config.Config;
import com.redsmods.sound_physics_perfected.config.QualityPreset;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
//? if <1.21 {
/*import com.redsmods.sound_physics_perfected.mixin.client.OptionsListEntryAccessor;
import net.minecraft.client.OptionInstance;
*///?}

/**
 * The Sound Physics section of the Music &amp; Sounds screen: a quality preset and sliders for the ray tracing
 * settings. They write straight into {@link Config#getInstance()}, which the tracer reads at the start of every
 * trace, so a change is heard right away; the config file is saved when the screen closes.
 */
public final class SoundPhysicsSection {
    private static final String KEY = "sound_physics_perfected.options.";
    private static final int FULL_WIDTH = 310;

    //? if <1.21 {
    /*// The stand-in a 1.20.1 option row is made from, before it is given our widgets.
    private static final OptionInstance<Boolean> PLACEHOLDER = OptionInstance.createBoolean(KEY + "header", false);
    *///?}

    /** Whether the controls changed the config since it was last saved. */
    private static boolean unsaved;

    private final Screen screen;
    private final Supplier<Screen> reopen;
    private final List<ConfigSlider> qualitySliders = new ArrayList<>();
    private CycleButton<QualityPreset> qualityButton;

    private SoundPhysicsSection(Screen screen, Supplier<Screen> reopen) {
        this.screen = screen;
        this.reopen = reopen;
    }

    /**
     * Adds the section's rows to the end of the screen's option list.
     *
     * @param reopen makes a fresh copy of the screen, to return to from the full config screen
     */
    public static void addTo(Screen screen, OptionsList list, Supplier<Screen> reopen) {
        new SoundPhysicsSection(screen, reopen).build(list);
    }

    /** Writes the config file if the controls changed anything; called when an options screen closes. */
    public static void saveIfChanged() {
        if (unsaved) {
            unsaved = false;
            Config.CONFIG.save();
        }
    }

    private void build(OptionsList list) {
        // Custom is not one of the values to cycle through; a click on Custom goes round to Low.
        //? if >=1.21.11 {
        /*qualityButton = CycleButton.builder(SoundPhysicsSection::presetName, QualityPreset.of(Config.getInstance()))
                .withValues(QualityPreset.PRESETS)
                .withTooltip(SoundPhysicsSection::presetTooltip)
        *///?} else {
        qualityButton = CycleButton.builder(SoundPhysicsSection::presetName)
                .withValues(QualityPreset.PRESETS)
                .withTooltip(SoundPhysicsSection::presetTooltip)
                .withInitialValue(QualityPreset.ULTRA)
                .withInitialValue(QualityPreset.of(Config.getInstance()))
        //?}
                .create(0, 0, FULL_WIDTH, 20, Component.translatable(KEY + "quality"),
                        (button, preset) -> applyPreset(preset));

        ConfigSlider rays = qualitySlider("rays", 0, 4096, 8,
                c -> c.raysCast, (c, v) -> c.raysCast = v, SoundPhysicsSection::number);
        ConfigSlider bounces = qualitySlider("bounces", 0, 16, 1,
                c -> c.raysBounced, (c, v) -> c.raysBounced = v, SoundPhysicsSection::number);
        ConfigSlider reverbBounces = qualitySlider("reverb_bounces", 0, 32, 1,
                c -> c.reverbBounces, (c, v) -> c.reverbBounces = v, SoundPhysicsSection::number);
        ConfigSlider rayLength = qualitySlider("ray_length", 2, 16, 1,
                c -> c.maxRayLength, (c, v) -> c.maxRayLength = v,
                v -> Component.translatable("sound_physics_perfected.config.unit.chunks", v));
        ConfigSlider muffling = slider("muffling", 0, 300, 10,
                c -> c.muffleStrength, (c, v) -> c.muffleStrength = v,
                v -> Component.translatable("sound_physics_perfected.config.unit.percent", v),
                () -> unsaved = true);
        ConfigSlider doppler = slider("doppler", 0, 500, 10,
                c -> c.dopplerStrength, (c, v) -> c.dopplerStrength = v,
                v -> v == 0 ? Component.translatable("options.off")
                        : Component.translatable("sound_physics_perfected.config.unit.percent", v),
                () -> unsaved = true);
        Button more = Button.builder(Component.translatable(KEY + "more"),
                        button -> Minecraft.getInstance().setScreen(Config.configScreen(reopen.get())))
                .width(FULL_WIDTH)
                .tooltip(Tooltip.create(Component.translatable(KEY + "more.tooltip")))
                .build();

        addRow(list, new SectionHeader(FULL_WIDTH, Component.translatable(KEY + "header")), null);
        addRow(list, qualityButton, null);
        addRow(list, rays, bounces);
        addRow(list, reverbBounces, rayLength);
        addRow(list, muffling, doppler);
        addRow(list, more, null);
    }

    /** A slider for one of the settings the presets set. */
    private ConfigSlider qualitySlider(String name, int min, int max, int step, ToIntFunction<Config> getter,
                                       ObjIntConsumer<Config> setter, IntFunction<Component> valueText) {
        ConfigSlider slider = slider(name, min, max, step, getter, setter, valueText, () -> {
            unsaved = true;
            qualityButton.setValue(QualityPreset.of(Config.getInstance()));
        });
        qualitySliders.add(slider);
        return slider;
    }

    private static ConfigSlider slider(String name, int min, int max, int step, ToIntFunction<Config> getter,
                                       ObjIntConsumer<Config> setter, IntFunction<Component> valueText,
                                       Runnable onChange) {
        // Always the live config instance, never one captured when the screen was opened.
        return new ConfigSlider(Component.translatable(KEY + name), Component.translatable(KEY + name + ".tooltip"),
                min, max, step, () -> getter.applyAsInt(Config.getInstance()),
                v -> setter.accept(Config.getInstance(), v), valueText, onChange);
    }

    private void applyPreset(QualityPreset preset) {
        preset.applyTo(Config.getInstance());
        unsaved = true;
        for (ConfigSlider slider : qualitySliders) {
            slider.refresh();
        }
    }

    private static Component number(int value) {
        return Component.literal(Integer.toString(value));
    }

    private static Component presetName(QualityPreset preset) {
        return Component.translatable(KEY + "quality." + preset.id());
    }

    private static Tooltip presetTooltip(QualityPreset preset) {
        MutableComponent text = Component.translatable(KEY + "quality." + preset.id() + ".tooltip");
        if (preset != QualityPreset.CUSTOM) {
            text.append("\n\n").append(Component.translatable(KEY + "quality.values", preset.rays, preset.bounces,
                    preset.reverbBounces, preset.rayLength).withStyle(ChatFormatting.GRAY));
        }
        return Tooltip.create(text);
    }

    /** Adds a row of one full-width widget, or of two half-width ones. */
    private void addRow(OptionsList list, AbstractWidget left, @Nullable AbstractWidget right) {
        //? if >=1.21 {
        list.addSmall(left, right);
        //?} else {
        /*// Rows here can only be made from OptionInstances, and keep the x their widgets were created at.
        int x = screen.width / 2 - 155;
        left.setX(x);
        if (right != null) {
            right.setX(x + 160);
        }
        Object row = list.children().get(list.addBig(PLACEHOLDER));
        ((OptionsListEntryAccessor) row).spp$setChildren(right == null ? List.of(left) : List.of(left, right));
        *///?}
    }
}
