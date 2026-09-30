package com.redsmods.sound_physics_perfected.config;

import dev.isxander.yacl3.config.v2.api.autogen.IntField;
import dev.isxander.yacl3.config.v2.api.autogen.IntSlider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QualityPresetTest {
    @Test
    void eachPresetIsRecognisedAndAnythingElseIsCustom() {
        for (QualityPreset preset : QualityPreset.PRESETS) {
            assertEquals(preset, QualityPreset.of(preset.rays, preset.bounces, preset.reverbBounces, preset.rayLength));
        }
        QualityPreset high = QualityPreset.HIGH;
        assertEquals(QualityPreset.CUSTOM, QualityPreset.of(high.rays + 8, high.bounces, high.reverbBounces, high.rayLength));
        assertEquals(QualityPreset.CUSTOM, QualityPreset.of(high.rays, high.bounces, high.reverbBounces, high.rayLength + 1));
        assertFalse(QualityPreset.PRESETS.contains(QualityPreset.CUSTOM), "Custom is not something to pick");
    }

    @Test
    void presetsGoUpInQuality() {
        for (int i = 1; i < QualityPreset.PRESETS.size(); i++) {
            QualityPreset lower = QualityPreset.PRESETS.get(i - 1);
            QualityPreset higher = QualityPreset.PRESETS.get(i);
            assertTrue(higher.rays > lower.rays, higher + " rays");
            assertTrue(higher.bounces >= lower.bounces, higher + " bounces");
            assertTrue(higher.reverbBounces > lower.reverbBounces, higher + " reverb bounces");
            assertTrue(higher.rayLength >= lower.rayLength, higher + " ray length");
        }
    }

    @Test
    void presetsStayWithinTheConfigsRanges() throws NoSuchFieldException {
        // The ranges of Config's own annotations (reading them does not initialise Config, which needs the game).
        IntField rays = Config.class.getField("raysCast").getAnnotation(IntField.class);
        IntSlider bounces = Config.class.getField("raysBounced").getAnnotation(IntSlider.class);
        IntSlider reverbBounces = Config.class.getField("reverbBounces").getAnnotation(IntSlider.class);
        IntSlider rayLength = Config.class.getField("maxRayLength").getAnnotation(IntSlider.class);
        for (QualityPreset preset : QualityPreset.PRESETS) {
            assertTrue(preset.rays >= rays.min() && preset.rays <= rays.max(), preset + " rays");
            assertEquals(0, preset.rays % 8, preset + ": the rays slider moves in steps of 8");
            assertTrue(preset.bounces >= bounces.min() && preset.bounces <= bounces.max(), preset + " bounces");
            assertTrue(preset.reverbBounces >= reverbBounces.min() && preset.reverbBounces <= reverbBounces.max(),
                    preset + " reverb bounces");
            assertTrue(preset.rayLength >= rayLength.min() && preset.rayLength <= rayLength.max(), preset + " length");
            assertTrue(preset.reverbBounces >= preset.bounces, preset + ": reverb rays go at least as far");
        }
    }
}
