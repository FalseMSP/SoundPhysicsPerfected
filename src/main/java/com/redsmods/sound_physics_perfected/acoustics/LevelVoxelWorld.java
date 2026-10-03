package com.redsmods.sound_physics_perfected.acoustics;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * {@link VoxelWorld} over a Minecraft level. Reads block states straight from chunk sections (caching the
 * current chunk), and matches the old Level#clip based tracing: collision shapes, fluids ignored, optional
 * barriers-as-air. One instance per worker thread.
 */
public final class LevelVoxelWorld implements VoxelWorld, Shapes.DoubleLineConsumer {
    private final Level level;
    private final ChunkSource chunks;
    private final boolean barrierAsAir;
    private final boolean useExplosionResistance;
    private final double maxBlocksPermeated;
    private final boolean water;
    private final int minSection;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    private int cachedChunkX = Integer.MIN_VALUE, cachedChunkZ = Integer.MIN_VALUE;
    private LevelChunkSection[] cachedSections;
    /** The section the last lookup was in (rays stay in one for ~16 steps), or null when it holds only air. */
    private int sectionX = Integer.MIN_VALUE, sectionY = Integer.MIN_VALUE, sectionZ = Integer.MIN_VALUE;
    private LevelChunkSection section;
    /** The current section's known-empty cells (see {@link CellMemo}), or null. */
    private long[] sectionMemo;
    private final CellMemo memo;

    private BlockState loadedState;
    private VoxelShape loadedShape;
    private boolean loadedFull;
    private boolean loadedWater;
    private BlockState entryState;
    private AcousticMaterials.Entry entry;
    private BoxConsumer boxTarget;

    /**
     * The last few block states' classification. Rays cross the same few states again and again (stone, dirt...),
     * and looking a shape up through the block is the single biggest cost of tracing.
     */
    private static final int KNOWN = 8;
    private final BlockState[] knownState = new BlockState[KNOWN];
    private final VoxelShape[] knownShape = new VoxelShape[KNOWN];
    private final int[] knownKind = new int[KNOWN];
    private final boolean[] knownFull = new boolean[KNOWN];
    private final boolean[] knownWaterlogged = new boolean[KNOWN];
    private final float[][] knownPass = new float[KNOWN][];
    private boolean loadedWaterlogged;
    /** The loaded cell's gaps (bass, mid, treble), or null when it is solid. */
    private float[] loadedPass;
    private int knownNext;

    /**
     * Which cells of the sections a trace passes through are known to be empty, filled in as the trace's workers first
     * look at them and shared between them: rays cross the same few thousand cells millions of times, and a known
     * empty cell then costs one bit test instead of a palette lookup. One bit per cell (512 bytes per section); only
     * emptiness is kept, since anything else needs its block state anyway. Lives for one trace (or one recorded
     * trail), since the world can change in between.
     * <p>
     * Workers set bits without locking. A lost update only forgets that a cell is empty (it is looked up again), and
     * bits are only ever set for empty cells, so no worker can see a cell as empty that is not.
     */
    public static final class CellMemo {
        private final java.util.concurrent.ConcurrentHashMap<Long, long[]> sections =
                new java.util.concurrent.ConcurrentHashMap<>();

        private static long key(int sx, int sy, int sz) {
            return ((long) sx & 0x3FFFFF) << 42 | ((long) sy & 0xFFFFF) << 22 | ((long) sz & 0x3FFFFF);
        }

        /** A block in this section changed between empty and not: forget what was known about the section. */
        public void dropSection(int sx, int sy, int sz) {
            sections.remove(key(sx, sy, sz));
        }

        /** A chunk was (re)loaded: its data may all be new. */
        public void dropChunk(int cx, int cz) {
            long x = (long) cx & 0x3FFFFF, z = (long) cz & 0x3FFFFF;
            sections.keySet().removeIf(k -> (k >>> 42) == x && (k & 0x3FFFFF) == z);
        }

        public int size() {
            return sections.size();
        }

        long[] section(int sx, int sy, int sz) {
            long key = key(sx, sy, sz);
            long[] cells = sections.get(key);
            if (cells == null) {
                long[] fresh = new long[64];
                cells = sections.putIfAbsent(key, fresh);
                if (cells == null) cells = fresh;
            }
            return cells;
        }
    }

    public LevelVoxelWorld(Level level, boolean barrierAsAir, boolean useExplosionResistance, double maxBlocksPermeated) {
        this(level, barrierAsAir, useExplosionResistance, maxBlocksPermeated, true);
    }

    /** @param water whether water is its own medium (reflective surface, costly to cross) rather than air */
    public LevelVoxelWorld(Level level, boolean barrierAsAir, boolean useExplosionResistance, double maxBlocksPermeated,
                           boolean water) {
        this(level, barrierAsAir, useExplosionResistance, maxBlocksPermeated, water, null);
    }

    /** @param memo cells already classified by this trace's other workers (null: none) */
    public LevelVoxelWorld(Level level, boolean barrierAsAir, boolean useExplosionResistance, double maxBlocksPermeated,
                           boolean water, CellMemo memo) {
        this.memo = memo;
        this.level = level;
        this.water = water;
        this.chunks = level.getChunkSource();
        this.barrierAsAir = barrierAsAir;
        this.useExplosionResistance = useExplosionResistance;
        this.maxBlocksPermeated = maxBlocksPermeated;
        // getSectionIndex(y) = (y >> 4) - minSection on every supported version (getMinSection was renamed).
        this.minSection = -level.getSectionIndex(0);
    }

    private LevelChunkSection[] sectionsOf(int x, int z) {
        int cx = x >> 4, cz = z >> 4;
        if (cx != cachedChunkX || cz != cachedChunkZ) {
            LevelChunk chunk = chunks.getChunk(cx, cz, false);
            cachedSections = chunk == null ? null : chunk.getSections();
            cachedChunkX = cx;
            cachedChunkZ = cz;
        }
        return cachedSections;
    }

    /** Selects the section holding (x, y, z); null when it is empty, unloaded or out of the world. */
    private LevelChunkSection sectionAt(int x, int y, int z) {
        int sx = x >> 4, sy = y >> 4, sz = z >> 4;
        if (sx != sectionX || sy != sectionY || sz != sectionZ) {
            sectionX = sx;
            sectionY = sy;
            sectionZ = sz;
            section = null;
            sectionMemo = null;
            LevelChunkSection[] sections = sectionsOf(x, z); // unloaded: behaves like air, as Level#clip does
            int index = sy - minSection;
            if (sections != null && index >= 0 && index < sections.length) {
                LevelChunkSection candidate = sections[index];
                if (candidate != null && !candidate.hasOnlyAir()) {
                    section = candidate;
                    if (memo != null) sectionMemo = memo.section(sx, sy, sz);
                }
            }
        }
        return section;
    }

    @Override
    public int load(int x, int y, int z) {
        LevelChunkSection section = sectionAt(x, y, z);
        if (section == null) return EMPTY;
        long[] empty = sectionMemo;
        if (empty == null) return classify(section, x, y, z);
        int i = (y & 15) << 8 | (z & 15) << 4 | (x & 15);
        if ((empty[i >>> 6] & (1L << i)) != 0) return EMPTY;
        int kind = classify(section, x, y, z);
        if (kind == EMPTY) empty[i >>> 6] |= 1L << i;
        return kind;
    }

    private int classify(LevelChunkSection section, int x, int y, int z) {
        BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
        if (state.isAir()) return EMPTY;
        for (int i = 0; i < KNOWN; i++) {
            if (knownState[i] == state) {
                return select(state, knownKind[i], knownShape[i], knownFull[i], knownWaterlogged[i], knownPass[i]);
            }
        }
        if (barrierAsAir && state.is(Blocks.BARRIER)) return remember(state, EMPTY, null, false, false, null);

        pos.set(x, y, z);
        // What sound meets: usually the collision shape, but some blocks are drawn differently from how they collide
        // (fences, walls, snow layers, powder snow, lava; see AcousticMaterials).
        AcousticMaterials.Entry acoustic = AcousticMaterials.of(state);
        VoxelShape shape = acoustic.shape != null ? acoustic.shape : state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            // Water (source, flowing, or filling a block without collision like seagrass) is its own medium.
            if (water && !state.getFluidState().isEmpty() && state.getFluidState().is(FluidTags.WATER)) {
                return remember(state, WATER, Shapes.block(), true, false, null);
            }
            return remember(state, EMPTY, null, false, false, null);
        }
        boolean waterlogged = water && !state.getFluidState().isEmpty() && state.getFluidState().is(FluidTags.WATER);
        boolean full = shape == Shapes.block() || (acoustic.shape == null && state.isCollisionShapeFullBlock(level, pos));
        return remember(state, SOLID, shape, full, waterlogged, acoustic.pass);
    }

    private int remember(BlockState state, int kind, VoxelShape shape, boolean full, boolean waterlogged,
                         float[] pass) {
        if (!state.getBlock().hasDynamicShape()) { // shapes that depend on the position can't be reused
            int i = knownNext;
            knownNext = (i + 1) % KNOWN;
            knownState[i] = state;
            knownKind[i] = kind;
            knownShape[i] = shape;
            knownFull[i] = full;
            knownWaterlogged[i] = waterlogged;
            knownPass[i] = pass;
        }
        return select(state, kind, shape, full, waterlogged, pass);
    }

    private int select(BlockState state, int kind, VoxelShape shape, boolean full, boolean waterlogged, float[] pass) {
        if (kind != EMPTY) {
            loadedState = state;
            loadedShape = shape;
            loadedFull = full;
            loadedWater = kind == WATER;
            loadedWaterlogged = waterlogged;
            loadedPass = pass;
        }
        return kind;
    }

    @Override
    public double loadedPassBass() {
        return loadedPass == null ? 0.0 : loadedPass[0];
    }

    @Override
    public double loadedPassMid() {
        return loadedPass == null ? 0.0 : loadedPass[1];
    }

    @Override
    public double loadedPassTreble() {
        return loadedPass == null ? 0.0 : loadedPass[2];
    }

    @Override
    public boolean loadedWaterlogged() {
        return loadedWaterlogged;
    }

    @Override
    public boolean loadedSectionEmpty() {
        return section == null; // the section of the last load(): empty, unloaded or outside the world
    }

    @Override
    public boolean sectionEmpty(int x, int y, int z) {
        return sectionAt(x, y, z) == null;
    }

    @Override
    public boolean loadedIsFullCube() {
        return loadedFull;
    }

    @Override
    public void forEachLoadedBox(BoxConsumer consumer) {
        boxTarget = consumer;
        loadedShape.forAllBoxes(this);
    }

    @Override
    public void consume(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        boxTarget.accept(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private AcousticMaterials.Entry entry() {
        if (entryState != loadedState) {
            entry = AcousticMaterials.of(loadedState);
            entryState = loadedState;
        }
        return entry;
    }

    @Override
    public AcousticMaterial loadedMaterial() {
        return loadedWater ? AcousticMaterial.WATER : entry().material;
    }

    @Override
    public double loadedTransmissionWeight() {
        return entry().transmissionWeight(useExplosionResistance, maxBlocksPermeated);
    }
}
