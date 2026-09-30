package com.redsmods.sound_physics_perfected;

import com.redsmods.sound_physics_perfected.ReverbHelpers.LegacyReverb;
import com.redsmods.sound_physics_perfected.acoustics.AcousticMaterials;
import com.redsmods.sound_physics_perfected.acoustics.ClipRayOps;
import com.redsmods.sound_physics_perfected.acoustics.LevelVoxelWorld;
import com.redsmods.sound_physics_perfected.acoustics.ListenerTrace;
import com.redsmods.sound_physics_perfected.acoustics.RayOps;
import com.redsmods.sound_physics_perfected.acoustics.ReverbParams;
import com.redsmods.sound_physics_perfected.acoustics.ReverbStats;
import com.redsmods.sound_physics_perfected.acoustics.ReverbZones;
import com.redsmods.sound_physics_perfected.acoustics.RoomAcousticsModel;
import com.redsmods.sound_physics_perfected.acoustics.TraceSettings;
import com.redsmods.sound_physics_perfected.acoustics.VoxelRayOps;
import com.redsmods.sound_physics_perfected.config.Config;
import com.redsmods.sound_physics_perfected.config.DebugType;
import com.redsmods.sound_physics_perfected.config.RedsAttenuationType;
import com.redsmods.sound_physics_perfected.storageclasses.SoundData;
import com.redsmods.sound_physics_perfected.wrappers.RedPermeatedSoundInstance;
import com.redsmods.sound_physics_perfected.wrappers.RedTickableInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.Vec3;
//? if neoforge && =1.21.1 {
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
//?}

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Ray traced audio, cast from the player (after Vercidium's "A first look at ray traced audio").
 *
 * <pre>
 * White: rays bouncing off surfaces from the player
 * Green: bounce point -> sound, line of sight only
 * Red:   bounce point -> sound, through blocks (permeation)
 * Blue:  bounce point -> player (reverb, and the shortest way back)
 * </pre>
 *
 * Threading: the public queues/maps are touched on the client thread only (the maps are concurrent so voice
 * chat can read them). Traces run on a small daemon pool against an immutable snapshot and are applied back
 * on the client thread. The published reverb parameters are immutable and volatile.
 */
public final class RaycastingHelper {
    // --- Fed by the sound engine hooks and by our own sound instances (client thread) ------------------------
    public static final Queue<RedTickableInstance> tickQueue = new ArrayDeque<>();
    public static final Queue<RedPermeatedSoundInstance> permeatedTickQueue = new ArrayDeque<>();
    public static final Queue<SoundData> soundQueue = new ArrayDeque<>();
    /** Intercepted sounds whose replacement has not started yet. They must still report as playing. */
    public static final Set<SoundInstance> notProcd = Collections.newSetFromMap(new IdentityHashMap<>());
    /** Sounds we give back to vanilla untouched (e.g. when a trace failed); the play hook lets these through. */
    public static final Set<SoundInstance> bypass = Collections.newSetFromMap(new IdentityHashMap<>());
    public static final Map<SoundInstance, RedTickableInstance> soundInstanceMap = new ConcurrentHashMap<>();
    public static final Map<SoundInstance, RedPermeatedSoundInstance> soundPermInstanceMap = new ConcurrentHashMap<>();
    public static final OpenALEffectsHandler fxHandler = new OpenALEffectsHandler();
    public static volatile Vec3 playerEyePos = Vec3.ZERO;

    // --- Trace pipeline ------------------------------------------------------------------------------------------
    private static final int THREADS = Math.max(2, Math.min(Runtime.getRuntime().availableProcessors() - 1, 8));
    private static final AtomicInteger THREAD_ID = new AtomicInteger();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(THREADS, runnable -> {
        Thread thread = new Thread(runnable, "SPP Ray Tracer #" + THREAD_ID.incrementAndGet());
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    private static CompletableFuture<Result> inFlight;
    /** Originals carried by the in-flight trace. */
    private static Set<SoundInstance> inFlightOriginals = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final List<SoundInstance> soundsToPlay = new ArrayList<>();
    private static final Set<SoundInstance> cancelled = Collections.newSetFromMap(new IdentityHashMap<>());
    private static int generation;
    private static double[] directions;
    private static int directionsFor = -1;
    private static WeakReference<Level> lastLevel = new WeakReference<>(null);
    private static Vec3 lastTraceEye = Vec3.ZERO;
    private static int ticksSinceTrace;
    /** Where / how long ago the room was last measured with the full reverb bounce count. */
    private static Vec3 lastFullReverbEye = Vec3.ZERO;
    private static int ticksSinceFullReverb = Integer.MAX_VALUE / 2;
    private static int tickCounter;
    private static long lastErrorLog;
    private static volatile boolean subLevelsNearby;
    private static double lastTraceMillis;
    private static int lastTraceSounds;

    // --- Published results ------------------------------------------------------------------------------------------
    private static volatile ReverbStats lastStats = new ReverbStats();
    private static volatile ReverbParams targetReverb;
    private static volatile ReverbParams currentReverb;
    /** Whether the listener's ears are under water (muffled hearing, underwater reverb). */
    private static volatile boolean underwater;
    /** EFX "underwater" preset: dark, dense and wet. */
    private static final ReverbParams UNDERWATER = new ReverbParams(0.3645f, 1.0f, 0.3162f, 0.01f, 1.0f, 1.49f, 0.10f, 1.0f,
            0.5963f, 0.007f, 7.0795f, 0.011f, 0.25f, 0.0f, 1.18f, 0.348f, 0.9943f, 5000f, 250f, 0f, 1.0f, 3.0f, 0.3f);
    /** Treble that reaches ears under water, relative to bass. */
    public static final float UNDERWATER_HF = 0.25f;
    /**
     * Treble emphasis of transmission at 100% muffling: a stone wall then stops ~20 dB more treble than bass,
     * glass stays fairly clear, wool is dull.
     */
    private static final double MUFFLE_AT_100_PERCENT = 2.5;

    /** Listener axes (right xyz, up xyz, forward xyz), for turning world-space reverb pans into listener space. */
    private static volatile float[] listenerBasis = {1, 0, 0, 0, 1, 0, 0, 0, -1};

    private RaycastingHelper() {
    }

    private enum Kind { ONE_SHOT, TICKABLE, PERMEATED }

    private record Target(Kind kind, SoundData data, RedTickableInstance tickable, Vec3 position) {
    }

    private record Job(Level level, Player player, Vec3 eye, TraceSettings settings, RoomAcousticsModel.Settings room,
                       double[] dirs, List<Target> targets, ListenerTrace.Targets arrays, boolean fast,
                       boolean barrierAsAir, boolean useExplosionResistance, double maxBlocksPermeated,
                       double stepSize, int raysCast, int raysBounced, int generation, boolean hasNewSounds,
                       boolean sourceReverb, double listenerEnclosure, boolean fullReverb, boolean water,
                       double energyFullShare, CachedTrail trail, TargetKey[] keys, double[][] cachedResults,
                       long worldVersion, long nanos, boolean cacheable) {
        RayOps newOps() {
            if (fast) {
                return new VoxelRayOps(new LevelVoxelWorld(level, barrierAsAir, useExplosionResistance, maxBlocksPermeated, water));
            }
            return new ClipRayOps(level, player, barrierAsAir, useExplosionResistance, maxBlocksPermeated, stepSize);
        }
    }

    private record Result(Job job, ListenerTrace.Accumulator paths, double[] obstruction, ReverbParams environment,
                          ReverbZones.SourceReverb[] sourceReverbs, long nanos, ListenerTrace.Trail[] trails) {
    }

    // ================================================================================================================
    // Trail caching: while the listener stands still and nothing around changes, the rays' paths are the same, so
    // new sounds only need connecting to them, and a sound heard from the same spot before needs nothing at all.
    // ================================================================================================================

    /** Bumped (client thread) whenever a block that matters to sound changes near the listener. */
    private static long worldVersion;
    /** Paths are re-traced at least this often anyway (chunks loading, anything not seen as a block change). */
    private static final long TRAIL_TTL_NANOS = 2_000_000_000L;
    private static final int TRAIL_MAX_RESULTS = 512;
    private static final int RESULT = ListenerTrace.STRIDE, RESULT_COUNT = RESULT, RESULT_OBSTRUCTION = RESULT + 1;

    private record TargetKey(long x, long y, long z, long range) {
    }

    /** A finished trace's ray paths and per-sound results. Client thread, except the paths (read-only). */
    private static final class CachedTrail {
        final Level level;
        final Vec3 eye;
        final TraceSettings settings;
        final long worldVersion;
        final long nanos;
        final ListenerTrace.Trail[] chunks;
        final ReverbStats stats;
        final Map<TargetKey, double[]> results = new HashMap<>();

        CachedTrail(Level level, Vec3 eye, TraceSettings settings, long worldVersion, long nanos,
                    ListenerTrace.Trail[] chunks, ReverbStats stats) {
            this.level = level;
            this.eye = eye;
            this.settings = settings;
            this.worldVersion = worldVersion;
            this.nanos = nanos;
            this.chunks = chunks;
            this.stats = stats;
        }
    }

    private static CachedTrail trailCache;

    /** A block changed on the client (see ClientLevelMixin). */
    public static void onBlockChanged(Level level, BlockPos pos, BlockState oldState, BlockState newState) {
        if (oldState == newState || trailCache == null) return;
        Vec3 eye = playerEyePos;
        double dx = pos.getX() + 0.5 - eye.x, dy = pos.getY() + 0.5 - eye.y, dz = pos.getZ() + 0.5 - eye.z;
        if (dx * dx + dy * dy + dz * dz > 256.0 * 256.0) return;
        if (acousticKind(level, pos, oldState) == acousticKind(level, pos, newState)) return; // crops, redstone...
        worldVersion++;
    }

    /** What the tracer sees in a block: nothing, water, or this block with this shape. */
    private static Object acousticKind(Level level, BlockPos pos, BlockState state) {
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (!shape.isEmpty()) return state.getBlock() == Blocks.BARRIER ? Blocks.BARRIER : java.util.List.of(state.getBlock(), shape);
        return state.getFluidState().is(FluidTags.WATER) ? Blocks.WATER : Blocks.AIR;
    }

    // --- Per-sound reverb (sounds in other spaces) -----------------------------------------------------------------
    private static final double[] SOURCE_DIRECTIONS = ListenerTrace.fibonacciSphere(32);
    private static final int SOURCE_BOUNCES = 5;
    private static final long SOURCE_CACHE_NANOS = 2_000_000_000L;
    private record CachedSourceReverb(ReverbZones.SourceReverb reverb, long nanos) {
    }
    private static final Map<Long, CachedSourceReverb> SOURCE_CACHE = new ConcurrentHashMap<>();
    private static final ReverbZones ZONES = new ReverbZones();
    private static final Map<SoundInstance, Integer> zoneOfSound = new IdentityHashMap<>();

    // ================================================================================================================
    // Per tick entry point (client thread, after vanilla ticked its sounds)
    // ================================================================================================================

    public static void onSoundEngineTick(Level level, Player player) {
        try {
            tickCounter++;
            ticksSinceFullReverb++;
            pump(level, player, false);
            updateReverbSmoothing();
        } catch (RuntimeException e) {
            logError("Error in sound ray tracing tick", e);
        }
    }

    /**
     * Every frame, right after the frame's packets were handled: deliver finished traces and start tracing
     * brand new sounds immediately, so they play about a frame after they were triggered instead of waiting
     * for the next 50 ms game tick.
     */
    public static void onFrame(Level level, Player player) {
        try {
            pump(level, player, true);
        } catch (RuntimeException e) {
            logError("Error in sound ray tracing frame update", e);
        }
    }

    private static void pump(Level level, Player player, boolean frame) {
        if (level != lastLevel.get()) {
            resetForLevel(level);
        }
        playerEyePos = player.getEyePosition();
        underwater = Config.getInstance().waterAcoustics && player.isEyeInFluid(FluidTags.WATER);

        if (inFlight != null && inFlight.isDone()) {
            collect(inFlight);
            inFlight = null;
        }
        if (inFlight == null && (!frame || !soundQueue.isEmpty())) {
            Job job = prepareJob(level, player);
            if (job != null) {
                inFlight = launch(job);
                int budget = Config.getInstance().lowLatencyBudgetMs;
                if (job.hasNewSounds() && budget > 0) {
                    // Let brand new sounds start this tick instead of the next one when the trace is quick.
                    try {
                        inFlight.get(budget, TimeUnit.MILLISECONDS);
                    } catch (TimeoutException | ExecutionException ignored) {
                        // collected next tick (or reported by collect())
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    if (inFlight.isDone()) {
                        collect(inFlight);
                        inFlight = null;
                    }
                }
            }
        }
        playQueuedObjects();
    }

    private static void resetForLevel(Level level) {
        lastLevel = new WeakReference<>(level);
        inFlight = null; // results for the old level are dropped
        inFlightOriginals = Collections.newSetFromMap(new IdentityHashMap<>());
        onStopAll();
        bypass.clear();
        targetReverb = null;
        currentReverb = null;
        lastStats = new ReverbStats();
        AcousticMaterials.clearCache(); // block tags can differ per server
        SOURCE_CACHE.clear();
        trailCache = null;
        worldVersion++;
    }

    // ================================================================================================================
    // Launching a trace
    // ================================================================================================================

    private static Job prepareJob(Level level, Player player) {
        Config config = Config.getInstance();
        Vec3 eye = player.getEyePosition();
        ticksSinceTrace++;

        boolean hasNewSounds = !soundQueue.isEmpty();
        boolean hasTickables = config.permeation ? !permeatedTickQueue.isEmpty() : !tickQueue.isEmpty();
        boolean idle = !hasNewSounds && !hasTickables;
        long now = System.nanoTime();
        CachedTrail cached = trailCache;
        boolean unchanged = cached != null && cached.level == level && cached.eye.equals(eye)
                && cached.worldVersion == worldVersion && !subLevelsNearby;
        if (idle && (!config.reverb || (unchanged && now - cached.nanos < TRAIL_TTL_NANOS)
                || (eye.distanceToSqr(lastTraceEye) < 0.0625 && ticksSinceTrace < 10))) {
            return null; // nothing to place, and the room around a standing player has not changed
        }

        List<Target> targets = new ArrayList<>();
        while (!soundQueue.isEmpty()) {
            SoundData data = soundQueue.poll();
            SoundInstance original = ((RedSoundInstance) data.sound).getOriginal();
            if (cancelled.remove(original)) {
                notProcd.remove(original);
                continue;
            }
            targets.add(new Target(Kind.ONE_SHOT, data, null, subLevelToWorld(level, data.position)));
        }
        if (config.permeation) {
            for (RedPermeatedSoundInstance sound : permeatedTickQueue) {
                targets.add(new Target(Kind.PERMEATED, null, sound, sound.getOriginalPosition()));
            }
        } else {
            for (RedTickableInstance sound : tickQueue) {
                targets.add(new Target(Kind.TICKABLE, null, sound, sound.getOriginalPosition()));
            }
        }
        tickQueue.clear();
        permeatedTickQueue.clear();

        ListenerTrace.Targets arrays = new ListenerTrace.Targets(targets.size());
        for (int i = 0; i < targets.size(); i++) {
            Target t = targets.get(i);
            float volume = t.kind == Kind.ONE_SHOT ? t.data.sound.getVolume() : t.tickable.getOriginalVolume();
            arrays.set(i, t.position.x, t.position.y, t.position.z, 16.0 * volume);
        }

        if (config.raysCast != directionsFor || directions == null) {
            directions = ListenerTrace.fibonacciSphere(Math.max(config.raysCast, 0));
            directionsFor = config.raysCast;
        }

        // The room only needs re-measuring (with the long reverb bounce chains) when the listener moved or every
        // 10 ticks; traces in between, run for new sounds, keep the last estimate.
        boolean fullReverb = eye.distanceToSqr(lastFullReverbEye) > 0.25 || ticksSinceFullReverb >= 10;
        TraceSettings settings = new TraceSettings(config.raysBounced, fullReverb ? config.reverbBounces : config.raysBounced,
                16.0 * config.maxRayLength, config.permeation,
                config.reverb, config.permeationAbsorption, config.maxBlocksPermeated,
                Math.max(config.bounceAbsorptionMultiplier, 0.001), config.attenuationType,
                MUFFLE_AT_100_PERCENT * config.muffleStrength / 100.0);
        subLevelsNearby = subLevelsNear(level, eye, Math.max(settings.maxTotalDistance, settings.segmentLength));
        boolean fast = config.fastRaytracing && !subLevelsNearby;

        // Reuse the last trace's paths (and results) while nothing has moved: moving sub-levels rule that out.
        boolean cacheable = !subLevelsNearby;
        boolean reuse = unchanged && cacheable && cached.settings.connectsLike(settings)
                && now - cached.nanos < TRAIL_TTL_NANOS;
        if (reuse) {
            fullReverb = false; // the room has not changed either
        }
        TargetKey[] keys = new TargetKey[targets.size()];
        double[][] cachedResults = new double[targets.size()][];
        for (int i = 0; i < targets.size(); i++) {
            keys[i] = new TargetKey(Math.round(arrays.x[i] * 16.0), Math.round(arrays.y[i] * 16.0),
                    Math.round(arrays.z[i] * 16.0),
                    settings.permeation ? 0L : Double.doubleToLongBits(arrays.greenRange[i]));
            if (reuse) {
                cachedResults[i] = cached.results.get(keys[i]);
                arrays.skip[i] = cachedResults[i] != null;
            }
        }

        lastTraceEye = eye;
        ticksSinceTrace = 0;
        if (fullReverb) {
            lastFullReverbEye = eye;
            ticksSinceFullReverb = 0;
        }
        boolean sourceReverb = config.reverb && config.reverbZones && config.legacyReverb == LegacyReverb.MODERN
                && fxHandler.zonesAvailable();
        return new Job(level, player, eye, settings, roomSettings(config), directions, targets, arrays, fast,
                config.barrierAsAir, config.useExplosionResistance, config.maxBlocksPermeated,
                config.permeationStepSize, config.raysCast, config.raysBounced, generation, hasNewSounds, sourceReverb,
                currentReverb == null ? 0.0 : currentReverb.enclosure, fullReverb, config.waterAcoustics,
                config.energyFullShare / 100.0, reuse ? cached : null, keys, cachedResults, worldVersion, now,
                cacheable);
    }

    private static RoomAcousticsModel.Settings roomSettings(Config c) {
        return new RoomAcousticsModel.Settings(c.soundSpeed, c.globalReverbIntensity, c.maxOverallGain,
                c.decayTimeMultiplier, c.maxDecayTime, c.reflectionsMultiplier, c.lateReverbMultiplier,
                c.echoMultiplier, c.baseHfReference, c.baseLfReference, c.directionalReverb);
    }

    private static CompletableFuture<Result> launch(Job job) {
        inFlightOriginals = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Target target : job.targets) {
            if (target.kind == Kind.ONE_SHOT) {
                inFlightOriginals.add(((RedSoundInstance) target.data.sound).getOriginal());
            }
        }
        long start = System.nanoTime();
        int rays = job.dirs.length / 3;
        int targetCount = job.targets.size();
        double[] obstruction = new double[targetCount];
        List<CompletableFuture<ListenerTrace.Accumulator>> parts = new ArrayList<>();

        boolean anyToTrace = false;
        for (int j = 0; j < targetCount; j++) {
            if (job.arrays.skip[j]) {
                obstruction[j] = job.cachedResults[j][RESULT_OBSTRUCTION];
            } else {
                anyToTrace = true;
            }
        }
        // Direct paths, a few sounds per task so a burst of new sounds does not wait on one thread.
        int directTasks = anyToTrace ? Math.min(THREADS, (targetCount + 3) / 4) : 0;
        for (int t = 0; t < directTasks; t++) {
            int first = (int) ((long) targetCount * t / directTasks);
            int last = (int) ((long) targetCount * (t + 1) / directTasks);
            parts.add(CompletableFuture.supplyAsync(() -> {
                ListenerTrace.Accumulator acc = new ListenerTrace.Accumulator(targetCount);
                RayOps ops = job.newOps();
                for (int j = first; j < last; j++) {
                    if (job.arrays.skip[j]) continue;
                    obstruction[j] = ListenerTrace.traceDirect(ops, job.settings, rays, job.eye.x, job.eye.y,
                            job.eye.z, job.arrays, j, acc);
                }
                return acc;
            }, POOL));
        }

        ListenerTrace.Trail[] trails;
        if (job.trail != null) {
            // Same place, same world: connect the new sounds to the recorded paths.
            trails = null;
            for (ListenerTrace.Trail trail : anyToTrace ? job.trail.chunks : new ListenerTrace.Trail[0]) {
                parts.add(CompletableFuture.supplyAsync(() -> {
                    ListenerTrace.Accumulator acc = new ListenerTrace.Accumulator(targetCount);
                    ListenerTrace.connectTrail(job.newOps(), job.settings, trail, job.arrays, acc);
                    return acc;
                }, POOL));
            }
        } else {
            int chunks = Math.max(1, Math.min(rays, THREADS * 3));
            trails = rays > 0 && job.cacheable ? new ListenerTrace.Trail[chunks] : null;
            for (int c = 0; c < chunks && rays > 0; c++) {
                int from = (int) ((long) rays * c / chunks);
                int to = (int) ((long) rays * (c + 1) / chunks);
                int chunk = c;
                parts.add(CompletableFuture.supplyAsync(() -> {
                    ListenerTrace.Accumulator acc = new ListenerTrace.Accumulator(targetCount);
                    ListenerTrace.Trail trail = trails == null ? null : new ListenerTrace.Trail();
                    ListenerTrace.traceRays(job.newOps(), job.settings, job.eye.x, job.eye.y, job.eye.z, job.dirs,
                            from, to, job.arrays, acc, trail);
                    if (trails != null) trails[chunk] = trail;
                    return acc;
                }, POOL));
            }
        }

        ReverbZones.SourceReverb[] sourceReverbs = new ReverbZones.SourceReverb[targetCount];
        if (job.sourceReverb) {
            for (int j = 0; j < targetCount; j++) {
                int target = j;
                parts.add(CompletableFuture.supplyAsync(() -> {
                    sourceReverbs[target] = sourceReverb(job, target);
                    return new ListenerTrace.Accumulator(targetCount);
                }, POOL));
            }
        }

        return CompletableFuture.allOf(parts.toArray(new CompletableFuture[0])).thenApply(ignored -> {
            ListenerTrace.Accumulator total = new ListenerTrace.Accumulator(targetCount);
            for (CompletableFuture<ListenerTrace.Accumulator> part : parts) {
                total.merge(part.join()); // merged in a fixed order, so results are deterministic
            }
            if (job.trail != null) {
                total.stats.merge(job.trail.stats); // the room is the one the paths were traced in
            }
            for (int j = 0; j < targetCount; j++) {
                double[] cachedResult = job.cachedResults[j];
                if (cachedResult == null) continue;
                System.arraycopy(cachedResult, 0, total.sums, j * ListenerTrace.STRIDE, ListenerTrace.STRIDE);
                total.counts[j] = (int) cachedResult[RESULT_COUNT];
            }
            return new Result(job, total, obstruction, RoomAcousticsModel.estimate(total.stats, job.room),
                    sourceReverbs, System.nanoTime() - start, trails);
        });
    }

    /**
     * The reverb of the space a sound is in, and how much of it reaches the listener. Skipped for nearby sounds in
     * plain view (they share the listener's space); cached per 2-block cell of sound and listener.
     */
    private static ReverbZones.SourceReverb sourceReverb(Job job, int j) {
        double sx = job.arrays.x[j], sy = job.arrays.y[j], sz = job.arrays.z[j];
        Vec3 eye = job.eye;
        double distance = Math.sqrt(eye.distanceToSqr(sx, sy, sz));
        RayOps ops = job.newOps();
        // A sound in plain view shares the listener's space when it is close, or when the listener is enclosed
        // (in the open, a visible sound can still be inside a cave or building).
        if ((distance < 6.0 || job.listenerEnclosure > 0.9) && ops.clearPath(eye.x, eye.y, eye.z, sx, sy, sz, 1.0)) {
            return null;
        }
        long key = cellKey(sx, sy, sz) * 31L + cellKey(eye.x, eye.y, eye.z);
        long now = System.nanoTime();
        CachedSourceReverb cached = SOURCE_CACHE.get(key);
        if (cached != null && now - cached.nanos < SOURCE_CACHE_NANOS) {
            return cached.reverb;
        }
        ListenerTrace.SourceEnvironment env = new ListenerTrace.SourceEnvironment();
        ListenerTrace.traceSource(ops, job.settings, sx, sy, sz, eye.x, eye.y, eye.z, SOURCE_DIRECTIONS, SOURCE_BOUNCES, env);
        ReverbParams params = RoomAcousticsModel.estimate(env.stats, job.room);
        float[] pan = job.room.directional ? RoomAcousticsModel.focus(env.dirX, 0.0, env.dirZ, env.visibleEnergy) : new float[3];
        // Standing where half of a space's reflections are in view already means hearing its full reverb.
        float gain = (float) Math.min(1.0, env.visibleFraction() / 0.5);
        ReverbZones.SourceReverb reverb = new ReverbZones.SourceReverb(params, gain, pan[0], pan[2]);
        if (SOURCE_CACHE.size() > 1024) SOURCE_CACHE.clear();
        SOURCE_CACHE.put(key, new CachedSourceReverb(reverb, now));
        return reverb;
    }

    private static long cellKey(double x, double y, double z) {
        long cx = (long) Math.floor(x) >> 1, cy = (long) Math.floor(y) >> 1, cz = (long) Math.floor(z) >> 1;
        return (cx & 0x1FFFFF) << 42 | (cy & 0x1FFFFF) << 21 | (cz & 0x1FFFFF);
    }

    /**
     * Once per tick, before sends are attached: offer every playing sound that carries its own reverb to the
     * reverb zones (strongest first) and load the zones into their effect slots.
     */
    public static void updateZones(Iterable<SoundInstance> playing) {
        zoneOfSound.clear();
        ZONES.beginTick();
        Config config = Config.getInstance();
        boolean enabled = config.reverb && config.reverbZones && fxHandler.zonesAvailable()
                && config.legacyReverb == LegacyReverb.MODERN;
        if (enabled) {
            List<RedTickableInstance> candidates = new ArrayList<>();
            for (SoundInstance sound : playing) {
                if (sound instanceof RedTickableInstance red && red.getSourceReverb() != null && !red.isStopped()) {
                    candidates.add(red);
                }
            }
            candidates.sort((a, b) -> Float.compare(b.getOriginalVolume() * b.getSourceReverb().gain,
                    a.getOriginalVolume() * a.getSourceReverb().gain));
            ReverbParams listener = currentReverb;
            for (RedTickableInstance sound : candidates) {
                int zone = ZONES.offer(sound.getSourceReverb(), sound.getOriginalVolume(), listener);
                if (zone >= 0) zoneOfSound.put(sound, zone);
            }
        }
        ZONES.endTick(0.05, Math.max(config.reverbSmoothing, 0.0f));
        refreshZones();
    }

    /** Loads active zones into OpenAL (also called every frame, since their direction depends on the camera). */
    public static void refreshZones() {
        for (int i = 0; i < ReverbZones.MAX_ZONES; i++) {
            ReverbZones.Zone zone = ZONES.zone(i);
            if (zone.params() != null) {
                fxHandler.applyZone(i, zone.withPan(), zone.active() ? zone.gain() : 0.0f);
            }
        }
    }

    /** Reverb zone the given playing sound should also send to, or -1. */
    public static int zoneOf(SoundInstance sound) {
        Integer zone = zoneOfSound.get(sound);
        return zone == null ? -1 : zone;
    }

    // ================================================================================================================
    // Applying a finished trace (client thread)
    // ================================================================================================================

    private static void collect(CompletableFuture<Result> future) {
        Set<SoundInstance> originals = inFlightOriginals;
        inFlightOriginals = Collections.newSetFromMap(new IdentityHashMap<>());
        Result result;
        try {
            result = future.join();
        } catch (RuntimeException e) {
            logError("Sound ray trace failed; playing its sounds unprocessed", e);
            // Never lose a sound: hand the intercepted originals back to vanilla.
            for (SoundInstance original : originals) {
                boolean pending = notProcd.remove(original);
                boolean wasCancelled = cancelled.remove(original);
                if (pending && !wasCancelled) {
                    bypass.add(original);
                    soundsToPlay.add(original);
                }
            }
            return;
        }
        if (result.job.generation != generation) {
            return; // world changed or stopAll() since launch
        }
        apply(result);
    }

    private static void apply(Result result) {
        Job job = result.job;
        ListenerTrace.Accumulator paths = result.paths;
        lastStats = paths.stats;
        remember(result);
        if (job.fullReverb || targetReverb == null) {
            targetReverb = result.environment;
        }
        lastTraceMillis = result.nanos / 1.0e6;
        lastTraceSounds = job.targets.size();
        int totalHits = paths.stats.totalHits();

        for (int j = 0; j < job.targets.size(); j++) {
            Target target = job.targets.get(j);
            boolean energy = job.settings.attenuationType == RedsAttenuationType.ENERGY;
            if (target.kind == Kind.PERMEATED && paths.counts[j] == 0 && !energy) {
                continue; // nothing reached it this time: keep its previous state (as before)
            }
            if (target.kind == Kind.ONE_SHOT) {
                SoundInstance original = ((RedSoundInstance) target.data.sound).getOriginal();
                notProcd.remove(original);
                if (cancelled.remove(original)) continue;
            }
            try {
                Placement p = new Placement(job, target, paths, j, result.obstruction[j], totalHits);
                RedTickableInstance placed;
                if (job.settings.permeation) {
                    placed = placePermeated(p);
                } else {
                    placed = placeDirect(p);
                }
                if (placed != null && job.sourceReverb) {
                    placed.setSourceReverb(result.sourceReverbs[j]);
                }
            } catch (RuntimeException e) {
                logError("Error placing a traced sound", e);
            }
        }
    }

    /** Keeps a fresh trace's paths, and every traced sound's result, for reuse while nothing changes. */
    private static void remember(Result result) {
        Job job = result.job;
        CachedTrail trail = job.trail;
        if (trail == null) {
            if (result.trails == null) return;
            trail = new CachedTrail(job.level, job.eye, job.settings, job.worldVersion, job.nanos, result.trails,
                    result.paths.stats);
            trailCache = trail;
        } else if (trail != trailCache) {
            return;
        }
        if (trail.results.size() > TRAIL_MAX_RESULTS) trail.results.clear();
        ListenerTrace.Accumulator paths = result.paths;
        for (int j = 0; j < job.targets.size(); j++) {
            if (job.arrays.skip[j]) continue;
            double[] r = Arrays.copyOfRange(paths.sums, j * ListenerTrace.STRIDE, (j + 1) * ListenerTrace.STRIDE + 2);
            r[RESULT_COUNT] = paths.counts[j];
            r[RESULT_OBSTRUCTION] = result.obstruction[j];
            trail.results.put(job.keys[j], r);
        }
    }

    /** Weighted averages of all paths that reached one sound. */
    private static final class Placement {
        final Job job;
        final Target target;
        final SoundInstance sound;
        final double totalWeight;
        final int rayCount;
        final Vec3 direction;
        final double distance;
        final double muffle;
        final double muffleHf;
        final boolean lineOfSight;
        final float confidence;
        final float attenuation;
        /** Energy loudness per band (ENERGY mode only), and the straight-line distance it keeps. */
        final boolean energy;
        final float energyLf, energyHf;
        final double trueDistance;

        Placement(Job job, Target target, ListenerTrace.Accumulator paths, int j, double obstruction, int totalHits) {
            this.job = job;
            this.target = target;
            this.sound = target.kind == Kind.ONE_SHOT ? target.data.sound : target.tickable;
            int o = j * ListenerTrace.STRIDE;
            double w = paths.sums[o + ListenerTrace.WEIGHT];
            this.totalWeight = w;
            this.rayCount = paths.counts[j];
            this.lineOfSight = obstruction == 0.0;
            if (w > 0.0) {
                Vec3 mean = new Vec3(paths.sums[o + ListenerTrace.DIR_X] / w, paths.sums[o + ListenerTrace.DIR_Y] / w,
                        paths.sums[o + ListenerTrace.DIR_Z] / w);
                // Paths from opposite sides can cancel out; fall back to the true direction instead of a random one.
                Vec3 toSound = target.position.subtract(job.eye);
                this.direction = mean.lengthSqr() > 1.0E-8 ? mean.normalize() : toSound.normalize();
                this.distance = paths.sums[o + ListenerTrace.DISTANCE] / w;
                this.muffle = paths.sums[o + ListenerTrace.MUFFLE] / w;
                this.muffleHf = paths.sums[o + ListenerTrace.MUFFLE_HF] / w;
            } else {
                this.direction = Vec3.ZERO;
                this.distance = 0.0;
                this.muffle = 0.0;
                this.muffleHf = 0.0;
            }

            RedsAttenuationType type = job.settings.attenuationType;
            this.energy = type == RedsAttenuationType.ENERGY;
            this.trueDistance = target.position.distanceTo(job.eye);
            int rays = job.dirs.length / 3;
            this.energyLf = energy ? (float) ListenerTrace.energyGain(paths.sums, j, false, rays, job.energyFullShare) : 1.0f;
            this.energyHf = energy ? (float) ListenerTrace.energyGain(paths.sums, j, true, rays, job.energyFullShare) : 1.0f;
            if (energy) {
                confidence = 1.0f;
                attenuation = 1.0f;
            } else if (type == RedsAttenuationType.VERCIDIUM_INVERSE_SQUARE) {
                confidence = (float) rayCount / Math.max(totalHits, 1);
                attenuation = 1.0f / (float) Math.pow(Math.max(distance, 0.01), 2);
            } else if (type == RedsAttenuationType.VERCIDIUM_LINEAR) {
                confidence = (float) rayCount / Math.max(totalHits, 1);
                attenuation = 1.0f / (float) Math.max(distance, 0.01);
            } else {
                confidence = (float) totalWeight / job.raysCast * job.raysBounced;
                attenuation = 1.0f;
            }
        }

        /** Where the sound should appear: along the averaged arrival direction, or where it is if nothing reached it. */
        Vec3 apparentPosition() {
            if (totalWeight == 0.0) {
                return originalPosition();
            }
            return job.eye.add(direction.scale(distance));
        }

        Vec3 originalPosition() {
            return target.kind == Kind.ONE_SHOT ? target.data.position : target.tickable.getOriginalPosition();
        }

        /**
         * Energy mode: where the sound is when it is in plain view (or nothing reached it); otherwise at its true
         * distance along the direction its energy arrives from, so the vanilla distance falloff stays right.
         */
        Vec3 energyPosition() {
            if (totalWeight == 0.0 || (lineOfSight && Config.getInstance().shortcutDirectionality)) {
                return originalPosition();
            }
            return job.eye.add(direction.scale(trueDistance));
        }

        float baseVolume() {
            return target.kind == Kind.ONE_SHOT ? ((RedSoundInstance) sound).getOriginal().getVolume()
                    : target.tickable.getOriginalVolume();
        }
    }

    /** Line-of-sight (green ray) mode. */
    private static RedTickableInstance placeDirect(Placement p) {
        if (p.energy) {
            return placeEnergy(p, false);
        }
        if (p.totalWeight == 0.0 && p.target.tickable != null) {
            RedTickableInstance sound = p.target.tickable;
            sound.setTargetVolume(0);
            sound.setTargetPosition(sound.getOriginalPosition());
            sound.setAttenuationMultiplier(0);
            return sound;
        }
        Vec3 position = p.apparentPosition();
        float volume = p.baseVolume() * p.confidence * p.attenuation;
        if (p.target.tickable != null) {
            RedTickableInstance sound = p.target.tickable;
            sound.setTargetPosition(position);
            sound.setTargetVolume(Math.max(0.01f, Math.min(1.0f, volume)));
            sound.setAttenuationMultiplier(p.attenuation);
            return sound;
        }
        RedSoundInstance wrapper = (RedSoundInstance) p.sound;
        RedTickableInstance replacement = new RedTickableInstance(wrapper.getLocation(), wrapper.getSound(),
                wrapper.getSource(), position, Math.max(0.001f, Math.min(1.0f, volume)),
                Math.max(0.5f, Math.min(2.0f, wrapper.getPitch())), wrapper.getOriginal(), p.attenuation);
        soundInstanceMap.put(wrapper.getOriginal(), replacement);
        debugMessage(wrapper.getOriginal());
        soundsToPlay.add(replacement);
        return replacement;
    }

    /**
     * Energy loudness: the sound keeps its own volume (and so its vanilla range and falloff); what is in the way only
     * sets its filter gain, per band.
     */
    private static RedTickableInstance placeEnergy(Placement p, boolean permeated) {
        Vec3 position = p.energyPosition();
        if (p.target.tickable != null) {
            RedTickableInstance sound = p.target.tickable;
            if (permeated != (sound instanceof RedPermeatedSoundInstance)) {
                return null; // left over from before permeation was switched
            }
            sound.setTargetPosition(position);
            sound.setTargetVolume(sound.getOriginalVolume());
            sound.setAttenuationMultiplier(1.0f);
            if (sound instanceof RedPermeatedSoundInstance permeatedSound) {
                permeatedSound.setPermeationIndex(1.0f);
                permeatedSound.setPermeationIndexHf(1.0f);
            }
            sound.setEnergyGain(p.energyLf, p.energyHf, false);
            return sound;
        }
        RedSoundInstance wrapper = (RedSoundInstance) p.sound;
        float volume = Math.max(0.001f, p.baseVolume());
        RedTickableInstance replacement;
        if (permeated) {
            RedPermeatedSoundInstance sound = new RedPermeatedSoundInstance(wrapper.getLocation(), wrapper.getSound(),
                    wrapper.getSource(), position, volume, wrapper.getPitch(), wrapper.getOriginal(), 1.0f, 1.0f, 1.0f);
            soundPermInstanceMap.put(wrapper.getOriginal(), sound);
            replacement = sound;
        } else {
            replacement = new RedTickableInstance(wrapper.getLocation(), wrapper.getSound(), wrapper.getSource(),
                    position, volume, Math.max(0.5f, Math.min(2.0f, wrapper.getPitch())), wrapper.getOriginal(), 1.0f);
            soundInstanceMap.put(wrapper.getOriginal(), replacement);
        }
        replacement.setEnergyGain(p.energyLf, p.energyHf, true);
        debugMessage(wrapper.getOriginal(), String.format(" %s %.1f m: bass %.1f dB, treble %.1f dB",
                p.lineOfSight ? "in view" : "hidden", p.trueDistance, db(p.energyLf), db(p.energyHf)));
        soundsToPlay.add(replacement);
        return replacement;
    }

    /** Permeation (red ray) mode. */
    private static RedTickableInstance placePermeated(Placement p) {
        if (p.energy) {
            return placeEnergy(p, true);
        }
        Vec3 position = p.apparentPosition();
        float confidence = p.confidence;
        float permeationIndex = (float) p.muffle;
        float permeationIndexHf = (float) p.muffleHf;
        if (Config.getInstance().shortcutDirectionality && p.lineOfSight) {
            // With a clear line of sight the sound comes from where it is, at full confidence.
            position = p.target.tickable != null ? p.target.tickable.getOriginalPosition()
                    : new Vec3(p.sound.getX(), p.sound.getY(), p.sound.getZ());
            confidence = 1.0f;
        }
        float volume = p.baseVolume() * confidence * p.attenuation * permeationIndex;

        if (p.target.tickable instanceof RedPermeatedSoundInstance sound) {
            if (p.totalWeight == 0.0) {
                sound.setTargetVolume(0);
                sound.setTargetPosition(sound.getOriginalPosition());
                sound.setPermeationIndex(0);
                sound.setPermeationIndexHf(0);
                sound.setAttenuationMultiplier(0);
                return sound;
            }
            sound.setTargetPosition(position);
            sound.setTargetVolume(volume);
            sound.setPermeationIndex(permeationIndex);
            sound.setPermeationIndexHf(permeationIndexHf);
            sound.setAttenuationMultiplier(p.attenuation);
            return sound;
        }
        if (p.target.tickable != null) {
            return null; // a line-of-sight instance left over from before permeation was switched on
        }
        RedSoundInstance wrapper = (RedSoundInstance) p.sound;
        RedPermeatedSoundInstance replacement = new RedPermeatedSoundInstance(wrapper.getLocation(),
                wrapper.getSound(), wrapper.getSource(), position, Math.max(0.01f, volume), wrapper.getPitch(),
                wrapper.getOriginal(), permeationIndex, permeationIndexHf, p.attenuation);
        soundPermInstanceMap.put(wrapper.getOriginal(), replacement);
        debugMessage(wrapper.getOriginal());
        soundsToPlay.add(replacement);
        return replacement;
    }

    private static void debugMessage(SoundInstance original) {
        debugMessage(original, "");
    }

    private static void debugMessage(SoundInstance original, String detail) {
        DebugType debug = Config.getInstance().debug;
        Minecraft client = Minecraft.getInstance();
        if (debug == DebugType.OFF || client.player == null) return;
        client.player.displayClientMessage(Component.literal(original.getLocation() + detail), debug == DebugType.ACTION_BAR);
    }

    private static double db(float gain) {
        return 20.0 * Math.log10(Math.max(gain, 1.0E-5f));
    }

    private static void playQueuedObjects() {
        if (soundsToPlay.isEmpty()) return;
        Minecraft client = Minecraft.getInstance();
        List<SoundInstance> batch = new ArrayList<>(soundsToPlay);
        soundsToPlay.clear();
        for (SoundInstance sound : batch) {
            client.getSoundManager().play(sound);
        }
    }

    // ================================================================================================================
    // Sound lifecycle hooks
    // ================================================================================================================

    /** An original was stopped: drop it if it is still waiting for its trace. */
    public static void onOriginalStopped(SoundInstance original) {
        if (!notProcd.contains(original)) return;
        soundQueue.removeIf(data -> ((RedSoundInstance) data.sound).getOriginal() == original);
        if (inFlightOriginals.contains(original)) {
            cancelled.add(original); // its trace is already running; drop it when it lands
        }
        notProcd.remove(original);
    }

    public static void onStopAll() {
        generation++;
        soundQueue.clear();
        tickQueue.clear();
        permeatedTickQueue.clear();
        notProcd.clear();
        cancelled.clear();
        soundsToPlay.clear();
        soundInstanceMap.clear();
        soundPermInstanceMap.clear();
    }

    /** Forget replacements that have finished playing (the maps used to grow for the whole session). */
    public static void pruneFinished(Predicate<SoundInstance> isActive) {
        if (tickCounter % 20 != 0) return;
        soundInstanceMap.values().removeIf(replacement -> !isActive.test(replacement));
        soundPermInstanceMap.values().removeIf(replacement -> !isActive.test(replacement));
        bypass.removeIf(original -> !isActive.test(original));
    }

    // ================================================================================================================
    // Reverb environment
    // ================================================================================================================

    private static void updateReverbSmoothing() {
        ReverbParams target = targetReverb;
        if (target == null) return;
        if (underwater) {
            // Keep the room's size and direction, but mostly sound like being under water.
            target = target.approach(UNDERWATER.withGainOf(target), 0.8f);
        }
        ReverbParams current = currentReverb;
        float smoothing = Math.max(Config.getInstance().reverbSmoothing, 0.0f);
        if (current == null || smoothing == 0.0f) {
            currentReverb = target;
            return;
        }
        float alpha = (float) (1.0 - Math.exp(-0.05 / smoothing));
        currentReverb = current.approach(target, alpha);
    }

    /** Wall time of the last trace and how many sounds it placed, for the reverb debug line. */
    public static String lastTraceSummary() {
        return String.format(java.util.Locale.ROOT, "trace %.1f ms, %d sounds, %s", lastTraceMillis, lastTraceSounds,
                subLevelsNearby || !Config.getInstance().fastRaytracing ? "clip" : "voxel");
    }

    public static boolean isUnderwater() {
        return underwater;
    }

    /** Called every frame with the camera's axes (right = -left). */
    public static void setListenerBasis(float leftX, float leftY, float leftZ, float upX, float upY, float upZ,
                                        float lookX, float lookY, float lookZ) {
        listenerBasis = new float[]{-leftX, -leftY, -leftZ, upX, upY, upZ, lookX, lookY, lookZ};
    }

    /** Listener axes: right xyz, up xyz, forward xyz. Any thread. */
    public static float[] getListenerBasis() {
        return listenerBasis;
    }

    /** The smoothed reverb for the listener's surroundings, or null before the first trace. Any thread. */
    public static ReverbParams getCurrentReverb() {
        return currentReverb;
    }

    // Statistics for the legacy (1.0.0 / 1.4.0) reverb modes, with their original definitions.
    public static double getDistanceFromWallEcho() {
        return lastStats.legacyWallDistanceSum;
    }

    public static double getDistanceFromWallEchoDenom() {
        return lastStats.legacyWallDistanceCount;
    }

    public static int getReverbStrength() {
        return lastStats.legacyVisible;
    }

    public static int getReverbDenom() {
        return lastStats.legacyBlueRays;
    }

    public static int getOutdoorLeak() {
        return lastStats.legacyEscapes;
    }

    public static int getOutdoorLeakDenom() {
        return lastStats.legacyLeakDenominator;
    }

    // ================================================================================================================
    // Helpers
    // ================================================================================================================

    /** Weighted blocks between two points (0 = clear line of sight). Safe to call from any thread. */
    public static double countBlocksBetween(Level world, Vec3 start, Vec3 end, Player player) {
        Config config = Config.getInstance();
        RayOps ops = config.fastRaytracing && !subLevelsNearby
                ? new VoxelRayOps(new LevelVoxelWorld(world, config.barrierAsAir, config.useExplosionResistance, config.maxBlocksPermeated))
                : new ClipRayOps(world, player, config.barrierAsAir, config.useExplosionResistance, config.maxBlocksPermeated, config.permeationStepSize);
        return ops.obstruction(start.x, start.y, start.z, end.x, end.y, end.z, config.maxBlocksPermeated);
    }

    /** Sounds on a Sable sub-level are positioned in its plot; move them to where the sub-level actually is. */
    private static Vec3 subLevelToWorld(Level level, Vec3 position) {
        //? if neoforge && =1.21.1 {
        SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, position);
        if (subLevel != null) {
            return subLevel.logicalPose().transformPosition(position);
        }
        //?}
        return position;
    }

    /** The voxel tracer cannot see Sable sub-levels, so fall back to Level#clip while one is in range. */
    private static boolean subLevelsNear(Level level, Vec3 eye, double radius) {
        //? if neoforge && =1.21.1 {
        try {
            return SableCompanion.INSTANCE.getAllIntersecting(level, new BoundingBox3d(eye.x - radius, eye.y - radius,
                    eye.z - radius, eye.x + radius, eye.y + radius, eye.z + radius)).iterator().hasNext();
        } catch (RuntimeException e) {
            return true;
        }
        //?} else {
        /*return false;
        *///?}
    }

    private static void logError(String message, Throwable error) {
        long now = System.currentTimeMillis();
        if (now - lastErrorLog > 10_000) {
            lastErrorLog = now;
            SoundPhysicsPerfected.DEBUG_LOGGER.error(message, error);
        }
    }
}
