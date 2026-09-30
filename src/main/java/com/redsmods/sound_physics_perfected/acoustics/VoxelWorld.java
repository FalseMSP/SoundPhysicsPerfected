package com.redsmods.sound_physics_perfected.acoustics;

/**
 * Cell lookups for the tracer. Implementations are stateful and single threaded: {@link #load} selects a
 * cell and the other accessors describe that cell.
 */
public interface VoxelWorld {
    /** Sound passes freely (air, no collision, barrier as air...). */
    int EMPTY = 0;
    /** Something solid (full cube or a shape of boxes). */
    int SOLID = 1;
    /** Water: a different medium. Its surface reflects sound, and crossing it costs a lot of energy. */
    int WATER = 2;

    /** Selects a cell and returns its kind ({@link #EMPTY}, {@link #SOLID} or {@link #WATER}). */
    int load(int x, int y, int z);

    /**
     * Whether the whole 16x16x16 section containing this cell is empty (only air, or not loaded), so a ray can
     * skip it in one step. Implementations without sections just return false.
     */
    default boolean sectionEmpty(int x, int y, int z) {
        return false;
    }

    /** Whether the loaded cell's shape is the full unit cube (water cells count as full). */
    boolean loadedIsFullCube();

    /** Visits the loaded cell's boxes in cell-local coordinates (0..1). Only called for non-full cells. */
    void forEachLoadedBox(BoxConsumer consumer);

    /** Surface absorption of the loaded cell. */
    AcousticMaterial loadedMaterial();

    /** How much one block of the loaded cell counts towards permeation (1 = one "standard" block). */
    double loadedTransmissionWeight();

    @FunctionalInterface
    interface BoxConsumer {
        void accept(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);
    }
}
