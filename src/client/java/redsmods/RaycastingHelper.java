package redsmods;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.*;

public class RaycastingHelper {
    /*
    Raycasting Helper for Red's Sounds (tbh this is what does all the work bc im lazy and don't know how to code lmao
    Colors of rays defined by: https://www.youtube.com/watch?v=u6EuAUjq92k
    White: Normal Bouncing Ray
    Blue: Sound Seeking Ray
    More to be defined.

     */

    private static final int RAYS_CAST = 2048; // 64000 is production number
    private static final int MAX_BOUNCES = 4;
    private static final double RAY_SEGMENT_LENGTH = 64.0;
    private static java.util.Map<SoundData, Integer> entityRayHitCounts = new java.util.HashMap<>();
    public static final Queue<SoundData> soundQueue = new LinkedList<>();
    private static final double SPEED_OF_SOUND_TICKS = 17.15*2; // 17.15 blocks per gametick
    private static final Map<Integer,ArrayList<RedPositionedSoundInstance>> soundPlayingWaiting = new HashMap<>();
    private static int ticksSinceWorld;

    // Sound RayData
    private static Map<SoundData, List<RayHitData>> rayHitsByEntity = new HashMap<>();
    public static void castBouncingRaysAndDetectSFX(World world, PlayerEntity player) {
        try {
            Vec3d playerEyePos = player.getEyePos();
            double maxTotalDistance = 64.0; // Max total distance after all bounces

            // Clear previous ray hit counts
            entityRayHitCounts.clear();

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getSoundManager() == null) {
                return;
            }

            Queue<SoundData> nearbyEntities = new LinkedList<>(soundQueue);

            // Generate ray directions
            Vec3d[] rayDirections = RaycastingHelper.generateRayDirections();
            rayHitsByEntity.clear(); // clear list before every call

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

        for (AveragedSoundData avgData : averagedResults.values()) {
            playAveragedSoundWithAdjustments(client, avgData, playerEyePos, 1.8f, 1.0f);
        }
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
            RedPositionedSoundInstance newSound = new RedPositionedSoundInstance(
                    soundId,                                    // Sound identifier
                    originalSound.getCategory(),                // Sound category
                    Math.max(0.01f, Math.min(1.0f, adjustedVolume)),  // Clamp volume between 0-1
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

            queueSound(newSound,avgData.soundEntity.getDistance());

            // Debug output
            System.out.println("Playing adjusted averaged sound: " + soundId.toString());
            System.out.println("  Volume: " + String.format("%.3f", adjustedVolume) + " (original: " + String.format("%.3f", baseVolume) + ")");
            System.out.println("  Pitch: " + String.format("%.3f", adjustedPitch) + " (original: " + String.format("%.3f", basePitch) + ")");
            System.out.println("  Confidence: " + String.format("%.3f", confidenceMultiplier));
            System.out.println("  Distance: " + avgData.soundEntity.getDistance() + " ");
            System.out.println("  x: " + targetPosition.getX() + "  y: " + targetPosition.getY() + "  z: " + targetPosition.getZ());

        } catch (Exception e) {
            System.err.println("Error playing adjusted averaged sound: " + e.getMessage());
        }
    }

    private static void queueSound(RedPositionedSoundInstance newSound, int distance) {
        soundPlayingWaiting.computeIfAbsent(distance*2+ticksSinceWorld+2,k -> new ArrayList<>()).add(newSound);
    }

    // Main method to process rays and calculate averages
    public static Map<SoundData, AveragedSoundData> processRaysWithAveraging(World world, PlayerEntity player,
                                                                             Vec3d playerEyePos, List<Vec3d> rayDirections,
                                                                             Queue<SoundData> nearbyEntities, double maxTotalDistance) {
        // Cast all rays and collect hit data
        for (Vec3d direction : rayDirections) {
            RaycastResult res = castBouncingRay(world, player, playerEyePos, direction, nearbyEntities, maxTotalDistance);
            if (res.hitEntity != null) {
                // Calculate weight using inverse square law (1/d²)
                // Add small epsilon to prevent division by zero
                double distance = Math.max(res.totalDistance, 0.1);
                double weight = 1.0 / (distance); // made it linear: * distance

                res.hitEntity.setDistance((int) Math.floor(distance/SPEED_OF_SOUND_TICKS)); // messy asf casting lol

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

            // cast BLUE rays
            castBlueRay(world,player,actualEnd,entities,totalDistanceTraveled);
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
//                drawEntityDetectionLine(world, currentPos, entityCenter);
//                drawBouncingRaySegment(world, currentPos, entityCenter, bounce);

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

    private static void castBlueRay(World world, PlayerEntity player, Vec3d currentPos, Queue<SoundData> entities, double currentDistance) {
        // Cast rays directly towards each sound source to check line of sight
        for (SoundData soundEntity : entities) {
            Vec3d entityCenter = soundEntity.boundingBox.getCenter();
            Vec3d directionToEntity = entityCenter.subtract(currentPos).normalize();
            double distanceToEntity = currentPos.distanceTo(entityCenter);

            // Create raycast context for line of sight check
            RaycastContext raycastContext = new RaycastContext(
                    currentPos,
                    entityCenter,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    player
            );

            BlockHitResult blockHit = world.raycast(raycastContext);

            // Check if we have line of sight (no block hit or hit is beyond the entity)
            boolean hasLineOfSight = blockHit.getType() != HitResult.Type.BLOCK ||
                    currentPos.distanceTo(blockHit.getPos()) >= distanceToEntity - 0.6;

            if (hasLineOfSight) {
                // Calculate weight based on distance (closer = higher weight)
                double weight = 1.0 / Math.max(distanceToEntity+currentDistance, 0.1);

                // Create ray result for this direct line of sight
                RaycastResult blueRayResult = new RaycastResult(
                        distanceToEntity,
                        directionToEntity,
                        soundEntity,
                        entityCenter,
                        true
                );

                RayHitData hitData = new RayHitData(blueRayResult, directionToEntity, weight);

                // Add to rayHitsByEntity map
                rayHitsByEntity.computeIfAbsent(soundEntity, k -> new ArrayList<>()).add(hitData);

                // Draw blue ray visualization (optional)
                drawBlueRay(world, currentPos, entityCenter);
            }
        }
    }
    public static void drawBlueRay (World world, Vec3d start, Vec3d end){
        if (world.isClient) {
            Vec3d direction = end.subtract(start).normalize();
            double distance = start.distanceTo(end);

            // Draw blue particles for line of sight rays
            for (double d = 0; d < distance; d += 0.5) {
                Vec3d particlePos = start.add(direction.multiply(d));

                // Use blue/cyan particles for line of sight visualization
                world.addParticle(net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME,
                        particlePos.x, particlePos.y, particlePos.z, 0, 0, 0);
            }
        }
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

                // Print to console for debugging
                String entityName = entity.soundId;
                System.out.println("SFX: " + entityName + " hit by " + rayCount + " rays");
            }
        }
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

    public static void playQueuedObjects(int tsw) {
        ticksSinceWorld = tsw;
        if (!soundPlayingWaiting.containsKey((Integer) ticksSinceWorld))
            return;

        MinecraftClient client = MinecraftClient.getInstance();
        ArrayList<RedPositionedSoundInstance> sound = soundPlayingWaiting.get((Integer) ticksSinceWorld);
        for (RedPositionedSoundInstance newSound : sound)
            client.getSoundManager().play(newSound);
        soundPlayingWaiting.remove(tsw);
    }
}
