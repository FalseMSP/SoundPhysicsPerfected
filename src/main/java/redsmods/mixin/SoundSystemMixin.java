package redsmods.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(SoundSystem.class)
public class SoundSystemMixin {

    @Inject(method = "play", at = @At("HEAD"))
    private void onSoundPlayed(SoundInstance instance, CallbackInfo ci) {
        if (instance == null || instance.getSound() == null) return;

        String soundId = instance.getSound().getIdentifier().toString();
        System.out.println("Sound played: " + soundId);

        // Example: check for step sounds
        if (soundId.contains("step")) {
            System.out.println("Footstep Detected!");
        }
    }
}
