package com.redsmods.sound_physics_perfected.acoustics;

import java.util.HashMap;
import java.util.Map;

/** A sparse voxel world for tests: full blocks, or single-box partial blocks, each with a material. */
final class GridWorld implements VoxelWorld {
    private final Map<Long, AcousticMaterial> cells = new HashMap<>();
    private final Map<Long, double[]> boxes = new HashMap<>();
    private final java.util.Set<Long> waterlogged = new java.util.HashSet<>();
    private boolean loadedWaterlogged;
    /** Number of occupied cells per 16^3 section, for {@link #sectionEmpty}. */
    private final Map<Long, Integer> sectionCounts = new HashMap<>();
    boolean reportSections = true;
    private AcousticMaterial loaded;
    private double[] loadedBox;

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }

    GridWorld set(int x, int y, int z, AcousticMaterial material) {
        if (cells.put(key(x, y, z), material) == null) sectionCounts.merge(key(x >> 4, y >> 4, z >> 4), 1, Integer::sum);
        boxes.remove(key(x, y, z));
        return this;
    }

    GridWorld setPartial(int x, int y, int z, AcousticMaterial material, double... box) {
        if (cells.put(key(x, y, z), material) == null) sectionCounts.merge(key(x >> 4, y >> 4, z >> 4), 1, Integer::sum);
        boxes.put(key(x, y, z), box);
        return this;
    }

    /** A partial block with water around its box. */
    GridWorld setWaterlogged(int x, int y, int z, AcousticMaterial material, double... box) {
        setPartial(x, y, z, material, box);
        waterlogged.add(key(x, y, z));
        return this;
    }

    GridWorld clear(int x, int y, int z) {
        if (cells.remove(key(x, y, z)) != null) sectionCounts.merge(key(x >> 4, y >> 4, z >> 4), -1, Integer::sum);
        boxes.remove(key(x, y, z));
        return this;
    }

    GridWorld fill(int x0, int y0, int z0, int x1, int y1, int z1, AcousticMaterial material) {
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++)
                    set(x, y, z, material);
        return this;
    }

    /** A room whose air interior is [x0..x1]x[y0..y1]x[z0..z1], enclosed by one-block walls. */
    GridWorld room(int x0, int y0, int z0, int x1, int y1, int z1, AcousticMaterial material) {
        fill(x0 - 1, y0 - 1, z0 - 1, x1 + 1, y1 + 1, z1 + 1, material);
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++)
                    clear(x, y, z);
        return this;
    }

    /** Flat ground at y = -1 around the origin. */
    GridWorld ground(AcousticMaterial material, int radius) {
        return fill(-radius, -1, -radius, radius, -1, radius, material);
    }

    @Override
    public int load(int x, int y, int z) {
        long k = key(x, y, z);
        loaded = cells.get(k);
        loadedBox = loaded == null ? null : boxes.get(k);
        loadedWaterlogged = waterlogged.contains(k);
        if (loaded == null) return EMPTY;
        return loaded == AcousticMaterial.WATER ? WATER : SOLID;
    }

    @Override
    public boolean sectionEmpty(int x, int y, int z) {
        return reportSections && sectionCounts.getOrDefault(key(x >> 4, y >> 4, z >> 4), 0) <= 0;
    }

    @Override
    public boolean loadedWaterlogged() {
        return loadedWaterlogged;
    }

    @Override
    public boolean loadedIsFullCube() {
        return loadedBox == null;
    }

    @Override
    public void forEachLoadedBox(BoxConsumer consumer) {
        consumer.accept(loadedBox[0], loadedBox[1], loadedBox[2], loadedBox[3], loadedBox[4], loadedBox[5]);
    }

    @Override
    public AcousticMaterial loadedMaterial() {
        return loaded;
    }

    @Override
    public double loadedTransmissionWeight() {
        return 1.0;
    }
}
