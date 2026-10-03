package com.redsmods.sound_physics_perfected.acoustics;

/**
 * {@link RayOps} implemented with a direct voxel walk. Several times cheaper than Level#clip because it
 * reads block states straight from the cell source, allocates nothing per step, and only builds shape
 * geometry for blocks that are not full cubes.
 */
public final class VoxelRayOps implements RayOps, VoxelWorld.BoxConsumer {
    private static final double EPSILON = 1.0E-9;
    /**
     * Crossing the air/water surface, in "blocks of stone" of permeation: almost all sound energy is reflected
     * at the surface (the real loss is around 30 dB); treble loses more.
     */
    static final double WATER_SURFACE_WEIGHT = 2.5, WATER_SURFACE_HF = 1.5;
    /** Travelling through water itself barely attenuates. */
    static final double WATER_VOLUME_WEIGHT = 0.1;

    private final VoxelWorld world;
    private final VoxelTraversal dda = new VoxelTraversal();
    /** Energy one weighted block of permeation lets through (the Permeation Absorption setting). */
    private final double absorption;
    private long seed;
    private boolean seeded;
    private double segmentBass = 1.0, segmentTreble = 1.0, hitBass = 1.0, hitTreble = 1.0;

    private double hitT;
    private int hitNormalX, hitNormalY, hitNormalZ;
    private AcousticMaterial hitMaterial;
    private double obstructionHf;

    // Scratch state for the box visitor.
    private static final int MODE_FIRST_HIT = 0;
    private static final int MODE_LENGTH = 1;
    private int boxMode;
    private double rayOx, rayOy, rayOz, rayDx, rayDy, rayDz;
    private double cellX, cellY, cellZ;
    private double clipMin, clipMax;
    private double boxBestT;
    private int boxBestAxis;
    private double boxLength;

    public VoxelRayOps(VoxelWorld world) {
        this(world, 0.4);
    }

    /** @param permeationAbsorption energy one weighted block lets through (0..1) */
    public VoxelRayOps(VoxelWorld world, double permeationAbsorption) {
        this.world = world;
        this.absorption = Math.min(Math.max(permeationAbsorption, 1.0E-6), 0.999999);
    }

    @Override
    public void nextRay(long seed) {
        this.seed = seed;
        this.seeded = true;
    }

    @Override
    public double segmentBass() {
        return segmentBass;
    }

    @Override
    public double segmentTreble() {
        return segmentTreble;
    }

    @Override
    public double hitBass() {
        return hitBass;
    }

    @Override
    public double hitTreble() {
        return hitTreble;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** This walk's random number in [0, 1): from the seed when given, else from where the ray starts and points. */
    private double walkRandom(double ox, double oy, double oz, double dx, double dy, double dz) {
        long h;
        if (seeded) {
            h = seed;
            seeded = false;
        } else {
            h = VoxelTraversal.floor(ox * 8.0) * 0x9E3779B97F4A7C15L ^ VoxelTraversal.floor(oy * 8.0) * 0xC2B2AE3D27D4EB4FL
                    ^ VoxelTraversal.floor(oz * 8.0) * 0x165667B19E3779F9L ^ Math.round(dx * 64.0) * 0x27D4EB2F165667C5L
                    ^ Math.round(dy * 64.0) * 0x94D049BB133111EBL ^ Math.round(dz * 64.0);
        }
        return (mix(h ^ 0x632BE59BD9B4E019L) >>> 11) * 0x1.0p-53;
    }

    // The gaps the current walk went through: the share of rays still going (1 = no gaps met), and its random number.
    private double survive, walkU;

    /**
     * At a block with gaps the ray just met: true when it goes on through a gap, false when it hits the block. One
     * random number per walk decides every block on the way: the walk gets past a block while that number is below
     * the share of rays that get past every block so far, so each block is passed as often as its gaps say.
     */
    private boolean throughGap() {
        double mid = world.loadedPassMid();
        if (mid <= 0.0) return false;
        double bass = world.loadedPassBass(), treble = world.loadedPassTreble();
        if (walkU < survive * mid) {
            survive *= mid;
            segmentBass *= bass / mid;
            segmentTreble *= treble / mid;
            return true;
        }
        if (mid < 1.0) {
            hitBass = (1.0 - bass) / (1.0 - mid);
            hitTreble = (1.0 - treble) / (1.0 - mid);
        }
        return false;
    }

    /**
     * Weight of crossing a block with gaps, from the weight of crossing it solid: the gaps' share gets straight
     * through, the rest through the block itself.
     */
    private double gapWeight(double gaps, double solidWeight) {
        double t = gaps + (1.0 - gaps) * Math.pow(absorption, solidWeight);
        return Math.log(t) / Math.log(absorption);
    }

    // Section-emptiness cache for the current walk.

    /** Whether the cell just loaded lies in a completely empty section (the world knows it from that load). */
    private boolean inEmptySection() {
        return world.loadedSectionEmpty();
    }

    /** Jumps across the empty section holding the current cell; false once past the walk's end. */
    private boolean skipSection() {
        return dda.skipBox(dda.x & ~15, dda.y & ~15, dda.z & ~15, 16);
    }

    /** The medium (air or water) a ray starting at o in direction d travels through first. */
    private int originMedium(double ox, double oy, double oz, double dx, double dy, double dz) {
        int kind = world.load(VoxelTraversal.floor(ox + dx * 1.0E-4), VoxelTraversal.floor(oy + dy * 1.0E-4),
                VoxelTraversal.floor(oz + dz * 1.0E-4));
        return kind == VoxelWorld.WATER || (kind == VoxelWorld.SOLID && world.loadedWaterlogged())
                ? VoxelWorld.WATER : VoxelWorld.EMPTY;
    }

    @Override
    public boolean firstHit(double ox, double oy, double oz, double dx, double dy, double dz, double maxDistance) {
        walkU = walkRandom(ox, oy, oz, dx, dy, dz);
        survive = 1.0;
        segmentBass = segmentTreble = hitBass = hitTreble = 1.0;
        int medium = originMedium(ox, oy, oz, dx, dy, dz);
        dda.init(ox, oy, oz, dx, dy, dz, maxDistance);
        while (true) {
            int kind = world.load(dda.x, dda.y, dda.z);
            if (kind == VoxelWorld.EMPTY && medium == VoxelWorld.EMPTY) {
                // Nothing in this whole section: jump straight across it.
                if (inEmptySection()) {
                    if (!skipSection()) return false;
                    continue;
                }
            } else if (kind != VoxelWorld.SOLID) {
                // Air <-> water: the surface reflects like a very hard wall.
                if (kind != medium && dda.tExit() - dda.tEnter > EPSILON) {
                    if (dda.tEnter > maxDistance) return false;
                    hitT = dda.tEnter;
                    setNormal(dda.enterAxis, dx, dy, dz);
                    hitMaterial = AcousticMaterial.WATER;
                    return true;
                }
            } else {
                if (world.loadedWaterlogged() && medium != VoxelWorld.WATER && dda.tExit() - dda.tEnter > EPSILON) {
                    // Water fills a waterlogged block around its shape: coming from the air, that is a surface.
                    if (dda.tEnter > maxDistance) return false;
                    hitT = dda.tEnter;
                    setNormal(dda.enterAxis, dx, dy, dz);
                    hitMaterial = AcousticMaterial.WATER;
                    return true;
                }
                if (world.loadedIsFullCube()) {
                    // A zero-length visit is a ray grazing an edge/corner, or leaving the face it starts on.
                    if (dda.tExit() - dda.tEnter > EPSILON) {
                        if (dda.tEnter > maxDistance) {
                            return false;
                        }
                        if (throughGap()) {
                            if (!dda.advance()) return false;
                            continue;
                        }
                        hitT = dda.tEnter;
                        setNormal(dda.enterAxis, dx, dy, dz);
                        hitMaterial = world.loadedMaterial();
                        return true;
                    }
                } else {
                    beginBoxes(MODE_FIRST_HIT, ox, oy, oz, dx, dy, dz, 0.0, maxDistance);
                    world.forEachLoadedBox(this);
                    if (boxBestT <= maxDistance && !throughGap()) {
                        hitT = boxBestT;
                        setNormal(boxBestAxis, dx, dy, dz);
                        hitMaterial = world.loadedMaterial();
                        return true;
                    }
                }
            }
            if (!dda.advance()) return false;
        }
    }

    /** axis < 0 means the origin was inside the shape; like vanilla, report the face opposing the ray. */
    private void setNormal(int axis, double dx, double dy, double dz) {
        if (axis < 0) {
            double ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
            axis = ax >= ay && ax >= az ? 0 : (ay >= az ? 1 : 2);
        }
        double d = axis == 0 ? dx : (axis == 1 ? dy : dz);
        int n = d > 0 ? -1 : 1;
        hitNormalX = axis == 0 ? n : 0;
        hitNormalY = axis == 1 ? n : 0;
        hitNormalZ = axis == 2 ? n : 0;
    }

    @Override
    public double hitT() {
        return hitT;
    }

    @Override
    public int hitNormalX() {
        return hitNormalX;
    }

    @Override
    public int hitNormalY() {
        return hitNormalY;
    }

    @Override
    public int hitNormalZ() {
        return hitNormalZ;
    }

    @Override
    public AcousticMaterial hitMaterial() {
        return hitMaterial;
    }

    @Override
    public boolean clearPath(double ax, double ay, double az, double bx, double by, double bz, double tolerance) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance - tolerance <= 0.0) {
            return true;
        }
        double inv = 1.0 / distance;
        // Whether it sees through gaps on the way depends only on the two blocks it connects.
        nextRay(VoxelTraversal.floor(ax) * 0x9E3779B97F4A7C15L ^ VoxelTraversal.floor(ay) * 0xC2B2AE3D27D4EB4FL
                ^ VoxelTraversal.floor(az) * 0x165667B19E3779F9L ^ VoxelTraversal.floor(bx) * 0x27D4EB2F165667C5L
                ^ VoxelTraversal.floor(by) * 0x94D049BB133111EBL ^ VoxelTraversal.floor(bz));
        if (!firstHit(ax, ay, az, dx * inv, dy * inv, dz * inv, distance)) {
            return true;
        }
        return hitT >= distance - tolerance;
    }

    @Override
    public double obstruction(double ax, double ay, double az, double bx, double by, double bz, double maxWeighted) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < EPSILON) {
            obstructionHf = 0.0;
            return 0.0;
        }
        double inv = 1.0 / distance;
        dx *= inv;
        dy *= inv;
        dz *= inv;
        int endX = VoxelTraversal.floor(bx), endY = VoxelTraversal.floor(by), endZ = VoxelTraversal.floor(bz);

        int medium = originMedium(ax, ay, az, dx, dy, dz);
        double total = 0.0, totalHf = 0.0;
        dda.init(ax, ay, az, dx, dy, dz, distance);
        while (true) {
            int kind = world.load(dda.x, dda.y, dda.z);
            if (kind == VoxelWorld.EMPTY && medium == VoxelWorld.EMPTY) {
                // Air in air, most of every walk: nothing to add and no surface to cross. (An empty end cell adds
                // nothing either, and the walk stops at the sound's distance anyway.)
                if (inEmptySection()) {
                    if (!skipSection()) break;
                    continue;
                }
                // Stop at the sound: a cell entered exactly at its distance is past it.
                if (!dda.advance() || dda.tEnter >= distance) break;
                continue;
            }
            boolean end = dda.x == endX && dda.y == endY && dda.z == endZ;
            // Solid blocks keep whatever medium surrounds them.
            int cellMedium = kind == VoxelWorld.SOLID ? (world.loadedWaterlogged() ? VoxelWorld.WATER : medium) : kind;
            // Crossing into the sound's own block is free, as for solids: something splashing, paddling or
            // bobbing at the surface is heard from both sides of it.
            if (cellMedium != medium && dda.tExit() - dda.tEnter > EPSILON) {
                if (!end) {
                    total += WATER_SURFACE_WEIGHT;
                    totalHf += WATER_SURFACE_WEIGHT * WATER_SURFACE_HF;
                }
                medium = cellMedium;
                if (total >= maxWeighted) break;
            }
            if (end) {
                break; // the sound's own block (note block, jukebox, bell...) never muffles it
            }
            if (kind == VoxelWorld.EMPTY) {
                if (medium == VoxelWorld.EMPTY && inEmptySection()) {
                    if (!skipSection()) break;
                    continue;
                }
            } else {
                double from = Math.max(dda.tEnter, 0.0);
                double to = Math.min(dda.tExit(), distance);
                double length;
                if (kind == VoxelWorld.WATER || world.loadedIsFullCube()) {
                    length = to - from;
                } else {
                    beginBoxes(MODE_LENGTH, ax, ay, az, dx, dy, dz, from, to);
                    world.forEachLoadedBox(this);
                    length = boxLength;
                }
                if (length > 0.0) {
                    double weighted = length * (kind == VoxelWorld.WATER ? WATER_VOLUME_WEIGHT : world.loadedTransmissionWeight());
                    double weightedHf = weighted * world.loadedMaterial().transmissionHf;
                    if (kind == VoxelWorld.SOLID && world.loadedPassMid() > 0.0) {
                        weighted = gapWeight(world.loadedPassBass(), weighted);
                        weightedHf = gapWeight(world.loadedPassTreble(), weightedHf);
                    }
                    total += weighted;
                    totalHf += weightedHf;
                    if (total >= maxWeighted) {
                        break;
                    }
                }
            }
            if (!dda.advance()) break;
        }
        obstructionHf = totalHf;
        return total;
    }

    @Override
    public double lastObstructionHf() {
        return obstructionHf;
    }

    private void beginBoxes(int mode, double ox, double oy, double oz, double dx, double dy, double dz,
                            double clipMin, double clipMax) {
        this.boxMode = mode;
        this.rayOx = ox;
        this.rayOy = oy;
        this.rayOz = oz;
        this.rayDx = dx;
        this.rayDy = dy;
        this.rayDz = dz;
        this.cellX = dda.x;
        this.cellY = dda.y;
        this.cellZ = dda.z;
        this.clipMin = clipMin;
        this.clipMax = clipMax;
        this.boxBestT = Double.POSITIVE_INFINITY;
        this.boxBestAxis = -1;
        this.boxLength = 0.0;
    }

    /** Slab test of the current ray against one box of the loaded cell. */
    @Override
    public void accept(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double entry = Double.NEGATIVE_INFINITY, exit = Double.POSITIVE_INFINITY;
        int entryAxis = -1;

        // x slab
        if (rayDx == 0.0) {
            double o = rayOx - cellX;
            if (o < minX || o > maxX) return;
        } else {
            double inv = 1.0 / rayDx;
            double t1 = (cellX + minX - rayOx) * inv, t2 = (cellX + maxX - rayOx) * inv;
            double lo = Math.min(t1, t2), hi = Math.max(t1, t2);
            if (lo > entry) { entry = lo; entryAxis = 0; }
            if (hi < exit) exit = hi;
        }
        // y slab
        if (rayDy == 0.0) {
            double o = rayOy - cellY;
            if (o < minY || o > maxY) return;
        } else {
            double inv = 1.0 / rayDy;
            double t1 = (cellY + minY - rayOy) * inv, t2 = (cellY + maxY - rayOy) * inv;
            double lo = Math.min(t1, t2), hi = Math.max(t1, t2);
            if (lo > entry) { entry = lo; entryAxis = 1; }
            if (hi < exit) exit = hi;
        }
        // z slab
        if (rayDz == 0.0) {
            double o = rayOz - cellZ;
            if (o < minZ || o > maxZ) return;
        } else {
            double inv = 1.0 / rayDz;
            double t1 = (cellZ + minZ - rayOz) * inv, t2 = (cellZ + maxZ - rayOz) * inv;
            double lo = Math.min(t1, t2), hi = Math.max(t1, t2);
            if (lo > entry) { entry = lo; entryAxis = 2; }
            if (hi < exit) exit = hi;
        }

        if (boxMode == MODE_FIRST_HIT) {
            double start = Math.max(entry, 0.0);
            if (exit - start > EPSILON && start < boxBestT) {
                boxBestT = start;
                boxBestAxis = entry < 0.0 ? -1 : entryAxis;
            }
        } else {
            double from = Math.max(entry, clipMin), to = Math.min(exit, clipMax);
            if (to > from) {
                boxLength += to - from;
            }
        }
    }
}
