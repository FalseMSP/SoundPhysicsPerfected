package com.redsmods.sound_physics_perfected.acoustics;

import net.minecraft.core.BlockPos;
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
    private final int minSection;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    private int cachedChunkX = Integer.MIN_VALUE, cachedChunkZ = Integer.MIN_VALUE;
    private LevelChunkSection[] cachedSections;

    private BlockState loadedState;
    private VoxelShape loadedShape;
    private boolean loadedFull;
    private BlockState entryState;
    private AcousticMaterials.Entry entry;
    private BoxConsumer boxTarget;

    public LevelVoxelWorld(Level level, boolean barrierAsAir, boolean useExplosionResistance, double maxBlocksPermeated) {
        this.level = level;
        this.chunks = level.getChunkSource();
        this.barrierAsAir = barrierAsAir;
        this.useExplosionResistance = useExplosionResistance;
        this.maxBlocksPermeated = maxBlocksPermeated;
        // getSectionIndex(y) = (y >> 4) - minSection on every supported version (getMinSection was renamed).
        this.minSection = -level.getSectionIndex(0);
    }

    @Override
    public boolean load(int x, int y, int z) {
        int cx = x >> 4, cz = z >> 4;
        if (cx != cachedChunkX || cz != cachedChunkZ) {
            LevelChunk chunk = chunks.getChunk(cx, cz, false);
            cachedSections = chunk == null ? null : chunk.getSections();
            cachedChunkX = cx;
            cachedChunkZ = cz;
        }
        LevelChunkSection[] sections = cachedSections;
        if (sections == null) return false; // unloaded: behaves like air, as Level#clip does
        int index = (y >> 4) - minSection;
        if (index < 0 || index >= sections.length) return false;
        LevelChunkSection section = sections[index];
        if (section == null || section.hasOnlyAir()) return false;

        BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
        if (state.isAir()) return false;
        if (barrierAsAir && state.is(Blocks.BARRIER)) return false;

        pos.set(x, y, z);
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) return false;
        loadedState = state;
        loadedShape = shape;
        loadedFull = shape == Shapes.block() || state.isCollisionShapeFullBlock(level, pos);
        return true;
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
        return entry().material;
    }

    @Override
    public double loadedTransmissionWeight() {
        return entry().transmissionWeight(useExplosionResistance, maxBlocksPermeated);
    }
}
