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

    /**
     * The high-frequency counterpart of the last {@link #obstruction} result: the same path, with each material's
     * {@link AcousticMaterial#transmissionHf} factor applied (walls stop treble far better than bass).
     */
    double lastObstructionHf();

    /**
     * Seeds the next {@link #firstHit}'s choices at blocks with gaps (leaves, grates, bars): which gaps this ray goes
     * through. Without a seed the choice comes from the ray's own geometry. Either way the same ray chooses the same.
     */
    default void nextRay(long seed) {
    }

    /**
     * How much of its bass and treble the last {@link #firstHit}'s ray keeps relative to its mid band from the gaps it
     * went through on the way (bass gets through small gaps better than mid, treble worse). 1 when it met none.
     * Kept per ray so each band's energy averages out right.
     */
    default double segmentBass() {
        return 1.0;
    }

    default double segmentTreble() {
        return 1.0;
    }

    /** The same for the reflection at the hit: a block with gaps reflects less bass (it lets it through). */
    default double hitBass() {
        return 1.0;
    }

    default double hitTreble() {
        return 1.0;
    }
}
