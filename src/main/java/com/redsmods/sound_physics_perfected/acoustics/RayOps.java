package com.redsmods.sound_physics_perfected.acoustics;

/**
 * The three ray queries the acoustic simulation needs. There is a fast voxel implementation and a
 * vanilla {@code Level#clip} based one (kept for mods that add geometry outside the block grid).
 * Instances are single threaded; hit results are written to public fields to avoid allocation.
 */
public interface RayOps {
    /**
     * Finds the first surface along the ray. dir must be normalised.
     * On success fills {@link #hitT()}, the hit normal and {@link #hitMaterial()}.
     */
    boolean firstHit(double ox, double oy, double oz, double dx, double dy, double dz, double maxDistance);

    double hitT();

    int hitNormalX();

    int hitNormalY();

    int hitNormalZ();

    AcousticMaterial hitMaterial();

    /**
     * True when nothing blocks the segment a→b before (|b-a| - tolerance). A tolerance of 1 lets a ray
     * "see" a sound whose own block (note block, jukebox...) is in the way.
     */
    boolean clearPath(double ax, double ay, double az, double bx, double by, double bz, double tolerance);

    /**
     * Weighted length of solid material between a and b, ignoring the block that contains b. Stops
     * accumulating once the total reaches maxWeighted (the returned value may overshoot it by the last block).
     */
    double obstruction(double ax, double ay, double az, double bx, double by, double bz, double maxWeighted);
}
