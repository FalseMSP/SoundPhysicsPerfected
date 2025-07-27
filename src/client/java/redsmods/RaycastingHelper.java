package redsmods;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.TickableSoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import redsmods.storageclasses.*;
import redsmods.wrappers.RedPermeatedSoundInstance;
import redsmods.wrappers.RedPositionedSoundInstance;
import redsmods.wrappers.RedTickableInstance;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RaycastingHelper {
    /*
    Raycasting Helper for Red's Sounds (tbh this is what does all the work bc im lazy and don't know how to code lmao
    Colors of rays defined by: https://www.youtube.com/watch?v=u6EuAUjq92k
    White: Normal Bouncing Ray
    Green: Sound Seeking Ray
    Blue: Reverb Seeking Ray
    Red: Sound Seeking Permeated Ray
    More to be defined.
     */
    public static final Queue<RedTickableInstance> tickQueue = new LinkedList<>();
    private static java.util.Map<SoundData, Integer> entityRayHitCounts = new java.util.HashMap<>();
    public static final Queue<SoundData> soundQueue = new LinkedList<>();
    public static final Queue<SoundData> weatherQueue = new LinkedList<>();
    private static final double SPEED_OF_SOUND_TICKS = 17.15; // 17.15 blocks per gametick
    private static final Map<Integer,ArrayList<SoundInstance>> soundPlayingWaiting = new HashMap<>();
    private static int ticksSinceWorld;
    public static double distanceFromWallEcho = 0; // Modified in BlueRay casting
    public static double distanceFromWallEchoDenom = 0; // Modified in BlueRay casting
    public static int reverbStrength = 0; // Modified in BlueRay casting
    public static int reverbDenom = 0; // used in reverbStrength/reverbDenom (total)
    public static int outdoorLeak;
    public static int outdoorLeakDenom;

    private static final Map<SoundData, List<RayHitData>> rayHitsByEntity = new HashMap<>();
    private static Map<SoundData, List<RayHitData>> redRaysToTarget = new HashMap<>();
    private static final Map<SoundData, AveragedSoundData> muffledAveragedResults = new HashMap<>();
    public static final Map<SoundInstance, SoundInstance> soundInstanceMap = new ConcurrentHashMap<>();
    public static final Map<SoundInstance, SoundInstance> soundPermInstanceMap = new ConcurrentHashMap<>();

    // Config Grabbed stuff
    private static int RAYS_CAST = Config.getInstance().raysCast;
    private static int MAX_BOUNCES = Config.getInstance().raysBounced;
    private static double RAY_SEGMENT_LENGTH = 16.0 * Config.getInstance().maxRayLength; // 12 chunk max length
    public static boolean ENABLE_REVERB = Config.getInstance().reverbEnabled;
    public static boolean ENABLE_PERMEATION = Config.getInstance().permeationEnabled;
    public static int TICK_RATE = Config.getInstance().tickRate;
    public static RedsAttenuationType ATTENUATION_TYPE = Config.getInstance().attenuationType;

    public static void getConfig() {
        RAYS_CAST = Config.getInstance().raysCast;
        MAX_BOUNCES = Config.getInstance().raysBounced;
        ENABLE_REVERB = Config.getInstance().reverbEnabled;
        ENABLE_PERMEATION = Config.getInstance().permeationEnabled;
        RAY_SEGMENT_LENGTH = 16.0 * Config.getInstance().maxRayLength;
        TICK_RATE = Config.getInstance().tickRate;
        ATTENUATION_TYPE = Config.getInstance().attenuationType;
    }

    public static void castBouncingRaysAndDetectSFX(World world, PlayerEntity player) {
        try {
            Vec3d playerEyePos = player.getEyePos();
            double maxTotalDistance = RAY_SEGMENT_LENGTH * MAX_BOUNCES; // Max total distance after all bounces

            // Clear previous ray hit counts
            entityRayHitCounts.clear();

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getSoundManager() == null) {
                return;
            }

            if (soundQueue.isEmpty() && tickQueue.isEmpty())
                return; // no sounds to proc

            weatherQueue.clear();

            for (SoundData sound : soundQueue) {
                if (sound.soundId.contains("rain")) {
                    weatherQueue.add(sound);
                    soundQueue.remove(sound);
                }
            }

            // Generate ray directions
            Vec3d[] rayDirections = RaycastingHelper.generateRayDirections();
            rayHitsByEntity.clear(); // clear list before every call
            redRaysToTarget.clear(); // wow this was the issue? i feel like a real dumbass now D:

            processAndPlayAveragedSounds(world,player,playerEyePos,new ArrayList<>(Arrays.asList(rayDirections)),soundQueue,maxTotalDistance,client);
            // Display ray hit counts for detected sfx
            displayEntityRayHitCounts(world, player);

            tickQueue.clear();
            soundQueue.clear();

        } catch (Exception e) {
            System.err.println("Error in player bouncing ray entity detection: " + e.getMessage());
        }
    }

    public static void processAndPlayAveragedSounds(World world, PlayerEntity player, Vec3d playerEyePos,
                                                    List<Vec3d> rayDirections, Queue<SoundData> soundQueue,
                                                    double maxTotalDistance, MinecraftClient client) {

        // Process rays and get averaged results
        Map<SoundData, AveragedSoundData> averagedResults = processRaysWithAveraging(
                world, player, playerEyePos, rayDirections, soundQueue, maxTotalDistance);

        if (averagedResults.isEmpty() && muffledAveragedResults.isEmpty()) {
            return;
        }


        for (AveragedSoundData avgData : averagedResults.values()) {
            playAveragedSoundWithAdjustments(client, avgData, playerEyePos, 1.8f, 1.0f);
        }

        if(!ENABLE_PERMEATION)
            return;
        for (AveragedSoundData avgData : muffledAveragedResults.values()) {
            playMuffled(client, avgData, playerEyePos, 0.6f, 1f);
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
            Vec3d targetPosition;
            if (avgData.soundEntity.soundId.contains("rain")) { // if outdoors and raining, make the rain sound play on the player to make it sound like its all around the player && ((double) outdoorLeak / outdoorLeakDenom) > 0.4
                targetPosition = playerPos.add(avgData.averageDirection.multiply(5));
            } else {
                targetPosition = playerPos.add(avgData.averageDirection.multiply(avgData.averageDistance));
            }
            // Get original sound properties
            SoundInstance originalSound = avgData.soundEntity.sound;
            Identifier soundId = originalSound.getId();

            if(avgData.totalWeight == 0 && originalSound instanceof RedTickableInstance) {
                ((RedTickableInstance) originalSound).setVolume(0);
                ((RedTickableInstance) originalSound).setPos(((RedTickableInstance) originalSound).getOriginalPosition());
                return;
            }
            // Calculate adjusted volume based on ray count and weight (confidence-based)
            float baseVolume;

            if (originalSound instanceof RedTickableInstance)
                baseVolume = ((RedTickableInstance) originalSound).getOriginalVolume();
            else
                baseVolume = ((RedSoundInstance) originalSound).original.getVolume();

            float confidenceMultiplier = (float) Math.min(1.0, Math.log10(avgData.totalWeight + 1.0));
            float adjustedVolume = baseVolume * volumeMultiplier * confidenceMultiplier;

            // Calculate adjusted pitch
            float basePitch = originalSound.getPitch();
            float adjustedPitch = basePitch * pitchMultiplier;
            SoundInstance newSound;

            // Create positioned sound with adjustments
            if (originalSound instanceof RedTickableInstance) { // update pos of sounds
                System.out.println(soundId);
                System.out.println(((RedTickableInstance) originalSound).getOriginalPosition());
                System.out.println(originalSound.getX() + "    " + originalSound.getY() + "    " + originalSound.getZ());
                ((RedTickableInstance) originalSound).setPos(targetPosition);
                ((RedTickableInstance) originalSound).setVolume(Math.max(0.01f, Math.min(1.0f, adjustedVolume)));
                return;
            } else if (((RedSoundInstance) originalSound) instanceof TickableSoundInstance) {
                newSound = new RedTickableInstance(soundId,originalSound.getSound(),originalSound.getCategory(),targetPosition,Math.max(0.01f, Math.min(1.0f, adjustedVolume)),Math.max(0.5f, Math.min(2.0f, adjustedPitch)),originalSound, new Vec3d(originalSound.getX(), originalSound.getY(), originalSound.getZ()),baseVolume);
            } else {
                newSound = new RedTickableInstance(soundId,originalSound.getSound(),originalSound.getCategory(),targetPosition,Math.max(0.01f, Math.min(1.0f, adjustedVolume)),Math.max(0.5f, Math.min(2.0f, adjustedPitch)),originalSound,new Vec3d(originalSound.getX(),originalSound.getY(),originalSound.getZ()),baseVolume);
            }
            soundInstanceMap.put(((RedSoundInstance) originalSound).getOriginal(),newSound);
            if (adjustedVolume <= 0.01)
                return;

            queueSound(newSound,(int) (avgData.averageDistance / SPEED_OF_SOUND_TICKS));

            // echo logic (DEPRECATED)
            System.out.println(distanceFromWallEcho + ", " + reverbStrength);

            // Debug output
//            System.out.println("Playing adjusted averaged sound: " + soundId.toString());
//            System.out.println("  Volume: " + String.format("%.3f", adjustedVolume) + " (original: " + String.format("%.3f", baseVolume) + ")");
//            System.out.println("  Pitch: " + String.format("%.3f", adjustedPitch) + " (original: " + String.format("%.3f", basePitch) + ")");
//            System.out.println("  Confidence: " + String.format("%.3f", confidenceMultiplier));
//            System.out.println("  Distance: " + avgData.soundEntity.getDistance() + " ");
//            System.out.println("  x: " + targetPosition.getX() + "  y: " + targetPosition.getY() + "  z: " + targetPosition.getZ());

        } catch (Exception e) {
            System.err.println("Error playing adjusted averaged sound: " + e.getMessage());
        }
    }
    public static void playMuffled(MinecraftClient client, AveragedSoundData avgData, Vec3d playerPos,
                                                        float volumeMultiplier, float pitchMultiplier) {
        if (client == null || client.world == null || avgData == null) {
            return;
        }

        try {
            // Calculate the target position
            Vec3d targetPosition = playerPos.add(avgData.averageDirection.multiply(avgData.averageDistance));

            // Get original sound properties
            RedSoundInstance originalSound = (RedSoundInstance) avgData.soundEntity.sound;
            Identifier soundId = originalSound.getId();

            // Calculate adjusted volume based on ray count and weight (confidence-based)
            float baseVolume = originalSound.getVolume();
            float confidenceMultiplier = (float) Math.min(1.0, Math.log10(avgData.totalWeight + 1.0));
            float adjustedVolume = baseVolume * volumeMultiplier * confidenceMultiplier;

            // Calculate adjusted pitch
            float basePitch = originalSound.getPitch();
            float adjustedPitch = basePitch * pitchMultiplier;
            SoundInstance newSound;
            // Create positioned sound with adjustments
            if (originalSound.getOriginal() instanceof TickableSoundInstance) {
                newSound = new RedTickableInstance(soundId,originalSound.getSound(),originalSound.getCategory(),targetPosition,Math.max(0.01f, Math.min(1.0f, adjustedVolume)),Math.max(0.5f, Math.min(2.0f, adjustedPitch)),originalSound, new Vec3d(originalSound.getX(), originalSound.getY(), originalSound.getZ()),baseVolume);
            } else {
                newSound = new RedPermeatedSoundInstance(
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
            }
            soundPermInstanceMap.put(originalSound.getOriginal(),newSound);
            if (adjustedVolume <= 0.01)
                return;

            queueSound(newSound,(int) (avgData.averageDistance / SPEED_OF_SOUND_TICKS));

//            System.out.println("Playing muffled averaged sound: " + soundId.toString());
//            System.out.println("  Volume: " + String.format("%.3f", adjustedVolume) + " (original: " + String.format("%.3f", baseVolume) + ")");
//            System.out.println("  Pitch: " + String.format("%.3f", adjustedPitch) + " (original: " + String.format("%.3f", basePitch) + ")");
//            System.out.println("  Confidence: " + String.format("%.3f", confidenceMultiplier));
//            System.out.println("  Distance: " + avgData.soundEntity.getDistance() + " ");
//            System.out.println("  x: " + targetPosition.getX() + "  y: " + targetPosition.getY() + "  z: " + targetPosition.getZ());
        } catch (Exception e) {
            System.err.println("Error playing adjusted averaged sound: " + e.getMessage());
        }
    }

    private static void queueSound(SoundInstance newSound, int distance) {
        soundPlayingWaiting.computeIfAbsent((distance) + ticksSinceWorld + 1, k -> new ArrayList<>()).add(newSound);
    }

    private static void queueSound(RedPositionedSoundInstance newSound, int distance, int delay) {
        soundPlayingWaiting.computeIfAbsent((distance) + ticksSinceWorld + 1 + delay, k -> new ArrayList<>()).add(newSound);
    }

    // Main method to process rays and calculate averages
    public static Map<SoundData, AveragedSoundData> processRaysWithAveraging(World world, PlayerEntity player,
                                                                             Vec3d playerEyePos, List<Vec3d> rayDirections,
                                                                             Queue<SoundData> soundQueue, double maxTotalDistance) {
        reverbStrength = 0;
        distanceFromWallEcho = 0;
        distanceFromWallEchoDenom = 0;
        reverbDenom = 0;
        outdoorLeak = 0;
        outdoorLeakDenom = 0;
//        echoFac.clear()
        // Cast all rays and collect hit data
        for (Vec3d direction : rayDirections) {
            castBouncingRay(world, player, playerEyePos, direction, soundQueue, maxTotalDistance);
        }

        // Calculate averages for each entity
        Map<SoundData, AveragedSoundData> averagedResults = new HashMap<>();
        muffledAveragedResults.clear();


        for (Map.Entry<SoundData, List<RayHitData>> entry : rayHitsByEntity.entrySet()) {
            SoundData entity = entry.getKey();
            List<RayHitData> rayHits = entry.getValue();

            AveragedSoundData averagedData = calculateWeightedAverages(entity, rayHits);
            averagedResults.put(entity, averagedData);
        }

        for (Map.Entry<SoundData, List<RayHitData>> entry : redRaysToTarget.entrySet()) {
            SoundData entity = entry.getKey();
            List<RayHitData> rayHits = entry.getValue();

            AveragedSoundData averagedData = calculateWeightedAverages(entity, rayHits);
            muffledAveragedResults.put(entity, averagedData);
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
        if (totalWeight == 0.0)
            return new AveragedSoundData(entity, weightedDirectionSum, weightedDistanceSum,
                    totalWeight, rayHits.size(), rayHits);
        // Calculate averages
        double averageDistance = weightedDistanceSum / totalWeight;
        Vec3d averageDirection = weightedDirectionSum.multiply(1.0 / totalWeight).normalize();

        return new AveragedSoundData(entity, averageDirection, averageDistance,
                totalWeight, rayHits.size(), rayHits);
    }

    public static RaycastResult castBouncingRay(World world, PlayerEntity player, Vec3d startPos, Vec3d direction, Queue<SoundData> soundQueue, double maxTotalDistance) {
        Vec3d currentPos = startPos;
        Vec3d currentDirection = direction.normalize();
        Vec3d initialDirection = currentDirection.normalize(); // Store the initial direction as unit vector
        double remainingDistance = maxTotalDistance;
        double totalDistanceTraveled = 0.0;

        SoundData hitEntity = null;

        for (int bounce = 0; bounce <= MAX_BOUNCES && remainingDistance > 0; bounce++) {
            double BounceAbsMult = Math.pow(0.7,bounce); // Bounce Absoprtion
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

            // cast Green rays
            if (hitBlock) {
                castGreenRay(world, player, actualEnd, soundQueue, totalDistanceTraveled*BounceAbsMult, initialDirection);
                if (ENABLE_REVERB)
                    castBlueRay(world, player, actualEnd, soundQueue, totalDistanceTraveled, initialDirection);
                if (ENABLE_PERMEATION)
                    castRedRay(world, player, actualEnd, soundQueue, totalDistanceTraveled, initialDirection);
            }
            // Draw this segment
//            drawBouncingRaySegment(world, currentPos, actualEnd, bounce);

            // If we hit a block, calculate bounce
            if (hitBlock) {
                outdoorLeakDenom += 1;
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
                // Ray reached maximum segment length without hitting anything, Outside/Outdoor Rays!!
                for (SoundData soundEntity : weatherQueue) {
                    double weight;
                    if (ATTENUATION_TYPE == ATTENUATION_TYPE.INVERSE_SQUARE)
                        weight = 1.0 / (Math.max(totalDistanceTraveled - segmentDistance, 0.1) * Math.max(totalDistanceTraveled - segmentDistance, 0.1)); // quadratic
                    else
                        weight = 1.0 / Math.max(totalDistanceTraveled - segmentDistance, 0.1); // linear

                    RaycastResult GreenRayResult = new RaycastResult(
                            maxTotalDistance,
                            initialDirection,
                            soundEntity
                    );

                    RayHitData hitData = new RayHitData(GreenRayResult, initialDirection, weight);

                    // Add to rayHitsByEntity map
                    rayHitsByEntity.computeIfAbsent(soundEntity, k -> new ArrayList<>()).add(hitData);

                    // Increment ray hit count for this entity
                    entityRayHitCounts.put(soundEntity, entityRayHitCounts.getOrDefault(soundEntity, 0) + 1);
                }

                // Calculate reflected direction based on hit face (spherical reflection)
                Vec3d toCenter = player.getPos().subtract(actualEnd); // points towards player
                Vec3d normal = toCenter.normalize();
                Vec3d reflectedDirection = calculateReflection(currentDirection, normal);

                // Update for next bounce
                currentPos = segmentEnd.add(reflectedDirection.multiply(0.01)); // Small offset to avoid hitting same block
                currentDirection = reflectedDirection;

                // Reduce remaining distance
                remainingDistance -= segmentTraveled;

                reverbDenom += 1;
                outdoorLeak += 1;
                outdoorLeakDenom += 1;
            }
        }

        // Return comprehensive result
        return new RaycastResult(totalDistanceTraveled, initialDirection, hitEntity, currentPos);
    }

    private static void castGreenRay(World world, PlayerEntity player, Vec3d currentPos, Queue<SoundData> entities, double currentDistance, Vec3d initalDirection) {
        // Cast rays directly towards each sound source to check line of sight
        for (SoundData soundEntity : entities) {
            Vec3d entityCenter = soundEntity.position;
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
                    currentPos.distanceTo(blockHit.getPos()) >= distanceToEntity - 1;

            if (hasLineOfSight) {
                // Calculate weight based on distance (closer = higher weight)
                double weight;
                if (ATTENUATION_TYPE == ATTENUATION_TYPE.INVERSE_SQUARE)
                    weight = 1.0 / (Math.max(distanceToEntity+currentDistance, 0.1) * Math.max(distanceToEntity+currentDistance, 0.1)); // quadratic
                else
                    weight = 1.0 / Math.max(distanceToEntity+currentDistance, 0.1); // linear
                // Create ray result for this direct line of sight
                RaycastResult GreenRayResult = new RaycastResult(
                        distanceToEntity,
                        initalDirection,
                        soundEntity,
                        entityCenter
                );

                RayHitData hitData = new RayHitData(GreenRayResult, initalDirection, weight);

                // Add to rayHitsByEntity map
                rayHitsByEntity.computeIfAbsent(soundEntity, k -> new ArrayList<>()).add(hitData);

                // Increment ray hit count for this entity
                entityRayHitCounts.put(soundEntity, entityRayHitCounts.getOrDefault(soundEntity, 0) + 1);

                // Draw Green ray visualization
//                drawGreenRay(world, currentPos, entityCenter);
            }
        }

        for (RedTickableInstance soundEntity : tickQueue) { // for ticking sounds (longer sounds)
            Vec3d entityCenter = soundEntity.getOriginalPosition();
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
                    currentPos.distanceTo(blockHit.getPos()) >= distanceToEntity - 1;
            SoundData data = new TickableSoundData(soundEntity,soundEntity.getOriginalPosition(),soundEntity.getSound().getIdentifier().toString());
            if (hasLineOfSight) {
                // Calculate weight based on distance (closer = higher weight)
                double weight;
                if (ATTENUATION_TYPE == ATTENUATION_TYPE.INVERSE_SQUARE)
                    weight = 1.0 / (Math.max(distanceToEntity+currentDistance, 0.1) * Math.max(distanceToEntity+currentDistance, 0.1)); // quadratic
                else
                    weight = 1.0 / Math.max(distanceToEntity+currentDistance, 0.1); // linear

                // Create ray result for this direct line of sight
                RaycastResult GreenRayResult = new RaycastResult(
                        distanceToEntity,
                        initalDirection,
                        data,
                        entityCenter
                );

                RayHitData hitData = new RayHitData(GreenRayResult, initalDirection, weight);

                // Add to rayHitsByEntity map
                rayHitsByEntity.computeIfAbsent(data, k -> new ArrayList<>()).add(hitData);

                // Increment ray hit count for this entity
                entityRayHitCounts.put(data, entityRayHitCounts.getOrDefault(soundEntity, 0) + 1);

                // Draw Green ray visualization
//                drawGreenRay(world, currentPos, entityCenter);
            } else if (!rayHitsByEntity.containsKey(data)) { // create empty object to say that there was nothing hit
                rayHitsByEntity.computeIfAbsent(data, k -> new ArrayList<>());
            }
        }
    }

    private static boolean castBlueRay(World world, PlayerEntity player, Vec3d currentPos, Queue<SoundData> entities, double currentDistance, Vec3d initalDirection) {
        // Cast rays directly towards player to check line of sight
        Vec3d entityCenter = player.getBoundingBox().getCenter();

        // get currentPos out of a block
        currentPos = currentPos.add(entityCenter.subtract(currentPos).multiply(0.87));
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
        reverbDenom += 1;
        if (hasLineOfSight) {
//            int timeDelay = (int) Math.floor(currentDistance * SPEED_OF_SOUND_MS);
            distanceFromWallEcho += currentDistance; // average
            distanceFromWallEchoDenom += 1;
            reverbStrength += 1; // get the MAXIMUM amount of reverb possible
//            echoFac.putIfAbsent(timeDelay,0);
//            echoFac.put(timeDelay,echoFac.get(timeDelay)+1);

            // Draw Blue ray visualization
//            drawBlueRay(world, currentPos, entityCenter);
        }
        return hasLineOfSight;
    }

    private static void castRedRay(World world, PlayerEntity player, Vec3d currentPos, Queue<SoundData> entities, double currentDistance, Vec3d initalDirection) {
        // Cast rays directly towards each sound source to check how many blocks are between the sources.
        for (SoundData soundEntity : entities) {
            Vec3d entityCenter = soundEntity.position;
            double distanceToEntity = currentPos.distanceTo(entityCenter);
//            if (distanceToEntity + currentDistance > SPEED_OF_SOUND_TICKS || distanceToEntity > SPEED_OF_SOUND_TICKS)
//                continue;
            RaycastContext raycastContext = new RaycastContext(
                    currentPos,
                    entityCenter,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    player
            );

            BlockHitResult blockHit = world.raycast(raycastContext);
            currentPos = blockHit.getPos();
            // Count blocks between player and sound source
            int blockCount = countBlocksBetween(world, currentPos, entityCenter, player);
            if (blockCount == 0) // so green and red rays don't overlap
                continue;

            // apply attenuation based on number of blocks:
            double blockAttenuation = Math.pow(0.4, blockCount); // Each block reduces sound by 60%

            // Calculate weight based on distance and block count
            double weight = blockAttenuation / (Math.max(distanceToEntity, 0.1) * Math.max(distanceToEntity, 0.1));

            // Create ray result with block count information
            RaycastResult rayResult = new RaycastResult(
                    distanceToEntity,
                    initalDirection,
                    soundEntity,
                    entityCenter
            );
            RayHitData hitData = new RayHitData(rayResult, initalDirection, weight);

            // Add to rayHitsByEntity map
            redRaysToTarget.computeIfAbsent(soundEntity, k -> new ArrayList<>()).add(hitData);

            // Optional: Log block count for debugging
            // System.out.println("Blocks between player and " + soundEntity + ": " + blockCount);
        }
    }

    private static int countBlocksBetween(World world, Vec3d start, Vec3d end, PlayerEntity player) {
        int blockCount = 0;
        Vec3d direction = end.subtract(start).normalize();
        double totalDistance = start.distanceTo(end);
        double stepSize = 0.5; // Check every 0.5 blocks for accuracy

        for (double distance = stepSize; distance < totalDistance; distance += stepSize) {
            Vec3d currentPos = start.add(direction.multiply(distance));
            BlockPos blockPos = new BlockPos((int)Math.floor(currentPos.x),
                    (int)Math.floor(currentPos.y),
                    (int)Math.floor(currentPos.z));

            BlockState blockState = world.getBlockState(blockPos);

            // Check if the block is solid and not air
            if (!blockState.isAir()) {
                // Create a small raycast to check if this specific block actually blocks the path
                Vec3d blockCenter = Vec3d.ofCenter(blockPos);
                RaycastContext raycastContext = new RaycastContext(
                        start,
                        blockCenter,
                        RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE,
                        player
                );

                BlockHitResult hit = world.raycast(raycastContext);
                if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(blockPos)) {
                    blockCount++;
                    // Skip ahead to avoid counting the same block multiple times
                    distance += 1.0;
                    if (blockCount > 3) return 3;
                }
            }
        }

        return blockCount;
    }

    public static void drawGreenRay (World world, Vec3d start, Vec3d end){
        if (world.isClient) {
            Vec3d direction = end.subtract(start).normalize();
            double distance = start.distanceTo(end);

            // Draw Green particles for line of sight rays
            for (double d = 0; d < distance; d += 0.5) {
                Vec3d particlePos = start.add(direction.multiply(d));

                // Use Green particles for line of sight visualization
                world.addParticle(ParticleTypes.HAPPY_VILLAGER,
                        particlePos.x, particlePos.y, particlePos.z, 0, 0, 0);
            }
        }
    }

    public static void drawBlueRay (World world, Vec3d start, Vec3d end){
        if (world.isClient) {
            Vec3d direction = end.subtract(start).normalize();
            double distance = start.distanceTo(end);

            // Draw Green particles for line of sight rays
            for (double d = 0; d < distance; d += 0.5) {
                Vec3d particlePos = start.add(direction.multiply(d));

                // Use Green particles for line of sight visualization
                world.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                        particlePos.x, particlePos.y, particlePos.z, 0, 0, 0);
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

    public static Vec3d calculateReflection(Vec3d incident, Vec3d normal) {
        // Reflection formula: R = I - 2(I·N)N
        // Where I is incident vector, N is normal, R is reflected vector
        double dotProduct = incident.dotProduct(normal);
        return incident.subtract(normal.multiply(2 * dotProduct));
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
                        // Original ray - white/Green
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
                Vec3d entityPos = entity.position;
                Vec3d displayPos = entityPos.add(0, entity.position.y, 0);

                // Print to console for debugging
                String entityName = entity.soundId;
//                System.out.println("SFX: " + entityName + " hit by " + rayCount + " rays");
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
        ArrayList<SoundInstance> sound = soundPlayingWaiting.get((Integer) ticksSinceWorld);
        for (SoundInstance newSound : sound)
            client.getSoundManager().play(newSound);
        soundPlayingWaiting.remove(tsw);
    }
}
