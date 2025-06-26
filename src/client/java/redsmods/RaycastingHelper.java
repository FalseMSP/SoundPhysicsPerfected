package redsmods;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import redsmods.mixin.client.SoundSystemMixin;

import java.util.*;

public class RaycastingHelper {
    private static final int RAYS_CAST = 64000; // 64000 is production number
    private static final int MAX_BOUNCES = 7;
    private static final double RAY_SEGMENT_LENGTH = 64.0;
    private static java.util.Map<SoundData, Integer> entityRayHitCounts = new java.util.HashMap<>();
    public static final Queue<SoundData> soundQueue = new LinkedList<>();

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

    public static void castBouncingRaysAndDetectSFX(World world, PlayerEntity player) {
        try {
            Vec3d playerEyePos = player.getEyePos();
            double maxTotalDistance = 64.0; // Max total distance after all bounces

            // Clear previous ray hit counts
            entityRayHitCounts.clear();

            // Get all entities within expanded range
            Box searchBox = new Box(
                    playerEyePos.subtract(maxTotalDistance, maxTotalDistance, maxTotalDistance),
                    playerEyePos.add(maxTotalDistance, maxTotalDistance, maxTotalDistance)
            );

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getSoundManager() == null) {
                return;
            }

            Queue<SoundData> nearbyEntities = new LinkedList<>(soundQueue);

            // Generate ray directions
            Vec3d[] rayDirections = RaycastingHelper.generateRayDirections();
            Map<SoundData, List<RayHitData>> rayHitsByEntity = new HashMap<>();

            for (Vec3d direction : rayDirections) {
                RaycastResult res = castBouncingRay(world, player, playerEyePos, direction, nearbyEntities, maxTotalDistance);
                if (res.hitEntity != null) {
                    // Calculate weight using inverse square law (1/d²)
                    // Add small epsilon to prevent division by zero
                    double distance = Math.max(res.totalDistance, 0.1);
                    double weight = 1.0 / (distance * distance);

                    RayHitData hitData = new RayHitData(res, direction, weight);

                    // Group by entity
                    rayHitsByEntity.computeIfAbsent(res.hitEntity, k -> new ArrayList<>()).add(hitData);
                }
            }
            processAndPlayAveragedSounds(world,player,playerEyePos,new ArrayList<>(Arrays.asList(rayDirections)),nearbyEntities,maxTotalDistance,client);
            // Display ray hit counts for detected sfx
            displayEntityRayHitCounts(world, player);

            while (!soundQueue.isEmpty()) { // iterate through soundQueue
                soundQueue.poll();
            }
        } catch (Exception e) {
            System.err.println("Error in player bouncing ray entity detection: " + e.getMessage());
        }
    }

    public static void processAndPlayAveragedSounds(World world, PlayerEntity player, Vec3d playerEyePos,
                                                    List<Vec3d> rayDirections, Queue<SoundData> nearbyEntities,
                                                    double maxTotalDistance, MinecraftClient client) {

        // Process rays and get averaged results
        Map<SoundData, AveragedSoundData> averagedResults = processRaysWithAveraging(
                world, player, playerEyePos, rayDirections, nearbyEntities, maxTotalDistance);

        if (averagedResults.isEmpty()) {
//            System.out.println("No sounds detected by raycasting");
            return;
        }

        System.out.println("Processing " + averagedResults.size() + " detected sounds:");

        // Option 1: Play all sounds
        // playAllAveragedSounds(client, averagedResults, playerEyePos);

        // Option 2: Play only high-confidence sounds
        playFilteredAveragedSounds(client, averagedResults, playerEyePos, 0.01, 2); // min weight 0.01, min 2 rays

        // Option 3: Play only the most significant sound
        // playMostSignificantSound(client, averagedResults, playerEyePos);

        // Option 4: Play with volume/pitch adjustments based on confidence
    /*
    for (AveragedSoundData avgData : averagedResults.values()) {
        if (avgData.totalWeight > 0.01 && avgData.rayCount >= 2) {
            playAveragedSoundWithAdjustments(client, avgData, playerEyePos, 0.8f, 1.0f);
        }
    }
    */
    }

    // Method to play a single sound at averaged position
    public static void playSoundAtAveragedPosition(MinecraftClient client, AveragedSoundData avgData, Vec3d playerPos) {
        if (client == null || client.world == null || avgData == null) {
            return;
        }

        try {
            // Calculate the target position based on average direction and distance
            Vec3d targetPosition = playerPos.add(avgData.averageDirection.multiply(avgData.averageDistance));

            // Get the original sound identifier
            RedSoundInstance originalSound = avgData.soundEntity.sound;
            Identifier soundId = originalSound.getId();

            // Create a new positioned sound instance at the averaged location
            PositionedSoundInstance newSound = new RedPositionedSoundInstance(
                    soundId,                                    // Sound identifier
                    originalSound.getCategory(),                // Sound category
                    originalSound.getVolume(),                  // Volume
                    originalSound.getPitch(),                   // Pitch
                    SoundInstance.createRandom(),                           // Random instance
                    originalSound.isRepeatable(),
                    originalSound.getRepeatDelay(),              // Repeat delay
                    originalSound.getAttenuationType(),
                    (float) targetPosition.x,                   // X position
                    (float) targetPosition.y,                   // Y position
                    (float) targetPosition.z,                   // Z position
                    originalSound.isRelative()                  // Relative positioning
            );

            // Play the sound
            client.getSoundManager().play(newSound);

            // Debug output
            System.out.println("Playing averaged sound: " + soundId.toString());
            System.out.println("  Original position: " + avgData.soundEntity.position.toString());
            System.out.println("  Averaged position: " + targetPosition.toString());
            System.out.println("  Distance from player: " + String.format("%.2f", avgData.averageDistance));

        } catch (Exception e) {
            System.err.println("Error playing averaged sound: " + e.getMessage());
        }
    }

    // Method to play all sounds from averaged results
    public static void playAllAveragedSounds(MinecraftClient client, Map<SoundData, AveragedSoundData> averagedResults, Vec3d playerPos) {
        for (AveragedSoundData avgData : averagedResults.values()) {
            playSoundAtAveragedPosition(client, avgData, playerPos);
        }
    }

    // Method to play only sounds above a certain confidence threshold
    public static void playFilteredAveragedSounds(MinecraftClient client, Map<SoundData, AveragedSoundData> averagedResults,
                                                  Vec3d playerPos, double minWeight, int minRayCount) {
        List<AveragedSoundData> filteredSounds = getSoundsAboveThreshold(averagedResults, minWeight, minRayCount);

        for (AveragedSoundData avgData : filteredSounds) {
            playSoundAtAveragedPosition(client, avgData, playerPos);
        }

        System.out.println("Played " + filteredSounds.size() + " filtered averaged sounds");
    }

    // Utility method to get sounds above a certain confidence threshold
    public static List<AveragedSoundData> getSoundsAboveThreshold(Map<SoundData, AveragedSoundData> averagedResults,
                                                                  double minWeight, int minRayCount) {
        List<AveragedSoundData> filteredSounds = new ArrayList<>();

        for (AveragedSoundData avgData : averagedResults.values()) {
            if (avgData.totalWeight >= minWeight && avgData.rayCount >= minRayCount) {
                filteredSounds.add(avgData);
            }
        }

        // Sort by weight descending (most significant first)
        filteredSounds.sort((a, b) -> Double.compare(b.totalWeight, a.totalWeight));

        return filteredSounds;
    }

    // Advanced method with volume and pitch adjustment based on confidence
    public static void playAveragedSoundWithAdjustments(MinecraftClient client, AveragedSoundData avgData, Vec3d playerPos,
                                                        float volumeMultiplier, float pitchMultiplier) {
        if (client == null || client.world == null || avgData == null) {
            return;
        }

        try {
            // Calculate the target position
            Vec3d targetPosition = playerPos.add(avgData.averageDirection.multiply(avgData.averageDistance));

            // Get original sound properties
            RedSoundInstance originalSound = avgData.soundEntity.sound;
            Identifier soundId = originalSound.getId();

            // Calculate adjusted volume based on ray count and weight (confidence-based)
            float baseVolume = originalSound.getVolume();
            float confidenceMultiplier = (float) Math.min(1.0, Math.log10(avgData.totalWeight + 1.0));
            float adjustedVolume = baseVolume * volumeMultiplier * confidenceMultiplier;

            // Calculate adjusted pitch
            float basePitch = originalSound.getPitch();
            float adjustedPitch = basePitch * pitchMultiplier;

            // Create positioned sound with adjustments
            PositionedSoundInstance newSound = new PositionedSoundInstance(
                    soundId,                                    // Sound identifier
                    originalSound.getCategory(),                // Sound category
                    Math.max(0.0f, Math.min(1.0f, adjustedVolume)),  // Clamp volume between 0-1
                    Math.max(0.5f, Math.min(2.0f, adjustedPitch)),   // Clamp pitch between 0.5-2.0
                    SoundInstance.createRandom(),                           // Random instance
                    originalSound.isRepeatable(),
                    originalSound.getRepeatDelay(),              // Repeat delay
                    originalSound.getAttenuationType(),
                    (float) targetPosition.x,                   // X position
                    (float) targetPosition.y,                   // Y position
                    (float) targetPosition.z,                   // Z position
                    originalSound.isRelative()                  // Relative positioning
            );

            client.getSoundManager().play(newSound);

            // Debug output
            System.out.println("Playing adjusted averaged sound: " + soundId.toString());
            System.out.println("  Volume: " + String.format("%.3f", adjustedVolume) + " (original: " + String.format("%.3f", baseVolume) + ")");
            System.out.println("  Pitch: " + String.format("%.3f", adjustedPitch) + " (original: " + String.format("%.3f", basePitch) + ")");
            System.out.println("  Confidence: " + String.format("%.3f", confidenceMultiplier));

        } catch (Exception e) {
            System.err.println("Error playing adjusted averaged sound: " + e.getMessage());
        }
    }

    // Main method to process rays and calculate averages
    public static Map<SoundData, AveragedSoundData> processRaysWithAveraging(World world, PlayerEntity player,
                                                                             Vec3d playerEyePos, List<Vec3d> rayDirections,
                                                                             Queue<SoundData> nearbyEntities, double maxTotalDistance) {

        // Map to store ray hits grouped by sound entity
        Map<SoundData, List<RayHitData>> rayHitsByEntity = new HashMap<>();

        // Cast all rays and collect hit data
        for (Vec3d direction : rayDirections) {
            RaycastResult res = castBouncingRay(world, player, playerEyePos, direction, nearbyEntities, maxTotalDistance);
            if (res.hitEntity != null) {
                // Calculate weight using inverse square law (1/d²)
                // Add small epsilon to prevent division by zero
                double distance = Math.max(res.totalDistance, 0.1);
                double weight = 1.0 / (distance * distance);

                RayHitData hitData = new RayHitData(res, direction, weight);

                // Group by entity
                rayHitsByEntity.computeIfAbsent(res.hitEntity, k -> new ArrayList<>()).add(hitData);
            }
        }

        // Calculate averages for each entity
        Map<SoundData, AveragedSoundData> averagedResults = new HashMap<>();

        for (Map.Entry<SoundData, List<RayHitData>> entry : rayHitsByEntity.entrySet()) {
            SoundData entity = entry.getKey();
            List<RayHitData> rayHits = entry.getValue();

            AveragedSoundData averagedData = calculateWeightedAverages(entity, rayHits);
            averagedResults.put(entity, averagedData);
        }

        return averagedResults;
    }

    // Helper method to calculate weighted averages for a single entity
    private static AveragedSoundData calculateWeightedAverages(SoundData entity, List<RayHitData> rayHits) {
        double totalWeight = 0.0;
        double weightedDistanceSum = 0.0;
        Vec3d weightedDirectionSum = Vec3d.ZERO;

        // Calculate weighted sums
        for (RayHitData rayHit : rayHits) {
            double weight = rayHit.weight;
            totalWeight += weight;

            // Weighted distance
            weightedDistanceSum += rayHit.rayResult.totalDistance * weight;

            // Weighted direction (using initial ray direction)
            Vec3d weightedDirection = rayHit.rayResult.initialDirection.multiply(weight);
            weightedDirectionSum = weightedDirectionSum.add(weightedDirection);
        }

        // Calculate averages
        double averageDistance = weightedDistanceSum / totalWeight;
        Vec3d averageDirection = weightedDirectionSum.multiply(1.0 / totalWeight).normalize();

        return new AveragedSoundData(entity, averageDirection, averageDistance,
                totalWeight, rayHits.size(), rayHits);
    }

    public static RaycastResult castBouncingRay(World world, PlayerEntity player, Vec3d startPos, Vec3d direction, Queue<SoundData> entities, double maxTotalDistance) {
        Vec3d currentPos = startPos;
        Vec3d currentDirection = direction.normalize();
        Vec3d initialDirection = currentDirection.normalize(); // Store the initial direction as unit vector
        double remainingDistance = maxTotalDistance;
        double totalDistanceTraveled = 0.0;

        SoundData hitEntity = null;
        boolean rayCompleted = false;

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

            // Calculate distance traveled in this segment
            double segmentTraveled = currentPos.distanceTo(actualEnd);
            totalDistanceTraveled += segmentTraveled;

            // Check for entity intersections along this segment
            SoundData entityHit = checkRayEntityIntersection(currentPos, currentDirection, entities, actualEnd);

            if (entityHit != null) {
                // Calculate precise distance to entity
                Vec3d entityCenter = entityHit.boundingBox.getCenter();
                double distanceToEntity = currentPos.distanceTo(entityCenter);
                totalDistanceTraveled = totalDistanceTraveled - segmentTraveled + distanceToEntity;

                // Increment ray hit count for this entity
                entityRayHitCounts.put(entityHit, entityRayHitCounts.getOrDefault(entityHit, 0) + 1);

                // Draw line to the detected entity and stop this ray
                drawEntityDetectionLine(world, currentPos, entityCenter);
                drawBouncingRaySegment(world, currentPos, entityCenter, bounce);

                hitEntity = entityHit;
                rayCompleted = true;
                break;
            }

            // Draw this segment
//            drawBouncingRaySegment(world, currentPos, actualEnd, bounce);

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
                remainingDistance -= segmentTraveled;
            } else {
                // Ray reached maximum segment length without hitting anything
                rayCompleted = true;
                break;
            }
        }

        // Return comprehensive result
        return new RaycastResult(totalDistanceTraveled, initialDirection, hitEntity, currentPos, rayCompleted);
    }

    // Helper method to get just the total distance (for backward compatibility)
    public static double getTotalRayDistance(World world, PlayerEntity player, Vec3d startPos, Vec3d direction, Queue<SoundData> entities, double maxTotalDistance) {
        RaycastResult result = castBouncingRay(world, player, startPos, direction, entities, maxTotalDistance);
        return result.totalDistance;
    }

    // Helper method to get just the initial direction (for backward compatibility)
    public static Vec3d getInitialRayDirection(Vec3d direction) {
        return direction.normalize();
    }

    public static Vec3d calculateReflection(Vec3d incident, Direction hitSide) {
        Vec3d normal = Vec3d.of(hitSide.getVector());

        // Reflection formula: R = I - 2(I·N)N
        // Where I is incident vector, N is normal, R is reflected vector
        double dotProduct = incident.dotProduct(normal);
        return incident.subtract(normal.multiply(2 * dotProduct));
    }

    public static SoundData checkRayEntityIntersection(Vec3d rayStart, Vec3d rayDirection, Queue<SoundData> entities, Vec3d rayEnd) {
        SoundData closestEntity = null;
        double closestDistance = Double.MAX_VALUE;
        double maxDistance = rayStart.distanceTo(rayEnd);

        for (SoundData entity : entities) {
            Box entityBounds = entity.boundingBox;

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

    public static void displayEntityRayHitCounts(World world, PlayerEntity player) {
        if (world.isClient && !entityRayHitCounts.isEmpty()) {
            for (java.util.Map.Entry<SoundData, Integer> entry : entityRayHitCounts.entrySet()) {
                SoundData entity = entry.getKey();
                int rayCount = entry.getValue();

                // Display the count above the entity
                Vec3d entityPos = entity.boundingBox.getCenter();
                Vec3d displayPos = entityPos.add(0, entity.boundingBox.getLengthY() / 2 + 0.5, 0);

                // Create floating text effect with particles
                displayRayCountText(world, displayPos, rayCount);

                // Print to console for debugging
                String entityName = entity.soundId;
                System.out.println("SFX: " + entityName + " hit by " + rayCount + " rays");
            }
        }
    }

    public static void displayRayCountText(World world, Vec3d pos, int count) {
        if (world.isClient) {
            // Create a visual representation of the count using particles
            // Spawn particles in a pattern that represents the number

            // Base particles for visibility
            for (int i = 0; i < 5; i++) {
                world.addParticle(net.minecraft.particle.ParticleTypes.ENCHANT,
                        pos.x + (Math.random() - 0.5) * 0.3,
                        pos.y + (Math.random() - 0.5) * 0.3,
                        pos.z + (Math.random() - 0.5) * 0.3,
                        0, 0.1, 0);
            }

            // Intensity-based particles (more particles = more rays)
            int particleCount = Math.min(count / 10, 20); // Scale down for visibility
            for (int i = 0; i < particleCount; i++) {
                world.addParticle(net.minecraft.particle.ParticleTypes.TOTEM_OF_UNDYING,
                        pos.x + (Math.random() - 0.5) * 0.5,
                        pos.y + (Math.random() - 0.5) * 0.5,
                        pos.z + (Math.random() - 0.5) * 0.5,
                        0, 0.05, 0);
            }

            // Color-coded particles based on ray count ranges
            if (count > 100) {
                // High count - red particles
                world.addParticle(net.minecraft.particle.ParticleTypes.FLAME,
                        pos.x, pos.y, pos.z, 0, 0.1, 0);
            } else if (count > 50) {
                // Medium count - orange particles
                world.addParticle(net.minecraft.particle.ParticleTypes.LAVA,
                        pos.x, pos.y, pos.z, 0, 0.1, 0);
            } else if (count > 10) {
                // Low count - yellow particles
                world.addParticle(net.minecraft.particle.ParticleTypes.END_ROD,
                        pos.x, pos.y, pos.z, 0, 0.1, 0);
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
