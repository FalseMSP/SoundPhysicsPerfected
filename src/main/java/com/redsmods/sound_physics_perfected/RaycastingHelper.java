package com.redsmods.sound_physics_perfected;

import com.redsmods.sound_physics_perfected.ReverbHelpers.EnhancedReverbData;
import com.redsmods.sound_physics_perfected.ReverbHelpers.ReverbSurfaceData;
import com.redsmods.sound_physics_perfected.ReverbHelpers.RoomVolumeData;
import com.redsmods.sound_physics_perfected.config.Config;
import com.redsmods.sound_physics_perfected.config.DebugType;
import com.redsmods.sound_physics_perfected.config.RedsAttenuationType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
//? if neoforge && =1.21.1 {
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
//?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.redsmods.sound_physics_perfected.storageclasses.*;
import com.redsmods.sound_physics_perfected.wrappers.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Unique;

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
    public static final Queue<SoundData> soundQueue = new LinkedList<>();
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
    private static final AtomicInteger totalRaysHitSurface = new AtomicInteger(0);
    private static final ConcurrentHashMap<String, ReverbSurfaceData> surfaceMaterials = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Vec3, RoomVolumeData> roomVolumeCache = new ConcurrentHashMap<>();
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
    public static final ConcurrentHashMap<SoundInstance, RedTickableInstance> soundInstanceMap = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<SoundInstance, RedPermeatedSoundInstance> soundPermInstanceMap = new ConcurrentHashMap<>();

    // Thread pool for parallel ray processing
    private static final int THREAD_POOL_SIZE = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
    private static final ExecutorService raycastExecutor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    private static final ExecutorService soundProcessingExecutor = Executors.newFixedThreadPool(2);
    private static final AtomicBoolean isRaytracing = new AtomicBoolean(false);
    public static Vec3 playerEyePos = new Vec3(0,0,0);
    private static Vec3[] rayDirections;
    private static int lastRaysCast = -1;
    private static List<CompletableFuture<Void>> rayTasks;

    static {
        surfaceMaterials.put("default", new ReverbSurfaceData(0.05, 0.7, "medium"));
    }

    // SoundSystemMixin Public statics
    public static final Queue<RedPermeatedSoundInstance> FXQueue = new LinkedList<>();
    @Unique
    public static OpenALEffectsHandler fxHandler = new OpenALEffectsHandler();

    // Entrypoint #1
    public static void castBouncingRaysAndDetectSFX(Level world, Player player) {
        try {

            playerEyePos = player.getEyePosition();
            double maxTotalDistance = 16.0 * Config.getInstance().maxRayLength * Config.getInstance().raysBounced; // Max total distance after all bounces

            Minecraft client = Minecraft.getInstance();
            if (client == null || client.getSoundManager() == null) {
                return;
            }


            // Generate ray directions
            if (Config.getInstance().raysCast != lastRaysCast)
                rayDirections = RaycastingHelper.generateRayDirections();
            lastRaysCast = Config.getInstance().raysCast;

            if (processAndPlayAveragedSounds(world,player,playerEyePos,new ArrayList<>(Arrays.asList(rayDirections)), maxTotalDistance,client) == -1)
                return;

            rayHitsByEntity.clear(); // clear list after every call
            redRaysToTarget.clear(); // wow, this was the issue? I feel like a real dumbass now D:
            isRaytracing.set(false);

        } catch (Exception e) {
            System.err.println("Error in player bouncing ray entity detection: " + e.getMessage());
        }
    }

    public static void playQueuedObjects() {
        ticksSinceWorld++;

        if (soundPlayingWaiting.isEmpty())
            return;

        Minecraft client = Minecraft.getInstance();

        // Create a snapshot of all sounds to play
        List<SoundInstance> soundsToPlay = new ArrayList<>();

        for (ArrayList<SoundInstance> soundList : soundPlayingWaiting.values()) {
            synchronized (soundList) {  // Synchronize access to the list
                soundsToPlay.addAll(soundList);
            }
        }

        // Play all sounds from the snapshot
        for (SoundInstance newSound : soundsToPlay) {
            if (newSound == null)
                continue;
            client.getSoundManager().play(newSound);
        }

        // Clear the entire map after playing all sounds
        soundPlayingWaiting.clear();
    }

    public static int processAndPlayAveragedSounds(Level world, Player player, Vec3 playerEyePos,
                                                    List<Vec3> rayDirections,
                                                    double maxTotalDistance, Minecraft client) {

         if (processRaysWithAveraging(world, player, playerEyePos, rayDirections, maxTotalDistance) == -1) // return if still processing.
             return -1;

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

        if (averagedResults.isEmpty() && muffledAveragedResults.isEmpty()) {
            return 0;
        }

        List<CompletableFuture<Void>> soundTasks = new ArrayList<>();
        if(!Config.getInstance().permeation) { // force no regular processing if permeation is enabled
            for (AveragedSoundData avgData : averagedResults.values()) {
                CompletableFuture<Void> task = CompletableFuture.runAsync(() ->
                                playAveragedSoundWithAdjustments(client, avgData, playerEyePos, 1.0f, 1.0f),
                        soundProcessingExecutor);
                soundTasks.add(task);
            }
        }

        if(Config.getInstance().permeation) {
            for (AveragedSoundData avgData : muffledAveragedResults.values()) {
                CompletableFuture<Void> task = CompletableFuture.runAsync(() ->
                                playMuffled(client, avgData, playerEyePos, 1f, 1f, world, player),
                        soundProcessingExecutor);
                soundTasks.add(task);
            }
        }

        // Wait for all sound processing to complete
        CompletableFuture.allOf(soundTasks.toArray(new CompletableFuture[0])).join();
        return 0;
    }

    // Advanced method with volume and pitch adjustment based on confidence
    public static void playAveragedSoundWithAdjustments(Minecraft client, AveragedSoundData avgData, Vec3 playerPos,
                                                        float volumeMultiplier, float pitchMultiplier) {
        if (client == null || client.level == null || avgData == null) {
            return;
        }

        try {
            // Calculate the target position
            Vec3 targetPosition = playerPos.add(avgData.averageDirection.scale(avgData.averageDistance));
            if (avgData.totalWeight == 0)
                targetPosition = avgData.soundEntity.position; // make sound appear at its original source

            // Get original sound properties
            SoundInstance originalSound = avgData.soundEntity.sound;
            ResourceLocation soundId = originalSound.getLocation();
            if(avgData.totalWeight == 0 && originalSound instanceof RedTickableInstance) {
                ((RedTickableInstance) originalSound).setTargetVolume(0);
                ((RedTickableInstance) originalSound).setTargetPosition(((RedTickableInstance) originalSound).getOriginalPosition());
                ((RedTickableInstance) originalSound).setAttenuationMultiplier(0);
                return;
            }
            // Calculate adjusted volume based on ray count and weight (confidence-based)
            float baseVolume;

            if (originalSound instanceof RedTickableInstance)
                baseVolume = ((RedTickableInstance) originalSound).getOriginalVolume();
            else
                baseVolume = ((RedSoundInstance) originalSound).original.getVolume();

            float confidenceMultiplier;
            float attenuationMultiplier = 1;
            if (Config.getInstance().attenuationType == RedsAttenuationType.VERCIDIUM_INVERSE_SQUARE) {
                confidenceMultiplier = (float) avgData.rayCount / Math.max(totalRaysHitSurface.get(), 1);
                attenuationMultiplier = 1.0f / (float) Math.pow(Math.max(avgData.averageDistance,0.01),2);
            } else if (Config.getInstance().attenuationType == RedsAttenuationType.VERCIDIUM_LINEAR) {
                confidenceMultiplier = (float) avgData.rayCount / Math.max(totalRaysHitSurface.get(), 1);
                attenuationMultiplier = 1.0f / (float) Math.max(avgData.averageDistance,0.01);
            }
            else // maintain old behavior if someone still wants it
                confidenceMultiplier = (float) avgData.totalWeight / Config.getInstance().raysCast * Config.getInstance().raysBounced;
            float adjustedVolume = baseVolume * volumeMultiplier * confidenceMultiplier * attenuationMultiplier;

            // Calculate adjusted pitch
            float basePitch = originalSound.getPitch();
            float adjustedPitch = basePitch * pitchMultiplier;
            RedTickableInstance newSound;

            // Create positioned sound with adjustments
            if (originalSound instanceof RedTickableInstance) { // update pos of sounds
                ((RedTickableInstance) originalSound).setTargetPosition(targetPosition);
                ((RedTickableInstance) originalSound).setTargetVolume(Math.max(0.01f, Math.min(1.0f, adjustedVolume)));
                ((RedTickableInstance) originalSound).setAttenuationMultiplier(attenuationMultiplier);
                return;
            } else if (((RedSoundInstance) originalSound) instanceof TickableSoundInstance) {
                newSound = new RedTickableInstance(soundId,originalSound.getSound(),originalSound.getSource(),targetPosition,Math.max(0.001f, Math.min(1.0f, adjustedVolume)),Math.max(0.5f, Math.min(2.0f, adjustedPitch)),originalSound, attenuationMultiplier);
            } else {
                newSound = new RedTickableInstance(soundId,originalSound.getSound(),originalSound.getSource(),targetPosition,Math.max(0.001f, Math.min(1.0f, adjustedVolume)),Math.max(0.5f, Math.min(2.0f, adjustedPitch)),originalSound, attenuationMultiplier);
            }

            soundInstanceMap.put(((RedSoundInstance) originalSound).getOriginal(),newSound);
            if (Config.getInstance().debug == DebugType.ACTION_BAR) client.player.displayClientMessage(Component.literal(((RedSoundInstance) originalSound).getOriginal().toString()), true);
            else if (Config.getInstance().debug == DebugType.CHAT) client.player.displayClientMessage(Component.literal(((RedSoundInstance) originalSound).getOriginal().toString()), false);

            queueSound(newSound);

        } catch (Exception e) {
            System.err.println("Error playing adjusted averaged sound: " + e.getMessage());
        }
    }

    public static void playMuffled(Minecraft client, AveragedSoundData avgData, Vec3 playerPos,
                                   float volumeMultiplier, float pitchMultiplier, Level world, Player player) {
        if (client == null || client.level == null || avgData == null) {
            return;
        }

        try {
            // Calculate the target position
            Vec3 targetPosition = playerPos.add(avgData.averageDirection.scale(avgData.averageDistance));
            if (avgData.totalWeight == 0)
                targetPosition = avgData.soundEntity.position; // make sound stay where it was last tick.

            // Get original sound properties
            SoundInstance originalSound = avgData.soundEntity.sound;
            ResourceLocation soundId = originalSound.getLocation();

            // Calculate adjusted volume based on ray count and weight (confidence-based)
            float baseVolume;
            if (originalSound instanceof RedTickableInstance)
                baseVolume = ((RedTickableInstance) originalSound).getOriginalVolume();
            else
                baseVolume = ((RedSoundInstance) originalSound).original.getVolume();

            float confidenceMultiplier;
            float attenuationMultiplier = 1;
            if (Config.getInstance().attenuationType == RedsAttenuationType.VERCIDIUM_INVERSE_SQUARE) {
                confidenceMultiplier = (float) avgData.rayCount / Math.max(totalRaysHitSurface.get(), 1);
                attenuationMultiplier = 1.0f / (float) Math.pow(Math.max(avgData.averageDistance,0.01),2);
            } else if (Config.getInstance().attenuationType == RedsAttenuationType.VERCIDIUM_LINEAR) {
                confidenceMultiplier = (float) avgData.rayCount / Math.max(totalRaysHitSurface.get(), 1);
                attenuationMultiplier = 1.0f / (float) Math.max(avgData.averageDistance,0.01);
            }
            else // maintain old behavior if someone still wants it
                confidenceMultiplier = (float) avgData.totalWeight / Config.getInstance().raysCast * Config.getInstance().raysBounced;
            float permeationIndex = (float) avgData.averageMuffle;

            // Shortcut Directionality Logic
            if(Config.getInstance().shortcutDirectionality && hasLineOfSight(world, player, originalSound) && originalSound instanceof RedTickableInstance) {
                targetPosition = ((RedTickableInstance) originalSound).getOriginalPosition();
                // Force confidence to 1 if LOS is there
                confidenceMultiplier = 1; // bc 100% confident or whatever
            } else if (Config.getInstance().shortcutDirectionality && hasLineOfSight(world, player, originalSound)){
                targetPosition = new Vec3(originalSound.getX(),originalSound.getY(),originalSound.getZ());
                confidenceMultiplier = 1;
            }

            float adjustedVolume = baseVolume * volumeMultiplier * confidenceMultiplier * attenuationMultiplier * permeationIndex;

            // Calculate adjusted pitch
            float basePitch = originalSound.getPitch();
            float adjustedPitch = basePitch * pitchMultiplier;

            // make it so it mutes sounds when no rays make it
            if(avgData.totalWeight == 0 && originalSound instanceof RedPermeatedSoundInstance) {
                ((RedPermeatedSoundInstance) originalSound).setTargetVolume(0);
                ((RedPermeatedSoundInstance) originalSound).setTargetPosition(((RedTickableInstance) originalSound).getOriginalPosition());
                ((RedPermeatedSoundInstance) originalSound).setPermeationIndex(0);
                ((RedTickableInstance) originalSound).setAttenuationMultiplier(0);
                return;
            }

            if (originalSound instanceof RedTickableInstance) { // update pos of sounds
                ((RedPermeatedSoundInstance) originalSound).setTargetPosition(targetPosition);
                ((RedPermeatedSoundInstance) originalSound).setTargetVolume(adjustedVolume);
                ((RedPermeatedSoundInstance) originalSound).setPermeationIndex(permeationIndex);
                ((RedTickableInstance) originalSound).setAttenuationMultiplier(attenuationMultiplier);
                return;
            }

            RedPermeatedSoundInstance newSound;
            // Create positioned sound with adjustments
            newSound = new RedPermeatedSoundInstance(soundId,originalSound.getSound(),originalSound.getSource(),targetPosition,Math.max(0.01f, adjustedVolume),adjustedPitch,originalSound, permeationIndex, attenuationMultiplier);
            soundPermInstanceMap.put(((RedSoundInstance) originalSound).getOriginal(), newSound);

            if (Config.getInstance().debug == DebugType.ACTION_BAR) client.player.displayClientMessage(Component.literal(((RedSoundInstance) originalSound).getOriginal().toString()), true);
            else if (Config.getInstance().debug == DebugType.CHAT) client.player.displayClientMessage(Component.literal(((RedSoundInstance) originalSound).getOriginal().toString()), false);

            queueSound(newSound);

        } catch (Exception e) {
            System.err.println("Error playing adjusted averaged sound: " + e.getMessage());
        }
    }

    private static void queueSound(SoundInstance newSound) {
        soundPlayingWaiting.computeIfAbsent(ticksSinceWorld + 1, k -> new ArrayList<>()).add(newSound);
    }

    public static int processRaysWithAveraging(Level world, Player player,
                                                                             Vec3 playerEyePos, List<Vec3> rayDirections,
                                                                             double maxTotalDistance) {
        if (isRaytracing.compareAndSet(false, true)) {
            // create tasks
            final ConcurrentLinkedQueue<SoundData> sQ = new ConcurrentLinkedQueue<>(soundQueue);
            soundQueue.clear();
            final ConcurrentLinkedQueue<RedTickableInstance> tQ = new ConcurrentLinkedQueue<>(tickQueue);
            tickQueue.clear();
            final ConcurrentLinkedQueue<RedPermeatedSoundInstance> pTQ = new ConcurrentLinkedQueue<>(permeatedTickQueue);
            permeatedTickQueue.clear();
            rayTasks = createRayTasks(world, player, playerEyePos, rayDirections, sQ, tQ, pTQ, maxTotalDistance);
        }

        // Wait for all ray casting to complete
        if (!CompletableFuture.allOf(rayTasks.toArray(new CompletableFuture[0])).isDone())
            return -1;
        return 0;
    }

    private static List<CompletableFuture<Void>> createRayTasks(Level world, Player player,
                                                                Vec3 playerEyePos, List<Vec3> rayDirections,
                                                                Queue<SoundData> sq, Queue<RedTickableInstance> tQ, Queue<RedPermeatedSoundInstance> pTQ, double maxTotalDistance) {
        // Reset atomic variables
        reverbStrength.set(0);
        weightedReverbStrength.set(0.0);
        distanceFromWallEcho.set(0.0);
        distanceFromWallEchoDenom.set(0.0);
        reverbDenom.set(0);
        lateReflectionCount.set(0);
        earlyReflectionStrength.set(0.0);
        lateReflectionStrength.set(0.0);
        earlyReflectionCount.set(0);
        outdoorLeak.set(0);
        outdoorLeakDenom.set(0);
        totalRaysHitSurface.set(0);
        totalSurfaceArea.set(0);
        averageAbsorption.set(0.0);

        // Divide rays into chunks for parallel processing
        int raysPerChunk = Math.max(1, rayDirections.size() / THREAD_POOL_SIZE);
        List<List<Vec3>> rayChunks = new ArrayList<>();

        for (int i = 0; i < rayDirections.size(); i += raysPerChunk) {
            int endIndex = Math.min(i + raysPerChunk, rayDirections.size());
            rayChunks.add(rayDirections.subList(i, endIndex));
        }

        // Submit ray casting tasks
        List<CompletableFuture<Void>> rayTasks = new ArrayList<>();

        for (List<Vec3> rayChunk : rayChunks) {
            CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
                for (Vec3 direction : rayChunk) {
                    castBouncingRay(world, player, playerEyePos, direction, sq, tQ, pTQ, maxTotalDistance);
                }
            }, raycastExecutor);
            rayTasks.add(task);
        }
        return rayTasks;
    }

    public static void castBouncingRay(Level world, Player player, Vec3 startPos, Vec3 direction,
                                                Queue<SoundData> sQ, Queue<RedTickableInstance> tQ, Queue<RedPermeatedSoundInstance> pTQ, double maxTotalDistance) {
        Vec3 currentPos = startPos;
        Vec3 currentDirection = direction.normalize();
        Vec3 initialDirection = currentDirection.normalize();
        double remainingDistance = maxTotalDistance;
        double totalDistanceTraveled = 0.0;


        if (Config.getInstance().permeation)
            castRedRay(world, player, startPos, sQ, tQ, pTQ, totalDistanceTraveled, initialDirection, -1);
        else
            castGreenRay(world, player, startPos, sQ, tQ, pTQ, totalDistanceTraveled, initialDirection, -1);

        for (int bounce = 0; bounce <= Config.getInstance().raysBounced && remainingDistance > 0; bounce++) {
            double segmentDistance = Math.min(16.0 * Config.getInstance().maxRayLength, remainingDistance);
            Vec3 segmentEnd = currentPos.add(currentDirection.scale(segmentDistance));

            ClipContext raycastContext = new ClipContext(
                    currentPos,
                    segmentEnd,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
            ) {
                @Override
                public VoxelShape getBlockShape(BlockState blockState, BlockGetter level, BlockPos pos) {
                    if (blockState.getBlock() == Blocks.BARRIER && Config.getInstance().barrierAsAir) {
                        return Shapes.empty();
                    }
                    return super.getBlockShape(blockState, world, pos);
                }
            };;

            BlockHitResult blockHit = world.clip(raycastContext);

            Vec3 actualEnd = segmentEnd;
            boolean hitBlock = false;

            if (blockHit.getType() == HitResult.Type.BLOCK) {
                actualEnd = blockHit.getLocation();
                hitBlock = true;
            }

            double segmentTraveled = currentPos.distanceTo(actualEnd);
            totalDistanceTraveled += segmentTraveled;
            // bounce mult
            totalDistanceTraveled *= 1.0/Config.getInstance().bounceAbsorptionMultiplier; // make it so bounce mult is whatever doesn't get absorbed (hmm realism?!)

            if (hitBlock) {
                if (Config.getInstance().reverb) {
                    BlueRayResult blueRayResult = castBlueRay(world, player, actualEnd, sQ, totalDistanceTraveled, initialDirection, bounce);
                    if (blueRayResult.arrived) { // cast blue ray and if it makes it back to the player
                        // make it update that as initial direction + set totalDistance
                        initialDirection = blueRayResult.directionFromPlayer;
                        totalDistanceTraveled = blueRayResult.distance;
                        totalDistanceTraveled *= 1.0/Config.getInstance().bounceAbsorptionMultiplier; // make it so bounce mult is whatever doesn't get absorbed (hmm realism?!)
                    }
                }
                if (Config.getInstance().permeation)
                    castRedRay(world, player, actualEnd, sQ, tQ, pTQ, totalDistanceTraveled, initialDirection, bounce);
                else
                    castGreenRay(world, player, actualEnd, sQ, tQ, pTQ, totalDistanceTraveled, initialDirection, bounce);
            }
            if (hitBlock) {
                Vec3 hitPos = blockHit.getLocation();
                Direction hitSide = blockHit.getDirection();

                Vec3 reflectedDirection = calculateReflection(currentDirection, hitSide);

                currentPos = hitPos.add(reflectedDirection.scale(0.01));
                currentDirection = reflectedDirection;
                remainingDistance -= segmentTraveled;
                totalRaysHitSurface.incrementAndGet();
                outdoorLeakDenom.incrementAndGet();
            } else {
                Vec3 toCenter = player.position().subtract(actualEnd);
                Vec3 normal = toCenter.normalize();
                Vec3 reflectedDirection = calculateReflection(currentDirection, normal);

                currentPos = segmentEnd.add(reflectedDirection.scale(0.01));
                currentDirection = reflectedDirection;
                remainingDistance -= segmentTraveled;

                outdoorLeak.incrementAndGet();
                outdoorLeakDenom.incrementAndGet();
                if (bounce == 0)
                    return;
            }
        }
    }

    private static void castGreenRay(Level world, Player player, Vec3 currentPos, Queue<SoundData> sQ, Queue<RedTickableInstance> tQ, Queue<RedPermeatedSoundInstance> pTQ,
                                     double currentDistance, Vec3 initialDirection, int bounces) {
        for (SoundData soundEntity : sQ) {
            rayHitsByEntity.computeIfAbsent(soundEntity, k -> new CopyOnWriteArrayList<>()); // make sure all sounds are proc'd even if they aren't audible at first (makes discs work lmao)
            //? if neoforge && =1.21.1 {
             Vec3 entityCenter = SableCompanion.INSTANCE.getContaining(world, soundEntity.position) != null
                     ? SableCompanion.INSTANCE.getContaining(world, soundEntity.position).logicalPose().transformPosition(soundEntity.position)
                     : soundEntity.position;
            //? } else {
//            Vec3 entityCenter = soundEntity.position;
            //? }
            double distanceToEntity = currentPos.distanceTo(entityCenter);

            if (distanceToEntity + currentDistance > 16 * soundEntity.sound.getVolume())
                continue;

            ClipContext raycastContext = new ClipContext(
                    currentPos,
                    entityCenter,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
            );

            BlockHitResult blockHit = world.clip(raycastContext);

            boolean hasLineOfSight = blockHit.getType() != HitResult.Type.BLOCK ||
                    currentPos.distanceTo(blockHit.getLocation()) >= distanceToEntity - 1;

            if (hasLineOfSight) {
                double weight = getWeight(currentDistance,0,distanceToEntity);

                RaycastResult GreenRayResult = new RaycastResult(
                        distanceToEntity,
                        initialDirection,
                        soundEntity
                );

                Vec3 direction = entityCenter.subtract(currentPos);
                Vec3 normalVector = direction.normalize();
                RayHitData hitData = new RayHitData(GreenRayResult, normalVector, weight, 0, bounces);

                rayHitsByEntity.computeIfAbsent(soundEntity, k -> new CopyOnWriteArrayList<>()).add(hitData);
            }
        }

        // Handle tickable sounds
        for (RedTickableInstance soundEntity : tQ) {
            SoundData data = new TickableSoundData(soundEntity, soundEntity.getOriginalPosition(), soundEntity.getSound().toString());
            rayHitsByEntity.computeIfAbsent(data, k -> new CopyOnWriteArrayList<>());

            Vec3 entityCenter = soundEntity.getOriginalPosition();
            double distanceToEntity = currentPos.distanceTo(entityCenter);

            if (distanceToEntity + currentDistance > 16 * soundEntity.getOriginalVolume())
                continue;

            ClipContext raycastContext = new ClipContext(
                    currentPos,
                    entityCenter,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
            );

            BlockHitResult blockHit = world.clip(raycastContext);

            boolean hasLineOfSight = blockHit.getType() != HitResult.Type.BLOCK ||
                    currentPos.distanceTo(blockHit.getLocation()) >= distanceToEntity - 1;

            if (hasLineOfSight) {
                double weight = getWeight(currentDistance,0,distanceToEntity);

                RaycastResult GreenRayResult = new RaycastResult(
                        distanceToEntity,
                        initialDirection,
                        data
                );

                Vec3 direction = entityCenter.subtract(currentPos);
                Vec3 normalVector = direction.normalize();
                RayHitData hitData = new RayHitData(GreenRayResult, normalVector, weight, 0, bounces);

                rayHitsByEntity.computeIfAbsent(data, k -> new CopyOnWriteArrayList<>()).add(hitData);
            }
        }
    }

    private static BlueRayResult castBlueRay(Level world, Player player, Vec3 currentPos,
                                               Queue<SoundData> entities, double currentDistance,
                                               Vec3 initialDirection, int bounceNumber) {
        Vec3 entityCenter = player.getBoundingBox().getCenter();
        Vec3 toPlayer = entityCenter.subtract(currentPos).normalize();
        Vec3 rayStartPos = currentPos.add(toPlayer.scale(0.1));
        double distanceToEntity = rayStartPos.distanceTo(entityCenter);

        ClipContext raycastContext = new ClipContext(
                rayStartPos,
                entityCenter,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        );

        BlockHitResult blockHit = world.clip(raycastContext);
        boolean hasLineOfSight = blockHit.getType() != HitResult.Type.BLOCK;

        // Analyze surface material at bounce point
        if (bounceNumber <= 2) { // Only for early reflections
            analyzeSurfaceAtPosition(world, currentPos, currentDistance);
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
            Vec3 playerToAdjustedPos = rayStartPos.subtract(entityCenter);
            Vec3 reflectionAngle = initialDirection.subtract(toPlayer);
            double angleDeviation = Math.abs(reflectionAngle.length());

            // Weight reverb by reflection quality (direct vs scattered)
            double reflectionQuality = Math.max(0.1, 1.0 - angleDeviation);
            weightedReverbStrength.updateAndGet(current -> current + reflectionQuality);
            return new BlueRayResult(true, playerToAdjustedPos, entityCenter.distanceTo(rayStartPos));
        }

        return new BlueRayResult(false,null, -1);
    }

    private static double getAbsorptionCoeficient(Level world, Vec3 pos) {
        if (!Config.getInstance().useExplosionResistance)
            return 1;
        BlockPos blockPos = new BlockPos(
                (int)Math.floor(pos.x),
                (int)Math.floor(pos.y),
                (int)Math.floor(pos.z)
        );
        BlockState blockState = world.getBlockState(blockPos);

        if (!blockState.isAir()) {
            double absorptionCoefficient = 0.0;
            if(blockState.is(BlockTags.DAMPENS_VIBRATIONS) || blockState.is(BlockTags.OCCLUDES_VIBRATION_SIGNALS))
                absorptionCoefficient = Config.getInstance().maxBlocksPermeated;
            else
                absorptionCoefficient = Math.min(blockState.getBlock().getExplosionResistance()/6,5.0); // deepslate is default 1

            return absorptionCoefficient;
        }
        return 1; // smth went wrong.
    }

    private static void analyzeSurfaceAtPosition(Level world, Vec3 pos, double distance) {
        BlockPos blockPos = new BlockPos((int)pos.x, (int)pos.y, (int)pos.z);
        BlockState blockState = world.getBlockState(blockPos);

        if (!blockState.isAir()) {
            String materialName = blockState.getBlock().getName().getString().toLowerCase();
            ReverbSurfaceData surfaceData = surfaceMaterials.getOrDefault(materialName,
                    surfaceMaterials.get("default")); // SEE I TOLD YOU I HAVE IT IN CODE, I JUST AM WAY TOO LAZY TO MAKE IT ACTUALLY DO SMTH

            if(blockState.is(BlockTags.DAMPENS_VIBRATIONS) || blockState.is(BlockTags.OCCLUDES_VIBRATION_SIGNALS))
                surfaceData.absorptionCoefficient = Config.getInstance().maxBlocksPermeated;
            else
                surfaceData.absorptionCoefficient = Math.min(blockState.getBlock().getExplosionResistance()/6,5.0);

            // Weight by distance (closer surfaces have more impact)
            double distanceWeight = 1.0 / Math.max(distance, 1.0);

            averageAbsorption.updateAndGet(current -> current + (surfaceData.absorptionCoefficient * distanceWeight));
            totalSurfaceArea.updateAndGet(current -> current + 1); // Simplified surface area counting
        }
    }

    // Enhanced reverb calculation method
    public static EnhancedReverbData calculateEnhancedReverb() {
        double totalSurface = totalSurfaceArea.get();
        double avgAbsorption = totalSurface > 0 ? averageAbsorption.get() / totalSurface : 0.05;
        double volume = Math.pow((double) distanceFromWallEcho.get() / (double) distanceFromWallEchoDenom.get(),3); // just assume cube room.
        double surfaceToVolRatio = surfaceToVolumeRatio.get();

        // Calculate RT60 using Sabine's formula: RT60 = 0.161 * V / A
        // Where V is volume and A is total absorption
        double totalAbsorption = totalSurface * avgAbsorption;
        double rt60 = totalAbsorption > 0 ? (0.161 * volume) / totalAbsorption : 0.0;

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
        boolean isIndoors = (double) outdoorLeak.get() / outdoorLeakDenom.get() < 0.05;

        return new EnhancedReverbData(rt60, earlyReflectionDelay, lateStrength,
                roomRadius, avgAbsorption, acousticProfile, isIndoors);
    }

    // I could use this later:tm: for better baselines for the dynamic reverb, but then it wouldn't be seemeless, so u get to stay in the codebase.
    // Unused tho
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

    private static void castRedRay(Level world, Player player, Vec3 currentPos, Queue<SoundData> sQ,  Queue<RedTickableInstance> tQ, Queue<RedPermeatedSoundInstance> pTQ,
                                   double currentDistance, Vec3 initialDirection, int bounces) {
        for (SoundData soundEntity : sQ) {
//            rayHitsByEntity.computeIfAbsent(soundEntity, k -> new CopyOnWriteArrayList<>()); // make sure all sounds are proc'd even if they aren't audible at first (makes discs work lmao)
            redRaysToTarget.computeIfAbsent(soundEntity, k -> new CopyOnWriteArrayList<>());
            //? if neoforge && =1.21.1 {
            Vec3 entityCenter = SableCompanion.INSTANCE.getContaining(world, soundEntity.position) != null
                    ? SableCompanion.INSTANCE.getContaining(world, soundEntity.position).logicalPose().transformPosition(soundEntity.position)
                    : soundEntity.position;
            //? } else {
//            Vec3 entityCenter = soundEntity.position;
            //? }
            double distanceToEntity = currentPos.distanceTo(entityCenter);

            if (distanceToEntity + currentDistance > Config.getInstance().maxRayLength * 16)
                continue;

            double blockCount = countBlocksBetween(world, currentPos, entityCenter, player);

            double weight = getWeight(currentDistance,blockCount,distanceToEntity);
            double permeationAbsorption = Math.pow(Config.getInstance().permeationAbsorption, blockCount);
            weight *= permeationAbsorption;

            RaycastResult rayResult = new RaycastResult(
                    distanceToEntity,
                    initialDirection,
                    soundEntity
            );
            Vec3 direction = entityCenter.subtract(currentPos);
            Vec3 normalVector = direction.normalize();
            RayHitData hitData = new RayHitData(rayResult, normalVector, weight, permeationAbsorption, bounces);

//            if (blockCount == 0) {
//                rayHitsByEntity.computeIfAbsent(soundEntity, k -> new CopyOnWriteArrayList<>()).add(hitData);
//            }

            redRaysToTarget.computeIfAbsent(soundEntity, k -> new CopyOnWriteArrayList<>()).add(hitData);
        }
        for (RedPermeatedSoundInstance soundEntity : pTQ) {
            SoundData data = new TickableSoundData(soundEntity, soundEntity.getOriginalPosition(), soundEntity.getSound().toString());
//            redRaysToTarget.computeIfAbsent(data, k -> new CopyOnWriteArrayList<>());

            Vec3 entityCenter = soundEntity.getOriginalPosition();
            double distanceToEntity = currentPos.distanceTo(entityCenter);

            if (distanceToEntity + currentDistance > Config.getInstance().maxRayLength * 16)
                continue;

            double blockCount = countBlocksBetween(world, currentPos, entityCenter, player);

            double weight = getWeight(currentDistance, blockCount, distanceToEntity);
            double permeationAbsorption = Math.pow(Config.getInstance().permeationAbsorption, blockCount);
            weight *= permeationAbsorption;

            RaycastResult rayResult = new RaycastResult(
                    distanceToEntity,
                    initialDirection,
                    data
            );
            Vec3 direction = entityCenter.subtract(currentPos);
            Vec3 normalVector = direction.normalize();
            RayHitData hitData = new RayHitData(rayResult, normalVector, weight, permeationAbsorption, bounces);
            redRaysToTarget.computeIfAbsent(data, k -> new CopyOnWriteArrayList<>()).add(hitData);
        }
    }

    private static double getWeight(double currentDistance, double blockCount, double distanceToEntity) {
        double permeationAbsorption = Math.pow(Config.getInstance().permeationAbsorption, blockCount); // 0.7 is how much it'll lower the gain by, keep in mind the muffle fx is still seperate
        double weight;
        if (Config.getInstance().attenuationType == RedsAttenuationType.INVERSE_SQUARE)
            weight = permeationAbsorption / (Math.max(distanceToEntity + currentDistance, 0.1) * Math.max(distanceToEntity + currentDistance, 0.1));
        else if (Config.getInstance().attenuationType == RedsAttenuationType.LINEAR)
            weight = permeationAbsorption / Math.max(distanceToEntity + currentDistance, 0.1);
        else if (Config.getInstance().attenuationType == RedsAttenuationType.VERCIDIUM_INVERSE_SQUARE)
            weight = permeationAbsorption / (Math.max(distanceToEntity + currentDistance, 0.1) * Math.max(distanceToEntity + currentDistance, 0.1));
        else if (Config.getInstance().attenuationType == RedsAttenuationType.VERCIDIUM_LINEAR)
            weight = permeationAbsorption / (Math.max(distanceToEntity + currentDistance, 0.1) * Math.max(distanceToEntity + currentDistance, 0.1));
        else
            weight = 0;
        return weight;
    }

    // Helper method to calculate weighted averages for a single entity
    private static AveragedSoundData calculateWeightedAverages(SoundData entity, List<RayHitData> rayHits) {
        double totalWeight = 0.0;
        double weightedDistanceSum = 0.0;
        double weightedMuffleSum = 0.0;
        Vec3 weightedDirectionSum = Vec3.ZERO;

        // Calculate weighted sums
        for (RayHitData rayHit : rayHits) {
            double weight = rayHit.weight;
            totalWeight += weight;

            // Weighted distance
            weightedDistanceSum += rayHit.rayResult.totalDistance * weight;
            weightedMuffleSum += rayHit.muffleFac * weight;

            // Weighted direction (using initial ray direction)
            Vec3 weightedDirection = rayHit.rayResult.initialDirection.scale(weight);
            weightedDirectionSum = weightedDirectionSum.add(weightedDirection);
        }

        if (totalWeight == 0.0)
            return new AveragedSoundData(entity, weightedDirectionSum, weightedDistanceSum,
                    totalWeight, rayHits.size(), rayHits);
        // Calculate averages
        double averageDistance = weightedDistanceSum / totalWeight;
        Vec3 averageDirection = weightedDirectionSum.scale(1.0 / totalWeight).normalize();

        return new AveragedSoundData(entity, averageDirection, averageDistance,
                totalWeight, rayHits.size(), rayHits, weightedMuffleSum / totalWeight);
    }

//? if neoforge && =1.21.1 {
public static double countBlocksBetween(Level world, Vec3 start, Vec3 end, Player player) {
    double totalDistanceInBlocks = 0;
    Vec3 currentStart = start;

    BlockPos endBlockPos = new BlockPos((int) Math.floor(end.x), (int) Math.floor(end.y), (int) Math.floor(end.z));

    while (totalDistanceInBlocks < Config.getInstance().maxBlocksPermeated) {
        ClipContext context = new ClipContext(
                currentStart, end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ) {
            @Override
            public VoxelShape getBlockShape(BlockState blockState, BlockGetter level, BlockPos pos) {
                if (blockState.getBlock() == Blocks.BARRIER && Config.getInstance().barrierAsAir)
                    return Shapes.empty();
                return super.getBlockShape(blockState, world, pos);
            }
        };

        BlockHitResult hit = world.clip(context);
        if (hit.getType() != HitResult.Type.BLOCK) break;

        BlockPos hitBlockPos = hit.getBlockPos();
        BlockState blockState = world.getBlockState(hitBlockPos);

        // Check if this hit is inside a sub-level
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(world, hit.getLocation());

        // Project hit location to global space
        Vec3 globalHitLoc = subLevel != null
                ? subLevel.logicalPose().transformPosition(hit.getLocation())
                : hit.getLocation();

        // End block check — compare in the same space
        if (subLevel == null && hitBlockPos.equals(endBlockPos)) break;
        // For sub-level hits, check global distance proximity to end instead
        if (subLevel != null && globalHitLoc.distanceToSqr(end) < 0.5) break;

        // Direction for stepping — transform into plot space if needed
        Vec3 plotDirection;
        if (subLevel != null) {
            // Transform direction into plot space for correct stepping
            Vec3 plotStart = subLevel.logicalPose().transformPositionInverse(currentStart);
            Vec3 plotEnd = subLevel.logicalPose().transformPositionInverse(end);
            plotDirection = plotEnd.subtract(plotStart).normalize();
        } else {
            plotDirection = end.subtract(currentStart).normalize();
        }

        VoxelShape blockShape = blockState.getShape(world, hitBlockPos);
        AABB blockBounds = blockShape.isEmpty()
                ? new AABB(hitBlockPos.getX(), hitBlockPos.getY(), hitBlockPos.getZ(),
                hitBlockPos.getX() + 1, hitBlockPos.getY() + 1, hitBlockPos.getZ() + 1)
                : blockShape.bounds().move(hitBlockPos);

        // Step in plot space
        Vec3 exitPoint = hit.getLocation();
        double step = Config.getInstance().permeationStepSize;
        while (blockBounds.contains(exitPoint)) {
            exitPoint = exitPoint.add(plotDirection.scale(step));
        }

        // Project exit point to global space
        Vec3 globalExitPoint = subLevel != null
                ? subLevel.logicalPose().transformPosition(exitPoint)
                : exitPoint;

        // Distance check in global space
        if (SableCompanion.INSTANCE.distanceSquaredWithSubLevels(world, currentStart, start)
                >= end.distanceToSqr(start)) break;

        double distanceInBlock = globalHitLoc.distanceTo(globalExitPoint);
        double absorptionIndex = getAbsorptionCoeficient(world, hit.getLocation());
        totalDistanceInBlocks += distanceInBlock * absorptionIndex;

        // Next iteration starts in global space
        Vec3 globalDirection = end.subtract(currentStart).normalize();
        currentStart = globalExitPoint.add(globalDirection.scale(0.01));
    }
    return totalDistanceInBlocks;
}
//?} else {
    /*public static double countBlocksBetween(Level world, Vec3 start, Vec3 end, Player player) {
        double totalDistanceInBlocks = 0;
        Vec3 currentStart = start;

        // Calculate the block position that contains the end point
        BlockPos endBlockPos = new BlockPos((int) Math.floor(end.x), (int) Math.floor(end.y), (int) Math.floor(end.z));

        while (totalDistanceInBlocks < Config.getInstance().maxBlocksPermeated) {
            // Cast a ray from current position to the end point
            ClipContext context = new ClipContext(
                    currentStart,
                    end,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    player
            ) {
                @Override
                public VoxelShape getBlockShape(BlockState blockState, BlockGetter level, BlockPos pos) {
                    if (blockState.getBlock() == Blocks.BARRIER && Config.getInstance().barrierAsAir) {
                        return Shapes.empty();
                    }
                    return super.getBlockShape(blockState, world, pos);
                }
            };

            BlockHitResult hit = world.clip(context);

            // If we didn't hit anything or reached the end, we're done
            if (hit.getType() != HitResult.Type.BLOCK) {
                break;
            }

            BlockPos hitBlockPos = hit.getBlockPos();
            BlockState blockState = world.getBlockState(hitBlockPos);

            // If we hit the block that contains the end position, break
            if (hitBlockPos.equals(endBlockPos)) {
                break;
            }

            // Calculate the distance traveled through this specific block
            Vec3 direction = end.subtract(currentStart).normalize();

            // Get the block's bounding box
            VoxelShape blockShape = blockState.getShape(world, hitBlockPos);
            AABB blockBounds;
            if (!blockShape.isEmpty()) {
                blockBounds = blockShape.bounds().move(hitBlockPos);
            } else {
                blockBounds = new AABB(
                        hitBlockPos.getX(), hitBlockPos.getY(), hitBlockPos.getZ(),
                        hitBlockPos.getX() + 1, hitBlockPos.getY() + 1, hitBlockPos.getZ() + 1
                );
            }

            if(blockBounds.contains(end)) { // shouldn't but im kinda just hoping something works atp
                break;
            }

            // Find the exit point by moving along the ray direction until we're outside the block
            Vec3 exitPoint = hit.getLocation();
            double step = Config.getInstance().permeationStepSize; // Small step size for precision

            while (blockBounds.contains(exitPoint)) {
                exitPoint = exitPoint.add(direction.scale(step));
            }

            // Check if we've passed the end point
            if (currentStart.distanceTo(start) >= end.distanceTo(start)) {
                break;
            }

            // Calculate distance traveled within this block
            double distanceInBlock = hit.getLocation().distanceTo(exitPoint);
            double absorptionIndex = getAbsorptionCoeficient(world,hit.getLocation());
            totalDistanceInBlocks += distanceInBlock * absorptionIndex;


            // Add a small buffer to ensure we're clearly outside
            currentStart = exitPoint.add(direction.scale(0.01));
        }
        return totalDistanceInBlocks;
    }
    *///?}

    public static boolean hasLineOfSight(Level world, Player player, SoundInstance sound) {
        if (sound instanceof RedTickableInstance)
            return countBlocksBetween(world,player.getEyePosition(), ((RedTickableInstance) sound).getOriginalPosition(), player) == 0;
        return countBlocksBetween(world,player.getEyePosition(),new Vec3(sound.getX(),sound.getY(),sound.getZ()),player) == 0;
    }

    public static Vec3 calculateReflection(Vec3 incident, Direction hitSide) {
        //? if >=1.21.2
        /*Vec3 normal = Vec3.atLowerCornerOf(hitSide.getUnitVec3i());*/
        //? if <1.21.2
        Vec3 normal = Vec3.atLowerCornerOf(hitSide.getNormal());

        // Reflection formula: R = I - 2(I·N)N
        // Where I is incident vector, N is normal, R is reflected vector
        double dotProduct = incident.dot(normal);
        return incident.subtract(normal.scale(2 * dotProduct));
    }

    public static Vec3 calculateReflection(Vec3 incident, Vec3 normal) {
        // Reflection formula: R = I - 2(I·N)N
        // Where I is incident vector, N is normal, R is reflected vector
        double dotProduct = incident.dot(normal);
        return incident.subtract(normal.scale(2 * dotProduct));
    }

    public static Vec3[] generateRayDirections() {
        // Generate directions in a roughly spherical pattern
        // Using fibonacci sphere for even distribution
        int numRays = Config.getInstance().raysCast;
        Vec3[] directions = new Vec3[numRays];

        double goldenRatio = (1 + Math.sqrt(5)) / 2;

        for (int i = 0; i < numRays; i++) {
            double theta = 2 * Math.PI * i / goldenRatio;
            double phi = Math.acos(1 - 2.0 * (i + 0.5) / numRays);

            double x = Math.sin(phi) * Math.cos(theta);
            double y = Math.cos(phi);
            double z = Math.sin(phi) * Math.sin(theta);

            directions[i] = new Vec3(x, y, z);
        }

        return directions;
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

    // I was told that cleanup is neccessary when using threads, but idk where to put this lmao]
    // this is prolly not a good thing... #burnthevibecoder
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
