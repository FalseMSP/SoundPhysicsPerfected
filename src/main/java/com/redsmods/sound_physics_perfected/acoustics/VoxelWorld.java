package com.redsmods.sound_physics_perfected.acoustics;

/**
 * Cell lookups for the tracer. Implementations are stateful and single threaded: {@link #load} selects a
 * cell and the other accessors describe that cell.
 */
public interface VoxelWorld {
    /** Selects a cell. Returns false if sound passes through it freely (air, no collision, barrier as air...). */
    boolean load(int x, int y, int z);

    /** Whether the loaded cell's shape is the full unit cube. */
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
