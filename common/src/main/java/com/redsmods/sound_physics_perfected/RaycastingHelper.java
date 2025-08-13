package com.redsmods.sound_physics_perfected;

import com.redsmods.sound_physics_perfected.ReverbHelpers.EnhancedReverbData;
import com.redsmods.sound_physics_perfected.ReverbHelpers.ReverbSurfaceData;
import com.redsmods.sound_physics_perfected.ReverbHelpers.RoomVolumeData;
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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import com.redsmods.sound_physics_perfected.storageclasses.*;
import com.redsmods.sound_physics_perfected.wrappers.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
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

    // Queues and other lists that really aren't necessary lmao (i decided to care about readability rather than memory efficiency, sorry users' pcs
    public static final Queue<RedTickableInstance> tickQueue = new LinkedList<>();
    public static final Queue<RedPermeatedSoundInstance> permeatedTickQueue = new LinkedList<>();
    private static final ConcurrentHashMap<SoundData, Integer> entityRayHitCounts = new ConcurrentHashMap<>();
    public static final Queue<SoundData> soundQueue = new LinkedList<>();
    public static final Queue<SoundData> weatherQueue = new LinkedList<>();
    private static final double SPEED_OF_SOUND_TICKS = 17.15; // 17.15 blocks per gametick
    private static final Map<Integer,ArrayList<SoundInstance>> soundPlayingWaiting = new ConcurrentHashMap<>();
    private static int ticksSinceWorld;

    // Reverb Stuff
    private static final AtomicReference<Double> distanceFromWallEcho = new AtomicReference<>(0.0);
    private static final AtomicReference<Double> distanceFromWallEchoDenom = new AtomicReference<>(0.0);
    private static final AtomicInteger reverbStrength = new AtomicInteger(0);
    private static final AtomicInteger reverbDenom = new AtomicInteger(0);
    private static final AtomicInteger outdoorLeak = new AtomicInteger(0);
    private static final AtomicInteger outdoorLeakDenom = new AtomicInteger(0);
    private static final ConcurrentHashMap<String, ReverbSurfaceData> surfaceMaterials = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Vec3d, RoomVolumeData> roomVolumeCache = new ConcurrentHashMap<>();
    private static final AtomicInteger totalSurfaceArea = new AtomicInteger(0);
    private static final AtomicReference<Double> averageAbsorption = new AtomicReference<>(0.0);
    private static final AtomicReference<Double> roomVolume = new AtomicReference<>(0.0);
    private static final AtomicReference<Double> surfaceToVolumeRatio = new AtomicReference<>(0.0);
    private static final AtomicReference<Double> earlyReflectionStrength = new AtomicReference<>(0.0);
    private static final AtomicReference<Double> lateReflectionStrength = new AtomicReference<>(0.0);
    private static final AtomicReference<Double> weightedReverbStrength = new AtomicReference<>(0.0);
    private static final AtomicInteger earlyReflectionCount = new AtomicInteger(0);
    private static final AtomicInteger lateReflectionCount = new AtomicInteger(0);

    // Ray data
    private static final ConcurrentHashMap<SoundData, List<RayHitData>> rayHitsByEntity = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<SoundData, List<RayHitData>> redRaysToTarget = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<SoundData, AveragedSoundData> muffledAveragedResults = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<SoundInstance, SoundInstance> soundInstanceMap = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<SoundInstance, RedPermeatedSoundInstance> soundPermInstanceMap = new ConcurrentHashMap<>();

    // Thread pool for parallel ray processing
    private static final int THREAD_POOL_SIZE = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
    private static final ExecutorService raycastExecutor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    private static final ExecutorService soundProcessingExecutor = Executors.newFixedThreadPool(2);
    private static final AtomicBoolean isRaytracing = new AtomicBoolean(false);
    private static final AtomicBoolean freezeTickCounter = new AtomicBoolean(false);

    // Config Grabbed stuff
    private static int RAYS_CAST = Config.getInstance().raysCast;
    private static int MAX_BOUNCES = Config.getInstance().raysBounced;
    private static double RAY_SEGMENT_LENGTH = 16.0 * Config.getInstance().maxRayLength; // 12 chunk max length
    public static boolean ENABLE_REVERB = Config.getInstance().reverbEnabled;
    public static boolean ENABLE_PERMEATION = Config.getInstance().permeationEnabled;
    public static int TICK_RATE = Config.getInstance().tickRate;
    public static RedsAttenuationType ATTENUATION_TYPE = Config.getInstance().attenuationType;
    public static double PERMEATION_STEP_SIZE = Config.getInstance().permeationStepSize;

    static {
        surfaceMaterials.put("default", new ReverbSurfaceData(0.05, 0.7, "medium"));
    }

    // SoundSystemMixin Public static
    public static final Queue<RedPermeatedSoundInstance> FXQueue = new LinkedList<>();

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

            if (!isRaytracing.compareAndSet(false, true)) {
                return; // Already raytracing, ignore this call
            }

            Vec3d playerEyePos = player.getEyePos();
            double maxTotalDistance = RAY_SEGMENT_LENGTH * MAX_BOUNCES; // Max total distance after all bounces

            // Clear previous ray hit counts
            entityRayHitCounts.clear();

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getSoundManager() == null) {
                isRaytracing.set(false);
                return;
            }

            if (soundQueue.isEmpty() && tickQueue.isEmpty() && permeatedTickQueue.isEmpty()) {
                isRaytracing.set(false);
                return; // no sounds to proc
            }
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
            permeatedTickQueue.clear();
            isRaytracing.set(false);

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
        freezeTickCounter.set(true);
        for (AveragedSoundData avgData : averagedResults.values()) {
            CompletableFuture<Void> task = CompletableFuture.runAsync(() ->
                            playAveragedSoundWithAdjustments(client, avgData, playerEyePos, 1.0f, 1.0f),
                    soundProcessingExecutor);
            soundTasks.add(task);
        }

        if(ENABLE_PERMEATION) {
            for (AveragedSoundData avgData : muffledAveragedResults.values()) {
                CompletableFuture<Void> task = CompletableFuture.runAsync(() ->
                                playMuffled(client, avgData, playerEyePos, 1f, 1f),
                        soundProcessingExecutor);
                soundTasks.add(task);
            }
        }
        freezeTickCounter.set(false);

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
//                ((RedTickableInstance) originalSound).setPos(((RedTickableInstance) originalSound).getOriginalPosition());
                return;
            }
            // Calculate adjusted volume based on ray count and weight (confidence-based)
            float baseVolume;

            if (originalSound instanceof RedTickableInstance)
                baseVolume = ((RedTickableInstance) originalSound).getOriginalVolume();
            else
                baseVolume = ((RedSoundInstance) originalSound).original.getVolume();

            float confidenceMultiplier = (float) avgData.totalWeight / (float) RAYS_CAST*MAX_BOUNCES;
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
            SoundInstance originalSound = avgData.soundEntity.sound;
            Identifier soundId = originalSound.getId();

            // Calculate adjusted volume based on ray count and weight (confidence-based)
            float baseVolume;
            if (originalSound instanceof RedTickableInstance)
                baseVolume = ((RedTickableInstance) originalSound).getOriginalVolume();
            else
                baseVolume = ((RedSoundInstance) originalSound).original.getVolume();
            float confidenceMultiplier = (float) avgData.totalWeight / (float) RAYS_CAST*MAX_BOUNCES;
            float adjustedVolume = baseVolume * volumeMultiplier * confidenceMultiplier;
            if (confidenceMultiplier > 0.9) adjustedVolume = 0;

            // Calculate adjusted pitch
            float basePitch = originalSound.getPitch();
            float adjustedPitch = basePitch * pitchMultiplier;

            if (originalSound instanceof RedTickableInstance) { // update pos of sounds
                ((RedPermeatedSoundInstance) originalSound).setPos(targetPosition);
                ((RedPermeatedSoundInstance) originalSound).setVolume(Math.max(0.01f, Math.min(1.0f, adjustedVolume)));
                ((RedPermeatedSoundInstance) originalSound).setPermeationIndex(confidenceMultiplier);
                return;
            }

            RedPermeatedSoundInstance newSound;
            // Create positioned sound with adjustments
            newSound = new RedPermeatedSoundInstance(soundId,originalSound.getSound(),originalSound.getCategory(),targetPosition,Math.max(0.01f, Math.min(1.0f, adjustedVolume)),Math.max(0.5f, Math.min(2.0f, adjustedPitch)),originalSound, new Vec3d(originalSound.getX(), originalSound.getY(), originalSound.getZ()),baseVolume, confidenceMultiplier);
            soundPermInstanceMap.put(((RedSoundInstance) originalSound).getOriginal(), newSound);
//            if (adjustedVolume <= 0.01)
//                return;

            queueSound(newSound,(int) (avgData.averageDistance / SPEED_OF_SOUND_TICKS));

        } catch (Exception e) {
            System.err.println("Error playing adjusted averaged sound: " + e.getMessage());
        }
    }

    private static void queueSound(SoundInstance newSound, int distance) {
        soundPlayingWaiting.computeIfAbsent(ticksSinceWorld + 1, k -> new ArrayList<>()).add(newSound); // removed speed of sound calculation for delay.
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


        if (ENABLE_PERMEATION)
            castRedRay(world, player, startPos, soundQueue, totalDistanceTraveled, initialDirection);
        castGreenRay(world, player, startPos, soundQueue, totalDistanceTraveled, initialDirection);

        for (int bounce = 0; bounce <= MAX_BOUNCES && remainingDistance > 0; bounce++) {
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
                if (ENABLE_REVERB)
                    castBlueRay(world, player, actualEnd, soundQueue, totalDistanceTraveled, initialDirection,bounce);
                if (ENABLE_PERMEATION && bounce < 2) // only first 2 bounces cast permeating rays
                    castRedRay(world, player, actualEnd, soundQueue, totalDistanceTraveled, initialDirection);
                castGreenRay(world, player, actualEnd, soundQueue, totalDistanceTraveled, initialDirection);
            }

            if (hitBlock) {
                Vec3d hitPos = blockHit.getPos();
                Direction hitSide = blockHit.getSide();

                Vec3d reflectedDirection = calculateReflection(currentDirection, hitSide);

                currentPos = hitPos.add(reflectedDirection.multiply(0.01));
                currentDirection = reflectedDirection;
                remainingDistance -= segmentTraveled;
                outdoorLeakDenom.incrementAndGet();
            } else {
                for (SoundData soundEntity : weatherQueue) {
                    double weight;
                    if (ATTENUATION_TYPE == ATTENUATION_TYPE.INVERSE_SQUARE)
                        weight = 1.0 / (Math.max(totalDistanceTraveled - segmentTraveled, 0.1) * Math.max(totalDistanceTraveled - segmentTraveled, 0.1));
                    else
                        weight = 1.0 / Math.max(totalDistanceTraveled - segmentTraveled, 0.1);

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

                outdoorLeak.incrementAndGet();
                outdoorLeakDenom.incrementAndGet();

                return null; // make it so it doesn't continue bouncing bc it just doesn't work currently.
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
            SoundData data = new TickableSoundData(soundEntity, soundEntity.getOriginalPosition(), soundEntity.getSound().getIdentifier().toString());
            rayHitsByEntity.computeIfAbsent(data, k -> new CopyOnWriteArrayList<>());

            Vec3d entityCenter = soundEntity.getOriginalPosition();
            double distanceToEntity = currentPos.distanceTo(entityCenter);

            if (distanceToEntity + currentDistance > 16 * soundEntity.getOriginalVolume())
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
                        data,
                        entityCenter
                );

                RayHitData hitData = new RayHitData(GreenRayResult, initialDirection, weight);

                rayHitsByEntity.computeIfAbsent(data, k -> new CopyOnWriteArrayList<>()).add(hitData);
                entityRayHitCounts.merge(data, 1, Integer::sum);
            }
        }
    }

    private static boolean castBlueRay(World world, PlayerEntity player, Vec3d currentPos,
                                               Queue<SoundData> entities, double currentDistance,
                                               Vec3d initialDirection, int bounceNumber) {
        Vec3d entityCenter = player.getBoundingBox().getCenter();
        Vec3d adjustedPos = currentPos.add(entityCenter.subtract(currentPos).multiply(0.87));
        double distanceToEntity = adjustedPos.distanceTo(entityCenter);

        RaycastContext raycastContext = new RaycastContext(
                adjustedPos,
                entityCenter,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        );

        BlockHitResult blockHit = world.raycast(raycastContext);
        boolean hasLineOfSight = blockHit.getType() != HitResult.Type.BLOCK ||
                adjustedPos.distanceTo(blockHit.getPos()) >= distanceToEntity - 0.6;

        // Analyze surface material at bounce point
        if (bounceNumber <= 2) { // Only for early reflections
            analyzeSurfaceAtPosition(world, currentPos, currentDistance, hasLineOfSight);
        }

        // Calculate early vs late reflections
        double reflectionDelay = currentDistance / SPEED_OF_SOUND_TICKS;
        if (reflectionDelay < 0.05) { // Early reflections (< 50ms)
            earlyReflectionStrength.updateAndGet(current -> current + (hasLineOfSight ? 1.0 : 0.0));
            earlyReflectionCount.incrementAndGet();
        } else { // Late reflections
            lateReflectionStrength.updateAndGet(current -> current + (hasLineOfSight ? 0.6 : 0.0));
            lateReflectionCount.incrementAndGet();
        }

        reverbDenom.incrementAndGet();

        if (hasLineOfSight) {
            distanceFromWallEcho.updateAndGet(current -> current + currentDistance);
            distanceFromWallEchoDenom.updateAndGet(current -> current + 1.0);
            reverbStrength.incrementAndGet();

            // Calculate reflection angle for more accurate reverb
            Vec3d toPlayer = entityCenter.subtract(currentPos).normalize();
            Vec3d reflectionAngle = initialDirection.subtract(toPlayer);
            double angleDeviation = Math.abs(reflectionAngle.length());

            // Weight reverb by reflection quality (direct vs scattered)
            double reflectionQuality = Math.max(0.1, 1.0 - angleDeviation);
            weightedReverbStrength.updateAndGet(current -> current + reflectionQuality);
        }

        return hasLineOfSight;
    }

    private static void analyzeSurfaceAtPosition(World world, Vec3d pos, double distance, boolean hasLineOfSight) {
        BlockPos blockPos = new BlockPos((int)pos.x, (int)pos.y, (int)pos.z);
        BlockState blockState = world.getBlockState(blockPos);

        if (!blockState.isAir()) {
            String materialName = blockState.getBlock().getName().getString().toLowerCase();
            ReverbSurfaceData surfaceData = surfaceMaterials.getOrDefault(materialName,
                    surfaceMaterials.get("default"));

            // Weight by distance (closer surfaces have more impact)
            double distanceWeight = 1.0 / Math.max(distance, 1.0);

            averageAbsorption.updateAndGet(current -> current + (surfaceData.absorptionCoefficient * distanceWeight));
            totalSurfaceArea.updateAndGet(current -> current + 1); // Simplified surface area counting

            // Analyze room dimensions by checking surrounding blocks
            if (hasLineOfSight) {
                analyzeRoomDimensions(world, pos, blockPos);
            }
        }
    }

    private static void analyzeRoomDimensions(World world, Vec3d center, BlockPos hitPos) {
        // Quick room volume estimation by checking 6 directions
        double[] distances = new double[6];
        Vec3d[] directions = {
                new Vec3d(1, 0, 0), new Vec3d(-1, 0, 0),  // X axis
                new Vec3d(0, 1, 0), new Vec3d(0, -1, 0),  // Y axis
                new Vec3d(0, 0, 1), new Vec3d(0, 0, -1)   // Z axis
        };

        for (int i = 0; i < 6; i++) {
            distances[i] = measureDistanceToWall(world, center, directions[i], 16.0);
        }

        // Estimate room volume (simplified box model)
        double width = distances[0] + distances[1];
        double height = distances[2] + distances[3];
        double depth = distances[4] + distances[5];
        double estimatedVolume = width * height * depth;

        roomVolume.updateAndGet(current -> Math.max(current, estimatedVolume));

        // Calculate surface area to volume ratio for RT60 estimation
        double estimatedSurfaceArea = 2 * (width * height + width * depth + height * depth);
        if (estimatedVolume > 0) {
            surfaceToVolumeRatio.updateAndGet(current ->
                    Math.max(current, estimatedSurfaceArea / estimatedVolume));
        }
    }

    private static double measureDistanceToWall(World world, Vec3d start, Vec3d direction, double maxDistance) {
        for (double d = 1.0; d < maxDistance; d += 1.0) {
            Vec3d testPos = start.add(direction.multiply(d));
            BlockPos blockPos = new BlockPos((int)testPos.x, (int)testPos.y, (int)testPos.z);

            if (!world.getBlockState(blockPos).isAir()) {
                return d;
            }
        }
        return maxDistance; // Hit max distance, probably outdoor
    }

    // Enhanced reverb calculation method
    public static EnhancedReverbData calculateEnhancedReverb() {
        double totalSurface = totalSurfaceArea.get();
        double avgAbsorption = totalSurface > 0 ? averageAbsorption.get() / totalSurface : 0.05;
        double volume = roomVolume.get();
        double surfaceToVolRatio = surfaceToVolumeRatio.get();

        // Calculate RT60 using Sabine's formula: RT60 = 0.161 * V / A
        // Where V is volume and A is total absorption
        double totalAbsorption = totalSurface * avgAbsorption;
        double rt60 = totalAbsorption > 0 ? (0.161 * volume) / totalAbsorption : 0.0;
        rt60 = Math.min(rt60, 8.0); // Cap at 8 seconds for gameplay

        // Early reflection delay based on room size
        double roomRadius = Math.cbrt(volume * 3.0 / (4.0 * Math.PI)); // Sphere equivalent radius
        double earlyReflectionDelay = roomRadius / SPEED_OF_SOUND_TICKS;

        // Late reflection strength
        double earlyStrength = earlyReflectionCount.get() > 0 ?
                earlyReflectionStrength.get() / earlyReflectionCount.get() : 0.0;
        double lateStrength = lateReflectionCount.get() > 0 ?
                lateReflectionStrength.get() / lateReflectionCount.get() : 0.0;

        // Determine acoustic profile
        String acousticProfile = determineAcousticProfile(avgAbsorption, surfaceToVolRatio, volume);

        // Determine if indoors (high surface to volume ratio indicates enclosed space)
        boolean isIndoors = surfaceToVolRatio > 0.5 && volume < 8000;

        return new EnhancedReverbData(rt60, earlyReflectionDelay, lateStrength,
                roomRadius, avgAbsorption, acousticProfile, isIndoors);
    }

    private static String determineAcousticProfile(double absorption, double surfaceToVolRatio, double volume) {
        if (volume > 10000) return "cathedral"; // Large reverberant space
        if (absorption > 0.6) return "padded_room"; // Highly absorptive
        if (absorption < 0.1 && surfaceToVolRatio < 0.3) return "gymnasium"; // Hard surfaces, large space
        if (surfaceToVolRatio > 1.0) return "small_room"; // Cramped space
        if (absorption > 0.3) return "living_room"; // Mixed materials
        return "generic_room";
    }

    // Reverb getters
    public static EnhancedReverbData getEnhancedReverbData() {
        return calculateEnhancedReverb();
    }

    public static double getWeightedReverbStrength() {
        return weightedReverbStrength.get();
    }

    public static double getEarlyReflectionRatio() {
        int totalEarly = earlyReflectionCount.get();
        int totalLate = lateReflectionCount.get();
        int total = totalEarly + totalLate;
        return total > 0 ? (double) totalEarly / total : 0.0;
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

            double blockCount = countBlocksBetween(world, currentPos, entityCenter, player);
//            if (blockCount == 0)
//                continue;

            double blockAttenuation = Math.pow(0.7, blockCount); // 0.7 is how much it'll lower the gain by, keep in mind the muffle fx is still seperate
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
        for (RedPermeatedSoundInstance soundEntity : permeatedTickQueue) {
            Vec3d entityCenter = soundEntity.getOriginalPosition();
            SoundData data = new TickableSoundData(soundEntity, soundEntity.getOriginalPosition(), soundEntity.getSound().getIdentifier().toString());
            double distanceToEntity = currentPos.distanceTo(entityCenter);

            if (distanceToEntity + currentDistance > 16 * soundEntity.getOriginalVolume())
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

            double blockCount = countBlocksBetween(world, currentPos, entityCenter, player);
//            if (blockCount == 0)
//                continue;

            double blockAttenuation = Math.pow(0.7, blockCount); // 0.7 is how much it'll lower the gain by, keep in mind the muffle fx is still seperate
            double weight = blockAttenuation / (Math.max(distanceToEntity, 0.1) * Math.max(distanceToEntity, 0.1));

            RaycastResult rayResult = new RaycastResult(
                    distanceToEntity,
                    initialDirection,
                    data,
                    entityCenter
            );
            RayHitData hitData = new RayHitData(rayResult, initialDirection, weight);

            redRaysToTarget.computeIfAbsent(data, k -> new CopyOnWriteArrayList<>()).add(hitData);
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


    private static double countBlocksBetween(World world, Vec3d start, Vec3d end, PlayerEntity player) {
        double totalDistanceInBlocks = 0;
        Vec3d currentStart = start;

        while (totalDistanceInBlocks < 3) {
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
                // Calculate the distance traveled through this specific block
                Vec3d direction = end.subtract(currentStart).normalize();

                // Get the block's bounding box
                VoxelShape blockShape = blockState.getOutlineShape(world, hitBlockPos);
                Box blockBounds;
                if (!blockShape.isEmpty()) {
                    blockBounds = blockShape.getBoundingBox().offset(hitBlockPos);
                } else {
                    blockBounds = new Box(
                            hitBlockPos.getX(), hitBlockPos.getY(), hitBlockPos.getZ(),
                            hitBlockPos.getX() + 1, hitBlockPos.getY() + 1, hitBlockPos.getZ() + 1
                    );
                }

                // Find the exit point by moving along the ray direction until we're outside the block
                Vec3d exitPoint = hit.getPos();
                double step = PERMEATION_STEP_SIZE; // Small step size for precision

                while (blockBounds.contains(exitPoint)) {
                    exitPoint = exitPoint.add(direction.multiply(step));
                }

                // Calculate distance traveled within this block
                double distanceInBlock = hit.getPos().distanceTo(exitPoint);
                totalDistanceInBlocks += distanceInBlock;

                // Add a small buffer to ensure we're clearly outside
                currentStart = exitPoint.add(direction.multiply(0.01));
            } else {
                throw new RuntimeException("Why tf is the raycasting getting stuck within air... WHAT HAVE YOU DONE!!!");
            }

            // Check if we've passed the end point
            if (currentStart.distanceTo(start) >= end.distanceTo(start)) {
                break;
            }
        }
        return totalDistanceInBlocks;
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
        if (freezeTickCounter.get())
            return;
        ticksSinceWorld++;
        if (!soundPlayingWaiting.containsKey((Integer) ticksSinceWorld))
            return;

        MinecraftClient client = MinecraftClient.getInstance();
        ArrayList<SoundInstance> sound = soundPlayingWaiting.get((Integer) ticksSinceWorld);
        for (SoundInstance newSound : sound) {
            if (newSound == null)
                continue;
            client.getSoundManager().play(newSound);
        }
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