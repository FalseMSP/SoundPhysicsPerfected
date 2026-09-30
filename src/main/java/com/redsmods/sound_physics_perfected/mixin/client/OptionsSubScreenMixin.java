package com.redsmods.sound_physics_perfected.mixin.client;

import com.redsmods.sound_physics_perfected.gui.SoundPhysicsSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21 {
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
//?} else {
/*import net.minecraft.client.gui.screens.OptionsSubScreen;
*///?}

/** Saves the Sound Physics config when the Music &amp; Sounds screen closes, if its controls changed it. */
@Mixin(OptionsSubScreen.class)
public abstract class OptionsSubScreenMixin {
    @Inject(method = "removed", at = @At("HEAD"))
    private void spp$saveSoundPhysicsConfig(CallbackInfo ci) {
        SoundPhysicsSection.saveIfChanged();
    }
}
