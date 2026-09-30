package com.redsmods.sound_physics_perfected.gui;

import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/**
 * A vanilla slider bound to one integer config setting. It moves in steps of {@code step} from {@code min} to
 * {@code max}, and writes the setting as soon as it moves.
 */
public class ConfigSlider extends AbstractSliderButton {
    private final Component caption;
    private final int min;
    private final int max;
    private final int step;
    private final IntSupplier getter;
    private final IntConsumer setter;
    private final IntFunction<Component> valueText;
    private final Runnable onChange;

    /**
     * @param valueText the value as shown after the caption ("4 chunks")
     * @param onChange  run after the slider has changed the setting
     */
    public ConfigSlider(Component caption, Component tooltip, int min, int max, int step, IntSupplier getter,
                        IntConsumer setter, IntFunction<Component> valueText, Runnable onChange) {
        super(0, 0, 150, 20, Component.empty(), 0.0);
        this.caption = caption;
        this.min = min;
        this.max = max;
        this.step = step;
        this.getter = getter;
        this.setter = setter;
        this.valueText = valueText;
        this.onChange = onChange;
        setTooltip(Tooltip.create(tooltip));
        refresh();
    }

    /** Moves the handle to the setting's current value, after something else (a preset) changed it. */
    public void refresh() {
        int setting = Mth.clamp(getter.getAsInt(), min, max);
        value = (setting - min) / (double) (max - min);
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        // The setting itself, which may lie between steps or outside the range if it was set in the config file.
        setMessage(Options.genericValueLabel(caption, valueText.apply(getter.getAsInt())));
    }

    @Override
    protected void applyValue() {
        int steps = (max - min) / step;
        int setting = min + (int) Math.round(value * steps) * step;
        if (setting != getter.getAsInt()) {
            setter.accept(setting);
            onChange.run();
        }
    }
}
