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
    private int knownNext;

    public LevelVoxelWorld(Level level, boolean barrierAsAir, boolean useExplosionResistance, double maxBlocksPermeated) {
        this(level, barrierAsAir, useExplosionResistance, maxBlocksPermeated, true);
    }

    /** @param water whether water is its own medium (reflective surface, costly to cross) rather than air */
    public LevelVoxelWorld(Level level, boolean barrierAsAir, boolean useExplosionResistance, double maxBlocksPermeated,
                           boolean water) {
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

    @Override
    public int load(int x, int y, int z) {
        LevelChunkSection[] sections = sectionsOf(x, z);
        if (sections == null) return EMPTY; // unloaded: behaves like air, as Level#clip does
        int index = (y >> 4) - minSection;
        if (index < 0 || index >= sections.length) return EMPTY;
        LevelChunkSection section = sections[index];
        if (section == null || section.hasOnlyAir()) return EMPTY;

        BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
        if (state.isAir()) return EMPTY;
        for (int i = 0; i < KNOWN; i++) {
            if (knownState[i] == state) {
                return select(state, knownKind[i], knownShape[i], knownFull[i]);
            }
        }
        if (barrierAsAir && state.is(Blocks.BARRIER)) return remember(state, EMPTY, null, false);

        pos.set(x, y, z);
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            // Water (source, flowing, or filling a block without collision like seagrass) is its own medium.
            if (water && !state.getFluidState().isEmpty() && state.getFluidState().is(FluidTags.WATER)) {
                return remember(state, WATER, Shapes.block(), true);
            }
            return remember(state, EMPTY, null, false);
        }
        return remember(state, SOLID, shape, shape == Shapes.block() || state.isCollisionShapeFullBlock(level, pos));
    }

    private int remember(BlockState state, int kind, VoxelShape shape, boolean full) {
        if (!state.getBlock().hasDynamicShape()) { // shapes that depend on the position can't be reused
            int i = knownNext;
            knownNext = (i + 1) % KNOWN;
            knownState[i] = state;
            knownKind[i] = kind;
            knownShape[i] = shape;
            knownFull[i] = full;
        }
        return select(state, kind, shape, full);
    }

    private int select(BlockState state, int kind, VoxelShape shape, boolean full) {
        if (kind != EMPTY) {
            loadedState = state;
            loadedShape = shape;
            loadedFull = full;
            loadedWater = kind == WATER;
        }
        return kind;
    }

    @Override
    public boolean sectionEmpty(int x, int y, int z) {
        LevelChunkSection[] sections = sectionsOf(x, z);
        if (sections == null) return true;
        int index = (y >> 4) - minSection;
        if (index < 0 || index >= sections.length) return true;
        LevelChunkSection section = sections[index];
        return section == null || section.hasOnlyAir();
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
