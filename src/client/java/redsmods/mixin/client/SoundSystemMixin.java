package redsmods.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundSystem.class)
public class SoundSystemMixin {

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void onSoundPlay(SoundInstance sound, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();

        // Check if client and player exist
        if (client != null && client.player != null) {
            // Get the sound identifier
            String soundId = sound.getId().toString();

            // Get sound coordinates
            double x = sound.getX();
            double y = sound.getY();
            double z = sound.getZ();

            // Format coordinates to 2 decimal places
            String coordinates = String.format("(%.2f, %.2f, %.2f)", x, y, z);

            // Create and send chat message with coordinates
            String message = "Sound played: " + soundId + " at " + coordinates;
            client.player.sendMessage(Text.literal(message), false);

            ci.cancel();
        }
    }
}