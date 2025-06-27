package redsmods.mixin.client;

import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.sound.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import redsmods.RedPositionedSoundInstance;
import redsmods.RedSoundInstance;
import redsmods.SoundData;
import java.util.HashSet;
import static redsmods.RaycastingHelper.soundQueue;

@Mixin(SoundSystem.class)
public class SoundSystemMixin {
    private static final float BLOCKED_VOLUME_MULTIPLIER = 5f; // bigger number = quieter, its a distance factor
    private static final double BOX_RADIUS = 1;

    private static final int MAX_SOUNDS = 100; // Limit queue size to prevent memory issues
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 5;
    private HashSet<RedSoundInstance> replayList = new HashSet<>();

    @Shadow
    private SoundManager loader;

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void onSoundPlay(SoundInstance sound, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        // Add null checks
        if (client == null || client.player == null || client.world == null || sound == null) {
            return;
        }
        try {
            if (!(sound instanceof RedPositionedSoundInstance) && sound.getAttenuationType() != SoundInstance.AttenuationType.NONE) { // !replayList.contains(redSoundData)
                // Get sound coordinates
                WeightedSoundSet weightedSoundSet = sound.getSoundSet(this.loader);
                double soundX = sound.getX();
                double soundY = sound.getY();
                double soundZ = sound.getZ();
                Vec3d soundPos = new Vec3d(soundX, soundY, soundZ);

                // Create rectangular bounding box with 0.6m radius
                Box boundingBox = new Box(
                        soundX - BOX_RADIUS, // minX
                        soundY - BOX_RADIUS, // minY
                        soundZ - BOX_RADIUS, // minZ
                        soundX + BOX_RADIUS, // maxX
                        soundY + BOX_RADIUS, // maxY
                        soundZ + BOX_RADIUS  // maxZ
                );

                // Get sound ID
                String soundId = sound.getId().toString();

                // Create sound data object
                RedSoundInstance redSoundData = new RedSoundInstance(sound);
                SoundData soundData = new SoundData(redSoundData, soundPos, boundingBox, soundId);

                // Add to queue
                soundQueue.offer(soundData);

                // Remove oldest sounds if queue is too large
                while (soundQueue.size() > MAX_SOUNDS) {
                    soundQueue.poll();
                }

                // Get player position for debug message
                Vec3d playerPos = client.player.getEyePos();
                String coordinates = String.format("(%.2f, %.2f, %.2f)", soundX, soundY, soundZ);
                double distance = playerPos.distanceTo(soundPos);
                String distanceStr = String.format("%.2fm", distance);
                String message = String.format("Sound: %s at %s [%s] - Queued (%d total)",
                        soundId, coordinates, distanceStr, soundQueue.size());
                client.player.sendMessage(Text.literal(message), true);
                ci.cancel();
            }
        } catch (Exception e) {
            // Log error but don't crash
            System.err.println("Error tracking sound: " + e.getMessage());
        }
    }

    @Inject(method = "stop(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("TAIL"))
    private void onSoundStop(SoundInstance sound, CallbackInfo ci) {
        soundQueue.remove(sound);
    }

    // Add method to clean up orphaned sounds
    @Inject(method = "stopAll()V", at = @At("HEAD"))
    private void onStopAll(CallbackInfo ci) {
        soundQueue.clear();
    }
}
