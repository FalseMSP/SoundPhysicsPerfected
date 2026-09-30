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
        this.world = world;
    }

    // Section-emptiness cache for the current walk.
    private int sectionX, sectionY, sectionZ;
    private boolean sectionEmpty;

    private void resetSection() {
        sectionX = Integer.MIN_VALUE;
    }

    /** Whether the current cell lies in a completely empty 16x16x16 section (checked once per section). */
    private boolean inEmptySection() {
        int sx = dda.x >> 4, sy = dda.y >> 4, sz = dda.z >> 4;
        if (sx != sectionX || sy != sectionY || sz != sectionZ) {
            sectionX = sx;
            sectionY = sy;
            sectionZ = sz;
            sectionEmpty = world.sectionEmpty(dda.x, dda.y, dda.z);
        }
        return sectionEmpty;
    }

    /** The medium (air or water) a ray starting at o in direction d travels through first. */
    private int originMedium(double ox, double oy, double oz, double dx, double dy, double dz) {
        int kind = world.load(VoxelTraversal.floor(ox + dx * 1.0E-4), VoxelTraversal.floor(oy + dy * 1.0E-4),
                VoxelTraversal.floor(oz + dz * 1.0E-4));
        return kind == VoxelWorld.WATER ? VoxelWorld.WATER : VoxelWorld.EMPTY;
    }

    @Override
    public boolean firstHit(double ox, double oy, double oz, double dx, double dy, double dz, double maxDistance) {
        int medium = originMedium(ox, oy, oz, dx, dy, dz);
        dda.init(ox, oy, oz, dx, dy, dz, maxDistance);
        resetSection();
        while (true) {
            int kind = world.load(dda.x, dda.y, dda.z);
            if (kind == VoxelWorld.EMPTY && medium == VoxelWorld.EMPTY) {
                // Nothing in this whole section: jump straight across it.
                if (inEmptySection()) {
                    if (!dda.skipBox(sectionX << 4, sectionY << 4, sectionZ << 4, 16)) return false;
                    continue;
                }
            } else if (kind != VoxelWorld.SOLID) {
                // Air <-> water: the surface reflects like a very hard wall.
                if (kind != medium && dda.tExit - dda.tEnter > EPSILON) {
                    if (dda.tEnter > maxDistance) return false;
                    hitT = dda.tEnter;
                    setNormal(dda.enterAxis, dx, dy, dz);
                    hitMaterial = AcousticMaterial.WATER;
                    return true;
                }
            } else {
                if (world.loadedIsFullCube()) {
                    // A zero-length visit is a ray grazing an edge/corner, or leaving the face it starts on.
                    if (dda.tExit - dda.tEnter > EPSILON) {
                        if (dda.tEnter > maxDistance) {
                            return false;
                        }
                        hitT = dda.tEnter;
                        setNormal(dda.enterAxis, dx, dy, dz);
                        hitMaterial = world.loadedMaterial();
                        return true;
                    }
                } else {
                    beginBoxes(MODE_FIRST_HIT, ox, oy, oz, dx, dy, dz, 0.0, maxDistance);
                    world.forEachLoadedBox(this);
                    if (boxBestT <= maxDistance) {
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
        resetSection();
        while (true) {
            boolean end = dda.x == endX && dda.y == endY && dda.z == endZ;
            int kind = world.load(dda.x, dda.y, dda.z);
            // Solid blocks keep whatever medium surrounds them.
            int cellMedium = kind == VoxelWorld.SOLID ? medium : kind;
            // Crossing into the sound's own block is free, as for solids: something splashing, paddling or
            // bobbing at the surface is heard from both sides of it.
            if (cellMedium != medium && dda.tExit - dda.tEnter > EPSILON) {
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
                    if (!dda.skipBox(sectionX << 4, sectionY << 4, sectionZ << 4, 16)) break;
                    continue;
                }
            } else {
                double from = Math.max(dda.tEnter, 0.0);
                double to = Math.min(dda.tExit, distance);
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
                    total += weighted;
                    totalHf += weighted * world.loadedMaterial().transmissionHf;
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
