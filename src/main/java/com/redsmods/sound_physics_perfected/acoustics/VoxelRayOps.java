package com.redsmods.sound_physics_perfected.acoustics;

/**
 * {@link RayOps} implemented with a direct voxel walk. Several times cheaper than Level#clip because it
 * reads block states straight from the cell source, allocates nothing per step, and only builds shape
 * geometry for blocks that are not full cubes.
 */
public final class VoxelRayOps implements RayOps, VoxelWorld.BoxConsumer {
    private static final double EPSILON = 1.0E-9;

    private final VoxelWorld world;
    private final VoxelTraversal dda = new VoxelTraversal();

    private double hitT;
    private int hitNormalX, hitNormalY, hitNormalZ;
    private AcousticMaterial hitMaterial;

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

    @Override
    public boolean firstHit(double ox, double oy, double oz, double dx, double dy, double dz, double maxDistance) {
        dda.init(ox, oy, oz, dx, dy, dz, maxDistance);
        do {
            if (world.load(dda.x, dda.y, dda.z)) {
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
        } while (dda.advance());
        return false;
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
            return 0.0;
        }
        double inv = 1.0 / distance;
        dx *= inv;
        dy *= inv;
        dz *= inv;
        int endX = VoxelTraversal.floor(bx), endY = VoxelTraversal.floor(by), endZ = VoxelTraversal.floor(bz);

        double total = 0.0;
        dda.init(ax, ay, az, dx, dy, dz, distance);
        do {
            if (dda.x == endX && dda.y == endY && dda.z == endZ) {
                break; // the sound's own block (note block, jukebox, bell...) never muffles it
            }
            if (world.load(dda.x, dda.y, dda.z)) {
                double from = Math.max(dda.tEnter, 0.0);
                double to = Math.min(dda.tExit, distance);
                double length;
                if (world.loadedIsFullCube()) {
                    length = to - from;
                } else {
                    beginBoxes(MODE_LENGTH, ax, ay, az, dx, dy, dz, from, to);
                    world.forEachLoadedBox(this);
                    length = boxLength;
                }
                if (length > 0.0) {
                    total += length * world.loadedTransmissionWeight();
                    if (total >= maxWeighted) {
                        break;
                    }
                }
            }
        } while (dda.advance());
        return total;
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
