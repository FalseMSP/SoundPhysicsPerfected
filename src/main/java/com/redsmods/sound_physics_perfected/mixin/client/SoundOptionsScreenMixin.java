package com.redsmods.sound_physics_perfected.mixin.client;

import com.redsmods.sound_physics_perfected.gui.SoundPhysicsSection;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21 {
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
//?} else {
/*import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.client.gui.screens.SoundOptionsScreen;
import org.spongepowered.asm.mixin.Shadow;
*///?}

/** Adds the Sound Physics quality controls under the vanilla ones on the Music &amp; Sounds screen. */
@Mixin(SoundOptionsScreen.class)
public abstract class SoundOptionsScreenMixin extends OptionsSubScreen {
    //? if <1.21 {
    /*@Shadow
    private OptionsList list;
    *///?}

    private SoundOptionsScreenMixin(Screen lastScreen, Options options, Component title) {
        super(lastScreen, options, title);
    }

    // 1.21 fills the list in OptionsSubScreen#init's call to addOptions; 1.20.1 builds it all in init.
    //? if >=1.21 {
    @Inject(method = "addOptions", at = @At("TAIL"))
    //?} else {
    /*@Inject(method = "init", at = @At("TAIL"))
    *///?}
    private void spp$addSoundPhysicsSection(CallbackInfo ci) {
        SoundPhysicsSection.addTo(this, this.list, () -> new SoundOptionsScreen(this.lastScreen, this.options));
    }
}
