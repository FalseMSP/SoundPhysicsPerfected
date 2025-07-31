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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class RaycastingHelper {
    /*
    Raycasting Helper for Red's Sounds (tbh this is what does all the work bc im lazy and don't know how to code lmao
    Colors of rays defined by: https://www.youtube.com/watch?v=u6EuAUjq92k

    White: Normal Bouncing Ray
    Green: Sound Seeking Ray
    Blue: Reverb Seeking Ray
    Red: Sound Seeking Permeated Ray

    Thread Safety go brrrrrrrrrrrrr
     */
    public static final Queue<RedTickableInstance> tickQueue = new LinkedList<>();
    private static final ConcurrentHashMap<SoundData, Integer> entityRayHitCounts = new ConcurrentHashMap<>();
    public static final Queue<SoundData> soundQueue = new LinkedList<>();
    public static final Queue<SoundData> weatherQueue = new LinkedList<>();
    private static final double SPEED_OF_SOUND_TICKS = 17.15; // 17.15 blocks per gametick
    private static final Map<Integer,ArrayList<SoundInstance>> soundPlayingWaiting = new ConcurrentHashMap<>();
    private static int ticksSinceWorld;

    private static final AtomicReference<Double> distanceFromWallEcho = new AtomicReference<>(0.0);
    private static final AtomicReference<Double> distanceFromWallEchoDenom = new AtomicReference<>(0.0);
    private static final AtomicInteger reverbStrength = new AtomicInteger(0);
    private static final AtomicInteger reverbDenom = new AtomicInteger(0);
    private static final AtomicInteger outdoorLeak = new AtomicInteger(0);
    private static final AtomicInteger outdoorLeakDenom = new AtomicInteger(0);

    private static final ConcurrentHashMap<SoundData, List<RayHitData>> rayHitsByEntity = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<SoundData, List<RayHitData>> redRaysToTarget = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<SoundData, AveragedSoundData> muffledAveragedResults = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<SoundInstance, SoundInstance> soundInstanceMap = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<SoundInstance, SoundInstance> soundPermInstanceMap = new ConcurrentHashMap<>();

    // Thread pool for parallel ray processing
    private static final int THREAD_POOL_SIZE = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
    private static final ExecutorService raycastExecutor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    private static final ExecutorService soundProcessingExecutor = Executors.newFixedThreadPool(2);

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

            // Process weather sounds
            Iterator<SoundData> iterator = soundQueue.iterator();
            while (iterator.hasNext()) {
                SoundData sound = iterator.next();
                if (sound.soundId.contains("rain")) {
                    weatherQueue.add(sound);
                    iterator.remove();
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

        Map<SoundData, AveragedSoundData> averagedResults = processRaysWithAveraging(
                world, player, playerEyePos, rayDirections, soundQueue, maxTotalDistance);

        if (averagedResults.isEmpty() && muffledAveragedResults.isEmpty()) {
            return;
        }

        List<CompletableFuture<Void>> soundTasks = new ArrayList<>();

        for (AveragedSoundData avgData : averagedResults.values()) {
            CompletableFuture<Void> task = CompletableFuture.runAsync(() ->
                            playAveragedSoundWithAdjustments(client, avgData, playerEyePos, 1.8f, 1.0f),
                    soundProcessingExecutor);
            soundTasks.add(task);
        }

        if(ENABLE_PERMEATION) {
            for (AveragedSoundData avgData : muffledAveragedResults.values()) {
                CompletableFuture<Void> task = CompletableFuture.runAsync(() ->
                                playMuffled(client, avgData, playerEyePos, 0.6f, 1f),
                        soundProcessingExecutor);
                soundTasks.add(task);
            }
        }

        // Wait for all sound processing to complete
        CompletableFuture.allOf(soundTasks.toArray(new CompletableFuture[0])).join();
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

    public static Map<SoundData, AveragedSoundData> processRaysWithAveraging(World world, PlayerEntity player,
                                                                             Vec3d playerEyePos, List<Vec3d> rayDirections,
                                                                             Queue<SoundData> soundQueue, double maxTotalDistance) {
        // Reset atomic variables
        reverbStrength.set(0);
        distanceFromWallEcho.set(0.0);
        distanceFromWallEchoDenom.set(0.0);
        reverbDenom.set(0);
        outdoorLeak.set(0);
        outdoorLeakDenom.set(0);

        final ConcurrentLinkedQueue<SoundData> threadSafeSoundQueue = new ConcurrentLinkedQueue<>(soundQueue); // deep copy so queue can be appended while sounds are proccessing without breakin shi

        // Divide rays into chunks for parallel processing
        int raysPerChunk = Math.max(1, rayDirections.size() / THREAD_POOL_SIZE);
        List<List<Vec3d>> rayChunks = new ArrayList<>();

        for (int i = 0; i < rayDirections.size(); i += raysPerChunk) {
            int endIndex = Math.min(i + raysPerChunk, rayDirections.size());
            rayChunks.add(rayDirections.subList(i, endIndex));
        }

        // Submit ray casting tasks
        List<CompletableFuture<Void>> rayTasks = new ArrayList<>();

        for (List<Vec3d> rayChunk : rayChunks) {
            CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
                for (Vec3d direction : rayChunk) {
                    castBouncingRay(world, player, playerEyePos, direction, threadSafeSoundQueue, maxTotalDistance);
                }
            }, raycastExecutor);
            rayTasks.add(task);
        }

        // Wait for all ray casting to complete
        CompletableFuture.allOf(rayTasks.toArray(new CompletableFuture[0])).join();

        // Calculate averages for each entity (this part is fast, so keep sequential)
        Map<SoundData, AveragedSoundData> averagedResults = new ConcurrentHashMap<>();
        muffledAveragedResults.clear();

        // Process normal ray hits
        for (Map.Entry<SoundData, List<RayHitData>> entry : rayHitsByEntity.entrySet()) {
            SoundData entity = entry.getKey();
            List<RayHitData> rayHits = entry.getValue();
            AveragedSoundData averagedData = calculateWeightedAverages(entity, rayHits);
            averagedResults.put(entity, averagedData);
        }

        // Process permeated ray hits
        for (Map.Entry<SoundData, List<RayHitData>> entry : redRaysToTarget.entrySet()) {
            SoundData entity = entry.getKey();
            List<RayHitData> rayHits = entry.getValue();
            AveragedSoundData averagedData = calculateWeightedAverages(entity, rayHits);
            muffledAveragedResults.put(entity, averagedData);
        }

        return averagedResults;
    }

    public static RaycastResult castBouncingRay(World world, PlayerEntity player, Vec3d startPos, Vec3d direction,
                                                Queue<SoundData> soundQueue, double maxTotalDistance) {
        Vec3d currentPos = startPos;
        Vec3d currentDirection = direction.normalize();
        Vec3d initialDirection = currentDirection.normalize();
        double remainingDistance = maxTotalDistance;
        double totalDistanceTraveled = 0.0;

        SoundData hitEntity = null;

        castGreenRay(world, player, startPos, soundQueue, totalDistanceTraveled, initialDirection);
        if (ENABLE_PERMEATION)
            castRedRay(world, player, startPos, soundQueue, totalDistanceTraveled, initialDirection);

        for (int bounce = 0; bounce <= MAX_BOUNCES && remainingDistance > 0; bounce++) {
            double BounceAbsMult = Math.pow(0.7, bounce);
            double segmentDistance = Math.min(RAY_SEGMENT_LENGTH, remainingDistance);
            Vec3d segmentEnd = currentPos.add(currentDirection.multiply(segmentDistance));

            RaycastContext raycastContext = new RaycastContext(
                    currentPos,
                    segmentEnd,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    player
            );

            BlockHitResult blockHit = world.raycast(raycastContext);

            Vec3d actualEnd = segmentEnd;
            boolean hitBlock = false;

            if (blockHit.getType() == HitResult.Type.BLOCK) {
                actualEnd = blockHit.getPos();
                hitBlock = true;
            }

            double segmentTraveled = currentPos.distanceTo(actualEnd);
            totalDistanceTraveled += segmentTraveled;

            if (hitBlock) {
                castGreenRay(world, player, actualEnd, soundQueue, totalDistanceTraveled * BounceAbsMult, initialDirection);
                if (ENABLE_REVERB)
                    castBlueRay(world, player, actualEnd, soundQueue, totalDistanceTraveled, initialDirection);
                if (ENABLE_PERMEATION)
                    castRedRay(world, player, actualEnd, soundQueue, totalDistanceTraveled, initialDirection);
            }

            if (hitBlock) {
                outdoorLeakDenom.incrementAndGet();
                Vec3d hitPos = blockHit.getPos();
                Direction hitSide = blockHit.getSide();

                Vec3d reflectedDirection = calculateReflection(currentDirection, hitSide);

                currentPos = hitPos.add(reflectedDirection.multiply(0.01));
                currentDirection = reflectedDirection;
                remainingDistance -= segmentTraveled;
            } else {
                for (SoundData soundEntity : weatherQueue) {
                    double weight;
                    if (ATTENUATION_TYPE == ATTENUATION_TYPE.INVERSE_SQUARE)
                        weight = 1.0 / (Math.max(totalDistanceTraveled - segmentDistance, 0.1) * Math.max(totalDistanceTraveled - segmentDistance, 0.1));
                    else
                        weight = 1.0 / Math.max(totalDistanceTraveled - segmentDistance, 0.1);

                    RaycastResult GreenRayResult = new RaycastResult(
                            maxTotalDistance,
                            initialDirection,
                            soundEntity
                    );

                    RayHitData hitData = new RayHitData(GreenRayResult, initialDirection, weight);

                    rayHitsByEntity.computeIfAbsent(soundEntity, k -> new CopyOnWriteArrayList<>()).add(hitData);
                    entityRayHitCounts.merge(soundEntity, 1, Integer::sum);
                }

                Vec3d toCenter = player.getPos().subtract(actualEnd);
                Vec3d normal = toCenter.normalize();
                Vec3d reflectedDirection = calculateReflection(currentDirection, normal);

                currentPos = segmentEnd.add(reflectedDirection.multiply(0.01));
                currentDirection = reflectedDirection;
                remainingDistance -= segmentTraveled;

                reverbDenom.incrementAndGet();
                outdoorLeak.incrementAndGet();
                outdoorLeakDenom.incrementAndGet();
            }
        }

        return new RaycastResult(totalDistanceTraveled, initialDirection, hitEntity, currentPos);
    }

    private static void castGreenRay(World world, PlayerEntity player, Vec3d currentPos, Queue<SoundData> entities,
                                     double currentDistance, Vec3d initialDirection) {
        for (SoundData soundEntity : entities) {
            Vec3d entityCenter = soundEntity.position;
            double distanceToEntity = currentPos.distanceTo(entityCenter);

            if (distanceToEntity + currentDistance > 16 * soundEntity.sound.getVolume())
                continue;

            RaycastContext raycastContext = new RaycastContext(
                    currentPos,
                    entityCenter,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    player
            );

            BlockHitResult blockHit = world.raycast(raycastContext);

            boolean hasLineOfSight = blockHit.getType() != HitResult.Type.BLOCK ||
                    currentPos.distanceTo(blockHit.getPos()) >= distanceToEntity - 1;

            if (hasLineOfSight) {
                double weight;
                if (ATTENUATION_TYPE == ATTENUATION_TYPE.INVERSE_SQUARE)
                    weight = 1.0 / (Math.max(distanceToEntity + currentDistance, 0.1) * Math.max(distanceToEntity + currentDistance, 0.1));
                else
                    weight = 1.0 / Math.max(distanceToEntity + currentDistance, 0.1);

                RaycastResult GreenRayResult = new RaycastResult(
                        distanceToEntity,
                        initialDirection,
                        soundEntity,
                        entityCenter
                );

                RayHitData hitData = new RayHitData(GreenRayResult, initialDirection, weight);

                rayHitsByEntity.computeIfAbsent(soundEntity, k -> new CopyOnWriteArrayList<>()).add(hitData);
                entityRayHitCounts.merge(soundEntity, 1, Integer::sum);
            }
        }

        // Handle tickable sounds
        for (RedTickableInstance soundEntity : tickQueue) {
            Vec3d entityCenter = soundEntity.getOriginalPosition();
            double distanceToEntity = currentPos.distanceTo(entityCenter);

            RaycastContext raycastContext = new RaycastContext(
                    currentPos,
                    entityCenter,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    player
            );

            BlockHitResult blockHit = world.raycast(raycastContext);

            boolean hasLineOfSight = blockHit.getType() != HitResult.Type.BLOCK ||
                    currentPos.distanceTo(blockHit.getPos()) >= distanceToEntity - 1;

            SoundData data = new TickableSoundData(soundEntity, soundEntity.getOriginalPosition(), soundEntity.getSound().getIdentifier().toString());

            if (hasLineOfSight) {
                double weight;
                if (ATTENUATION_TYPE == ATTENUATION_TYPE.INVERSE_SQUARE)
                    weight = 1.0 / (Math.max(distanceToEntity + currentDistance, 0.1) * Math.max(distanceToEntity + currentDistance, 0.1));
                else
                    weight = 1.0 / Math.max(distanceToEntity + currentDistance, 0.1);

                RaycastResult GreenRayResult = new RaycastResult(
                        distanceToEntity,
                        initialDirection,
                        data,
                        entityCenter
                );

                RayHitData hitData = new RayHitData(GreenRayResult, initialDirection, weight);

                rayHitsByEntity.computeIfAbsent(data, k -> new CopyOnWriteArrayList<>()).add(hitData);
                entityRayHitCounts.merge(data, 1, Integer::sum);
            } else {
                rayHitsByEntity.computeIfAbsent(data, k -> new CopyOnWriteArrayList<>());
            }
        }
    }

    private static boolean castBlueRay(World world, PlayerEntity player, Vec3d currentPos, Queue<SoundData> entities,
                                       double currentDistance, Vec3d initialDirection) {
        Vec3d entityCenter = player.getBoundingBox().getCenter();
        currentPos = currentPos.add(entityCenter.subtract(currentPos).multiply(0.87));
        double distanceToEntity = currentPos.distanceTo(entityCenter);

        RaycastContext raycastContext = new RaycastContext(
                currentPos,
                entityCenter,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        );

        BlockHitResult blockHit = world.raycast(raycastContext);

        boolean hasLineOfSight = blockHit.getType() != HitResult.Type.BLOCK ||
                currentPos.distanceTo(blockHit.getPos()) >= distanceToEntity - 0.6;

        reverbDenom.incrementAndGet();

        if (hasLineOfSight) {
            distanceFromWallEcho.updateAndGet(current -> current + currentDistance);
            distanceFromWallEchoDenom.updateAndGet(current -> current + 1.0);
            reverbStrength.incrementAndGet();
        }

        return hasLineOfSight;
    }

    private static void castRedRay(World world, PlayerEntity player, Vec3d currentPos, Queue<SoundData> entities,
                                   double currentDistance, Vec3d initialDirection) {
        for (SoundData soundEntity : entities) {
            Vec3d entityCenter = soundEntity.position;
            double distanceToEntity = currentPos.distanceTo(entityCenter);

            if (distanceToEntity + currentDistance > 16 * soundEntity.sound.getVolume())
                continue;

            RaycastContext raycastContext = new RaycastContext(
                    currentPos,
                    entityCenter,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    player
            );

            BlockHitResult blockHit = world.raycast(raycastContext);
            currentPos = blockHit.getPos();

            int blockCount = countBlocksBetween(world, currentPos, entityCenter, player);
            if (blockCount == 0)
                continue;

            double blockAttenuation = Math.pow(0.4, blockCount);
            double weight = blockAttenuation / (Math.max(distanceToEntity, 0.1) * Math.max(distanceToEntity, 0.1));

            RaycastResult rayResult = new RaycastResult(
                    distanceToEntity,
                    initialDirection,
                    soundEntity,
                    entityCenter
            );
            RayHitData hitData = new RayHitData(rayResult, initialDirection, weight);

            redRaysToTarget.computeIfAbsent(soundEntity, k -> new CopyOnWriteArrayList<>()).add(hitData);
        }
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


    private static int countBlocksBetween(World world, Vec3d start, Vec3d end, PlayerEntity player) {
        int blockCount = 0;
        Vec3d currentStart = start;

        while (blockCount < 3) {
            // Cast a ray from current position to the end point
            RaycastContext raycastContext = new RaycastContext(
                    currentStart,
                    end,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    player
            );

            BlockHitResult hit = world.raycast(raycastContext);

            // If we didn't hit anything or reached the end, we're done
            if (hit.getType() != HitResult.Type.BLOCK) {
                break;
            }

            BlockPos hitBlockPos = hit.getBlockPos();
            BlockState blockState = world.getBlockState(hitBlockPos);

            // Only count solid blocks (not air)
            if (!blockState.isAir()) {
                blockCount++;
            }

            // Move 1 block forward past the hit block in the direction of travel
            Vec3d direction = end.subtract(currentStart).normalize();
            Vec3d hitPoint = hit.getPos();

            // Move slightly past the hit block to avoid hitting the same block again
            currentStart = hitPoint.add(direction.multiply(1.1));

            // Check if we've passed the end point
            if (currentStart.distanceTo(start) >= end.distanceTo(start)) {
                break;
            }
        }
        return blockCount;
    }

    public static void drawGreenRay(World world, Vec3d start, Vec3d end) {
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

    public static void drawBlueRay(World world, Vec3d start, Vec3d end) {
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

    // Utility methods to get atomic values safely
    public static double getDistanceFromWallEcho() {
        return distanceFromWallEcho.get();
    }

    public static double getDistanceFromWallEchoDenom() {
        return distanceFromWallEchoDenom.get();
    }

    public static int getReverbStrength() {
        return reverbStrength.get();
    }

    public static int getReverbDenom() {
        return reverbDenom.get();
    }

    public static int getOutdoorLeak() {
        return outdoorLeak.get();
    }

    public static int getOutdoorLeakDenom() {
        return outdoorLeakDenom.get();
    }

    // I was told that cleanup is neccessary when using threads, but idk where to put this lmao
    public static void shutdown() {
        try {
            raycastExecutor.shutdown();
            soundProcessingExecutor.shutdown();

            if (!raycastExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                raycastExecutor.shutdownNow();
            }

            if (!soundProcessingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                soundProcessingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            raycastExecutor.shutdownNow();
            soundProcessingExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}