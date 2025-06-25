package redsmods.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import redsmods.SoundAttenuation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(SoundSystem.class)
public class SoundSystemMixin {
    private int speedOfSound = 343; //blocks per second

    // Track playing sounds with their positions
    private final Map<SoundInstance, Vec3d> playingSounds = new ConcurrentHashMap<>();
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 5; // Check every 5 ticks (0.25 seconds)

//    @Shadow
//    public abstract void stop(SoundInstance sound);

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void onSoundPlay(SoundInstance sound, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();

        // Check if client, player, and world exist
        if (client != null && client.player != null && client.world != null) {
            // Get the sound identifier
            String soundId = sound.getId().toString();

            // Get sound coordinates
            double soundX = sound.getX();
            double soundY = sound.getY();
            double soundZ = sound.getZ();
            Vec3d soundPos = new Vec3d(soundX, soundY, soundZ);

            // Get player position (eye position for better accuracy)
            Vec3d playerPos = client.player.getEyePos();

            // Check initial line of sight
            boolean hasLineOfSight = checkLineOfSight(client.world, soundPos, playerPos, client.player);

            // Format coordinates to 2 decimal places
            String coordinates = String.format("(%.2f, %.2f, %.2f)", soundX, soundY, soundZ);

            // Calculate distance
            double distance = playerPos.distanceTo(soundPos);
            String distanceStr = String.format("%.2fm", distance);

            // Create and send chat message with coordinates, distance, and line of sight
            String lineOfSightStr = hasLineOfSight ? "Clear" : "Blocked";
            String message = String.format("Sound: %s at %s [%s] [%s]",
                    soundId, coordinates, distanceStr, lineOfSightStr);
            client.player.sendMessage(Text.literal(message), false);

            // If initially blocked, cancel the sound
            if (!hasLineOfSight) {
                ci.cancel();
            } else {
                // If sound has line of sight, track it for continuous monitoring
                playingSounds.put(sound, soundPos);
            }
        }
    }

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        tickCounter++;

        // Only check every CHECK_INTERVAL ticks to avoid performance issues
        if (tickCounter >= CHECK_INTERVAL) {
            tickCounter = 0;
            checkPlayingSounds(ci);
        }
    }

    @Inject(method = "stop(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"))
    private void onSoundStop(SoundInstance sound, CallbackInfo ci) {
        // Remove from tracking when sound stops
        playingSounds.remove(sound);
    }

    private void checkPlayingSounds(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client == null || client.player == null || client.world == null) {
            return;
        }

        Vec3d playerPos = client.player.getEyePos();

        // Check each playing sound
        playingSounds.entrySet().removeIf(entry -> {
            SoundInstance sound = entry.getKey();
            Vec3d soundPos = entry.getValue();

            // Check if sound still has line of sight
            boolean hasLineOfSight = checkLineOfSight(client.world, soundPos, playerPos, client.player);

            if (!hasLineOfSight) {
                // Stop the sound if line of sight is lost
                ci.cancel();

                // Send notification (optional - you might want to remove this to avoid spam)
                String soundId = sound.getId().toString();
                String message = String.format("Sound blocked: %s", soundId);
                client.player.sendMessage(Text.literal(message), true); // Send as overlay to avoid chat spam

                return true; // Remove from map
            }

            return false; // Keep in map
        });
    }

    private boolean checkLineOfSight(World world, Vec3d soundPos, Vec3d playerPos, net.minecraft.entity.player.PlayerEntity player) {
        // Perform raycast between sound source and player
        RaycastContext raycastContext = new RaycastContext(
                soundPos,
                playerPos,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        );

        BlockHitResult hitResult = world.raycast(raycastContext);

        // Check if we hit something
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = hitResult.getBlockPos();
            BlockPos soundBlockPos = new BlockPos((int)Math.floor(soundPos.x), (int)Math.floor(soundPos.y), (int)Math.floor(soundPos.z));

            // If the hit block is the same as the sound source block, consider it clear
            // This handles block breaking/placing sounds where the raycast hits the block being interacted with
            return hitPos.equals(soundBlockPos);
        }

        return true; // No block hit, line of sight is clear
    }
}