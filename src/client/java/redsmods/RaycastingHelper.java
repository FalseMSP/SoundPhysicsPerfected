package redsmods;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.List;

public class RaycastingHelper {
    private static final int RAYS_CAST = 1000;
    private static final int MAX_BOUNCES = 3;
    private static final double RAY_SEGMENT_LENGTH = 16.0;

    // PLAYER "enviornment scanning" raycasting
    public static void castSphereFromPlayer(World world, net.minecraft.entity.player.PlayerEntity player, double maxDistance, boolean drawRays) {
        try {
            // Get player's eye position as the center of the sphere
            Vec3d playerEyePos = player.getEyePos();

            // Generate ray directions in a sphere pattern
            Vec3d[] rayDirections = generateRayDirections();

            for (Vec3d direction : rayDirections) {
                // Cast ray from player in this direction
                Vec3d rayStart = playerEyePos;
                Vec3d rayEnd = rayStart.add(direction.multiply(maxDistance));

                RaycastContext raycastContext = new RaycastContext(
                        rayStart,
                        rayEnd,
                        RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE,
                        player
                );

                BlockHitResult hitResult = world.raycast(raycastContext);

                // Draw the ray if requested
                if (drawRays) {
                    drawPlayerRay(world, rayStart, rayEnd, hitResult);
                }
            }

        } catch (Exception e) {
            System.err.println("Error in sphere raycast from player: " + e.getMessage());
        }
    }

    public static void castBouncingRaysAndDetectEntities(World world, PlayerEntity player) {
        try {
            Vec3d playerEyePos = player.getEyePos();
            double maxTotalDistance = 64.0; // Max total distance after all bounces

            // Get all entities within expanded range
            Box searchBox = new Box(
                    playerEyePos.subtract(maxTotalDistance, maxTotalDistance, maxTotalDistance),
                    playerEyePos.add(maxTotalDistance, maxTotalDistance, maxTotalDistance)
            );

            List<Entity> nearbyEntities = world.getOtherEntities(player, searchBox);

            // Generate ray directions
            Vec3d[] rayDirections = RaycastingHelper.generateRayDirections();

            for (Vec3d direction : rayDirections) {
                castBouncingRay(world, player, playerEyePos, direction, nearbyEntities, maxTotalDistance);
            }

        } catch (Exception e) {
            System.err.println("Error in player bouncing ray entity detection: " + e.getMessage());
        }
    }

    public static void castBouncingRay(World world, PlayerEntity player, Vec3d startPos, Vec3d direction, List<Entity> entities, double maxTotalDistance) {
        Vec3d currentPos = startPos;
        Vec3d currentDirection = direction.normalize();
        double remainingDistance = maxTotalDistance;

        for (int bounce = 0; bounce <= MAX_BOUNCES && remainingDistance > 0; bounce++) {
            double segmentDistance = Math.min(RAY_SEGMENT_LENGTH, remainingDistance);
            Vec3d segmentEnd = currentPos.add(currentDirection.multiply(segmentDistance));

            // Cast ray for this segment
            RaycastContext raycastContext = new RaycastContext(
                    currentPos,
                    segmentEnd,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    player
            );

            BlockHitResult blockHit = world.raycast(raycastContext);

            // Determine actual end point of this segment
            Vec3d actualEnd = segmentEnd;
            boolean hitBlock = false;

            if (blockHit.getType() == HitResult.Type.BLOCK) {
                actualEnd = blockHit.getPos();
                hitBlock = true;
            }

            // Check for entity intersections along this segment
            Entity hitEntity = checkRayEntityIntersection(currentPos, currentDirection, entities, actualEnd);

            if (hitEntity != null) {
                // Draw line to the detected entity and stop this ray
                Vec3d entityCenter = hitEntity.getBoundingBox().getCenter();
                drawEntityDetectionLine(world, currentPos, entityCenter);
                drawBouncingRaySegment(world, currentPos, entityCenter, bounce);
                break;
            }

            // Draw this segment of the bouncing ray
            drawBouncingRaySegment(world, currentPos, actualEnd, bounce);

            // If we hit a block, calculate bounce
            if (hitBlock) {
                Vec3d hitPos = blockHit.getPos();
                Direction hitSide = blockHit.getSide();

                // Calculate reflected direction based on hit face
                Vec3d reflectedDirection = calculateReflection(currentDirection, hitSide);

                // Update for next bounce
                currentPos = hitPos.add(reflectedDirection.multiply(0.01)); // Small offset to avoid hitting same block
                currentDirection = reflectedDirection;

                // Reduce remaining distance
                double segmentTraveled = currentPos.distanceTo(hitPos);
                remainingDistance -= segmentTraveled;
            } else {
                // Ray didn't hit anything, we're done
                break;
            }
        }
    }

    public static Vec3d calculateReflection(Vec3d incident, Direction hitSide) {
        Vec3d normal = Vec3d.of(hitSide.getVector());

        // Reflection formula: R = I - 2(I·N)N
        // Where I is incident vector, N is normal, R is reflected vector
        double dotProduct = incident.dotProduct(normal);
        return incident.subtract(normal.multiply(2 * dotProduct));
    }

    public static Entity checkRayEntityIntersection(Vec3d rayStart, Vec3d rayDirection, List<Entity> entities, Vec3d rayEnd) {
        Entity closestEntity = null;
        double closestDistance = Double.MAX_VALUE;
        double maxDistance = rayStart.distanceTo(rayEnd);

        for (Entity entity : entities) {
            Box entityBounds = entity.getBoundingBox();

            // Calculate intersection with entity's bounding box
            double[] tValues = new double[6];
            tValues[0] = (entityBounds.minX - rayStart.x) / rayDirection.x; // Left face
            tValues[1] = (entityBounds.maxX - rayStart.x) / rayDirection.x; // Right face
            tValues[2] = (entityBounds.minY - rayStart.y) / rayDirection.y; // Bottom face
            tValues[3] = (entityBounds.maxY - rayStart.y) / rayDirection.y; // Top face
            tValues[4] = (entityBounds.minZ - rayStart.z) / rayDirection.z; // Front face
            tValues[5] = (entityBounds.maxZ - rayStart.z) / rayDirection.z; // Back face

            // Find the entry and exit points
            double tNear = Double.NEGATIVE_INFINITY;
            double tFar = Double.POSITIVE_INFINITY;

            boolean intersects = true;
            for (int i = 0; i < 3; i++) {
                double t1 = tValues[i * 2];
                double t2 = tValues[i * 2 + 1];

                if (Double.isInfinite(t1) || Double.isInfinite(t2)) {
                    continue;
                }

                if (t1 > t2) {
                    double temp = t1;
                    t1 = t2;
                    t2 = temp;
                }

                tNear = Math.max(tNear, t1);
                tFar = Math.min(tFar, t2);

                if (tNear > tFar || tFar < 0) {
                    intersects = false;
                    break;
                }
            }

            if (intersects && tNear >= 0 && tNear <= maxDistance && tNear < closestDistance) {
                closestDistance = tNear;
                closestEntity = entity;
            }
        }

        return closestEntity;
    }

    public static void drawBouncingRaySegment(World world, Vec3d start, Vec3d end, int bounceCount) {
        if (world.isClient) {
            Vec3d direction = end.subtract(start).normalize();
            double distance = start.distanceTo(end);

            // Use different colors based on bounce count
            for (double d = 0; d < distance; d += 0.4) {
                Vec3d particlePos = start.add(direction.multiply(d));

                // Color coding: first ray = white, bounces = progressively more red
                switch (bounceCount) {
                    case 0:
                        // Original ray - white/blue
                        world.addParticle(net.minecraft.particle.ParticleTypes.END_ROD,
                                particlePos.x, particlePos.y, particlePos.z, 0, 0, 0);
                        break;
                    case 1:
                        // First bounce - light red
                        world.addParticle(net.minecraft.particle.ParticleTypes.FLAME,
                                particlePos.x, particlePos.y, particlePos.z, 0, 0, 0);
                        break;
                    case 2:
                        // Second bounce - orange
                        world.addParticle(net.minecraft.particle.ParticleTypes.LAVA,
                                particlePos.x, particlePos.y, particlePos.z, 0, 0, 0);
                        break;
                    default:
                        // Third+ bounce - red smoke
                        world.addParticle(net.minecraft.particle.ParticleTypes.LARGE_SMOKE,
                                particlePos.x, particlePos.y, particlePos.z, 0, 0, 0);
                        break;
                }
            }
        }
    }

    public static void drawEntityDetectionLine(World world, Vec3d start, Vec3d end) {
        if (world.isClient) {
            Vec3d direction = end.subtract(start).normalize();
            double distance = start.distanceTo(end);

            // Draw a line with golden particles for entity detection
            for (double d = 0; d < distance; d += 0.3) {
                Vec3d particlePos = start.add(direction.multiply(d));

                // Use golden/yellow particles for entity detection
                world.addParticle(net.minecraft.particle.ParticleTypes.ENCHANT,
                        particlePos.x, particlePos.y, particlePos.z,
                        0, 0.02, 0); // Small upward velocity for visual effect
            }
        }
    }

    // OBJECT -> PLAYER raycasting
    // DEPRECATED
    // Helper method to draw rays cast from player with different visualization
    private static void drawPlayerRay(World world, Vec3d rayStart, Vec3d rayEnd, BlockHitResult hitResult) {
        // Determine the actual end point of the ray
        Vec3d actualEnd = rayEnd;
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            actualEnd = hitResult.getPos();
        }

        // Choose color - blue for rays from player
        int color = 0x0000FF; // Blue for player rays

        // Draw the ray using debug renderer
        if (world.isClient) {
            drawPlayerDebugLine(rayStart, actualEnd, color, world);
        }
    }

    private static void drawPlayerDebugLine(Vec3d start, Vec3d end, int color, World world) {
        if (world.isClient) {
            // Spawn particles along the ray path
            Vec3d direction = end.subtract(start).normalize();
            double distance = start.distanceTo(end);

            for (double d = 0; d < distance; d += 1.0) { // Larger spacing for less particle spam
                Vec3d particlePos = start.add(direction.multiply(d));

                // Use blue-ish particles for player rays
                world.addParticle(net.minecraft.particle.ParticleTypes.ENCHANT,
                        particlePos.x, particlePos.y, particlePos.z, 0, 0, 0);
            }
        }
    }

    // RAYCASTING WHATNOT
    public static boolean checkLineOfSight(World world, Vec3d soundPos, Vec3d playerPos, net.minecraft.entity.player.PlayerEntity player, boolean drawRays) {
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
    public static boolean checkLineOfSight(World world, Vec3d soundPos, Vec3d playerPos, net.minecraft.entity.player.PlayerEntity player) {
        return checkLineOfSight(world, soundPos, playerPos, player, false);
    }

    public static Vec3d[] generateRayDirections() {
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

    public static boolean rayIntersectsPlayerHitbox(Vec3d rayStart, Vec3d rayDirection, Box playerBounds, BlockHitResult blockHit) {
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
    public static void drawRay(World world, Vec3d rayStart, Vec3d rayEnd, BlockHitResult hitResult, boolean rayHitsPlayer) {
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

    public static void drawDebugLine(Vec3d start, Vec3d end, int color, World world) {
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

    public static Vec3d moveOutsideBlock(Vec3d soundPos, Vec3d direction, BlockPos blockPos) {
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
