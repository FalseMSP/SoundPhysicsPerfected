package redsmods.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import redsmods.SoundData;
import redsmods.VolumeAdjustedSFX;

import javax.swing.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static redsmods.RaycastingHelper.checkLineOfSight;

@Mixin(SoundSystem.class)
public class SoundSystemMixin {
    private static final float BLOCKED_VOLUME_MULTIPLIER = 5f; // bigger number = quieter, its a distance factor

    private final Map<SoundInstance, SoundData> playingSounds = new ConcurrentHashMap<>();
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 5;

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"))
    private void onSoundPlay(SoundInstance sound, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();

        // Add null checks
        if (client == null || client.player == null || client.world == null || sound == null) {
            return;
        }

        try {
            // Get sound coordinates
            double soundX = sound.getX();
            double soundY = sound.getY();
            double soundZ = sound.getZ();
//            double soundVolume = sound.getVolume();
            Vec3d soundPos = new Vec3d(soundX, soundY, soundZ);

            // Get player position
            Vec3d playerPos = client.player.getEyePos();

            // Check line of sight
            boolean hasLineOfSight = checkLineOfSight(client.world, soundPos, playerPos, client.player);

            // Store sound data for tracking
//            playingSounds.put(sound, new SoundData(soundPos, hasLineOfSight, (float) soundVolume));

            // Send debug message (consider making this optional/configurable)
            String soundId = sound.getId().toString();
            String coordinates = String.format("(%.2f, %.2f, %.2f)", soundX, soundY, soundZ);
            double distance = playerPos.distanceTo(soundPos);
            String distanceStr = String.format("%.2fm", distance);
            String lineOfSightStr = hasLineOfSight ? "Clear" : "Blocked";
            String message = String.format("Sound: %s at %s [%s] [%s]",
                    soundId, coordinates, distanceStr, lineOfSightStr);
            client.player.sendMessage(Text.literal(message), false);
        } catch (Exception e) {
            // Log error but don't crash
            System.err.println("Error tracking sound: " + e.getMessage());
        }

        try {
            // Get sound coordinates
            double soundX = sound.getX();
            double soundY = sound.getY();
            double soundZ = sound.getZ();
            Vec3d soundPos = new Vec3d(soundX, soundY, soundZ);

            // Get player position
            Vec3d playerPos = client.player.getEyePos();

            // Check line of sight immediately
            boolean hasLineOfSight = checkLineOfSight(client.world, soundPos, playerPos, client.player);

            // If blocked, return modified sound
            if (!hasLineOfSight) {
                sound = new VolumeAdjustedSFX(sound, playerPos,BLOCKED_VOLUME_MULTIPLIER);
            }
        } catch (Exception e) {
            System.err.println("Error in sound modification: " + e.getMessage());
        }
    }

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        tickCounter++;

        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            checkPlayingSounds();
        }
    }

    @Inject(method = "stop(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("TAIL"))
    private void onSoundStop(SoundInstance sound, CallbackInfo ci) {
        playingSounds.remove(sound);
    }

    // Add method to clean up orphaned sounds
    @Inject(method = "stopAll()V", at = @At("HEAD"))
    private void onStopAll(CallbackInfo ci) {
        playingSounds.clear();
    }

    private void checkPlayingSounds() {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client == null || client.player == null || client.world == null) {
            return;
        }

        try {
            Vec3d playerPos = client.player.getEyePos();

            playingSounds.entrySet().removeIf(entry -> {
                try {
                    SoundInstance sound = entry.getKey();
                    SoundData soundData = entry.getValue();

                    boolean currentLineOfSight = checkLineOfSight(client.world, soundData.position, playerPos, client.player);

                    if (currentLineOfSight != soundData.hasLineOfSight) {
                        soundData.hasLineOfSight = currentLineOfSight;

                        String soundId = sound.getId().toString();
                        String status = currentLineOfSight ? "unblocked" : "blocked";
                        String message = String.format("Sound %s: %s", status, soundId);
                        client.player.sendMessage(Text.literal(message), true);
                    }

                    return false;
                } catch (Exception e) {
                    // Remove problematic entries
                    System.err.println("Error checking sound: " + e.getMessage());
                    return true;
                }
            });
        } catch (Exception e) {
            System.err.println("Error in checkPlayingSounds: " + e.getMessage());
        }
    }
}