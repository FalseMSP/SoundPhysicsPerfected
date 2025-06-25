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

@Mixin(SoundSystem.class)
public class SoundSystemMixin {
    private static final float BLOCKED_VOLUME_MULTIPLIER = 5f; // bigger number = quieter, its a distance factor

    private final Map<SoundInstance, SoundData> playingSounds = new ConcurrentHashMap<>();
    private int tickCounter = 0;
    private static final int CHECK_INTERVAL = 5;
    private static final int RAYS_CAST = 10000;

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

    // RAYCASTING WHATNOT

    private boolean checkLineOfSight(World world, Vec3d soundPos, Vec3d playerPos, net.minecraft.entity.player.PlayerEntity player, boolean drawRays) {
        try {
            // Get player's bounding box
            Box playerBounds = player.getBoundingBox();

            // Define ray directions - using a sphere-like distribution
            Vec3d[] rayDirections = generateRayDirections();

            // Get the starting block position
            BlockPos startingBlockPos = new BlockPos((int)Math.floor(soundPos.x), (int)Math.floor(soundPos.y), (int)Math.floor(soundPos.z));
            boolean startingInBlock = world.getBlockState(startingBlockPos).isSolidBlock(world, startingBlockPos);

            boolean foundClearPath = false;

            for (Vec3d direction : rayDirections) {
                Vec3d rayStart = soundPos;

                // If starting inside a block, move the ray start to just outside the block
                if (startingInBlock) {
                    rayStart = moveOutsideBlock(soundPos, direction, startingBlockPos);
                }

                // Cast ray in this direction
                Vec3d rayEnd = rayStart.add(direction.multiply(64)); // Max distance of 64 blocks

                RaycastContext raycastContext = new RaycastContext(
                        rayStart,
                        rayEnd,
                        RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE,
                        player
                );

                BlockHitResult hitResult = world.raycast(raycastContext);

                // Check if ray intersects with player hitbox before hitting any blocks
                boolean rayHitsPlayer = rayIntersectsPlayerHitbox(rayStart, direction, playerBounds, hitResult);

                if (rayHitsPlayer) {
                    foundClearPath = true;
                }

                // Draw the ray if requested
                if (drawRays) {
                    drawRay(world, rayStart, rayEnd, hitResult, rayHitsPlayer);
                }
            }

            return foundClearPath;

        } catch (Exception e) {
            System.err.println("Error in line of sight check: " + e.getMessage());
            return true; // Default to clear line of sight on error
        }
    }

    // Overloaded method for backwards compatibility
    private boolean checkLineOfSight(World world, Vec3d soundPos, Vec3d playerPos, net.minecraft.entity.player.PlayerEntity player) {
        return checkLineOfSight(world, soundPos, playerPos, player, false);
    }

    private Vec3d[] generateRayDirections() {
        // Generate directions in a roughly spherical pattern
        // Using fibonacci sphere for even distribution
        int numRays = RAYS_CAST; // Good balance between accuracy and performance
        Vec3d[] directions = new Vec3d[numRays];

        double goldenRatio = (1 + Math.sqrt(5)) / 2;

        for (int i = 0; i < numRays; i++) {
            double theta = 2 * Math.PI * i / goldenRatio;
            double phi = Math.acos(1 - 2.0 * (i + 0.5) / numRays);

            double x = Math.sin(phi) * Math.cos(theta);
            double y = Math.cos(phi);
            double z = Math.sin(phi) * Math.sin(theta);

            directions[i] = new Vec3d(x, y, z);
        }

        return directions;
    }

    private boolean rayIntersectsPlayerHitbox(Vec3d rayStart, Vec3d rayDirection, Box playerBounds, BlockHitResult blockHit) {
        // Calculate intersection with player's bounding box
        double[] tValues = new double[6];

        // Check intersection with each face of the bounding box
        tValues[0] = (playerBounds.minX - rayStart.x) / rayDirection.x; // Left face
        tValues[1] = (playerBounds.maxX - rayStart.x) / rayDirection.x; // Right face
        tValues[2] = (playerBounds.minY - rayStart.y) / rayDirection.y; // Bottom face
        tValues[3] = (playerBounds.maxY - rayStart.y) / rayDirection.y; // Top face
        tValues[4] = (playerBounds.minZ - rayStart.z) / rayDirection.z; // Front face
        tValues[5] = (playerBounds.maxZ - rayStart.z) / rayDirection.z; // Back face

        // Find the entry and exit points
        double tNear = Double.NEGATIVE_INFINITY;
        double tFar = Double.POSITIVE_INFINITY;

        for (int i = 0; i < 3; i++) {
            double t1 = tValues[i * 2];
            double t2 = tValues[i * 2 + 1];

            if (t1 > t2) {
                double temp = t1;
                t1 = t2;
                t2 = temp;
            }

            tNear = Math.max(tNear, t1);
            tFar = Math.min(tFar, t2);

            if (tNear > tFar || tFar < 0) {
                return false; // No intersection
            }
        }

        // Check if intersection occurs before hitting a block
        if (tNear >= 0) {
            if (blockHit.getType() == HitResult.Type.MISS) {
                return true; // Ray hits player and no blocks in the way
            }

            // Calculate distance to block hit
            Vec3d blockHitPos = blockHit.getPos();
            double distanceToBlock = rayStart.distanceTo(blockHitPos);
            double distanceToPlayer = tNear;

            // Player is closer than the block hit
            return distanceToPlayer < distanceToBlock;
        }
        return false;
    }
        private void drawRay(World world, Vec3d rayStart, Vec3d rayEnd, BlockHitResult hitResult, boolean rayHitsPlayer) {
            // Determine the actual end point of the ray
            Vec3d actualEnd = rayEnd;
            if (hitResult.getType() == HitResult.Type.BLOCK) {
                actualEnd = hitResult.getPos();
            }

            // Choose color based on result
            int color;
            if (rayHitsPlayer) {
                color = 0x00FF00; // Green for rays that hit the player
            } else if (hitResult.getType() == HitResult.Type.BLOCK) {
                color = 0xFF0000; // Red for rays that hit blocks
            } else {
                color = 0xFFFFFF; // White for rays that hit nothing
            }

            // Draw the ray using debug renderer
            if (world.isClient) {
                drawDebugLine(rayStart, actualEnd, color,world);
            }
        }

        private void drawDebugLine(Vec3d start, Vec3d end, int color, World world) {
            if (world.isClient) {
                // Spawn particles along the ray path
                Vec3d direction = end.subtract(start).normalize();
                double distance = start.distanceTo(end);

                for (double d = 0; d < distance; d += 0.5) {
                    Vec3d particlePos = start.add(direction.multiply(d));

                    // Spawn different particle types based on color
                    if (color == 0x00FF00) {
                        // Green particles for successful rays
                        world.addParticle(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER,
                                particlePos.x, particlePos.y, particlePos.z, 0, 0, 0);
                    } else if (color == 0xFF0000) {
                        // Red particles for blocked rays
                        world.addParticle(net.minecraft.particle.ParticleTypes.ANGRY_VILLAGER,
                                particlePos.x, particlePos.y, particlePos.z, 0, 0, 0);
                    } else {
                        // White particles for unobstructed rays
                        world.addParticle(net.minecraft.particle.ParticleTypes.END_ROD,
                                particlePos.x, particlePos.y, particlePos.z, 0, 0, 0);
                    }
                }
            }

        }

        private Vec3d moveOutsideBlock(Vec3d soundPos, Vec3d direction, BlockPos blockPos) {
            // Calculate the block bounds
            double minX = blockPos.getX();
            double maxX = blockPos.getX() + 1.0;
            double minY = blockPos.getY();
            double maxY = blockPos.getY() + 1.0;
            double minZ = blockPos.getZ();
            double maxZ = blockPos.getZ() + 1.0;

            // Find where the ray exits the block
            double[] tValues = new double[6];
            tValues[0] = (minX - soundPos.x) / direction.x; // Left face
            tValues[1] = (maxX - soundPos.x) / direction.x; // Right face
            tValues[2] = (minY - soundPos.y) / direction.y; // Bottom face
            tValues[3] = (maxY - soundPos.y) / direction.y; // Top face
            tValues[4] = (minZ - soundPos.z) / direction.z; // Front face
            tValues[5] = (maxZ - soundPos.z) / direction.z; // Back face

            // Find the closest positive t value (exit point)
            double tExit = Double.POSITIVE_INFINITY;
            for (double t : tValues) {
                if (t > 0.001 && t < tExit) { // Small epsilon to avoid floating point issues
                    tExit = t;
                }
            }

            // Move to exit point plus small offset
            if (tExit != Double.POSITIVE_INFINITY) {
                return soundPos.add(direction.multiply(tExit + 0.01));
            }

            // Fallback: just move a bit in the direction
            return soundPos.add(direction.multiply(0.6));
        }

}