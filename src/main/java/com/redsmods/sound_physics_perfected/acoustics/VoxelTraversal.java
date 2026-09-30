package com.redsmods.sound_physics_perfected.acoustics;

/**
 * Amanatides–Woo voxel walk over unit cells. Pure Java (no Minecraft types) so it can be unit tested.
 *
 * The ray is parametrised by distance: point(t) = origin + t * dir, with dir normalised.
 * Like vanilla BlockGetter#traverseBlocks, the starting cell is picked with the origin nudged a hair
 * backwards, so a ray that starts exactly on a block face and points away from that block does not
 * register the block it is leaving.
 */
public final class VoxelTraversal {
    public int x, y, z;
    /** Ray distance at which the current cell was entered (0 for the first cell). */
    public double tEnter;
    /** Ray distance at which the current cell is left. */
    public double tExit;
    /** Axis the current cell was entered through (0 = x, 1 = y, 2 = z), or -1 for the starting cell. */
    public int enterAxis;

    private int stepX, stepY, stepZ;
    private double tMaxX, tMaxY, tMaxZ;
    private double tDeltaX, tDeltaY, tDeltaZ;
    private double maxT;
    private double ox, oy, oz, dx, dy, dz;

    public void init(double ox, double oy, double oz, double dx, double dy, double dz, double maxT) {
        this.maxT = maxT;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.x = floor(ox - dx * 1.0E-7);
        this.y = floor(oy - dy * 1.0E-7);
        this.z = floor(oz - dz * 1.0E-7);

        this.stepX = dx > 0 ? 1 : (dx < 0 ? -1 : 0);
        this.stepY = dy > 0 ? 1 : (dy < 0 ? -1 : 0);
        this.stepZ = dz > 0 ? 1 : (dz < 0 ? -1 : 0);

        this.tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        this.tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        this.tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);

        this.tMaxX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.max(0.0, ((stepX > 0 ? x + 1 : x) - ox) / dx);
        this.tMaxY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.max(0.0, ((stepY > 0 ? y + 1 : y) - oy) / dy);
        this.tMaxZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.max(0.0, ((stepZ > 0 ? z + 1 : z) - oz) / dz);

        this.tEnter = 0.0;
        this.enterAxis = -1;
        this.tExit = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));
    }

    /** Steps into the next cell. Returns false once the next cell would start beyond maxT. */
    public boolean advance() {
        if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
            tEnter = tMaxX;
            x += stepX;
            tMaxX += tDeltaX;
            enterAxis = 0;
        } else if (tMaxY <= tMaxZ) {
            tEnter = tMaxY;
            y += stepY;
            tMaxY += tDeltaY;
            enterAxis = 1;
        } else {
            tEnter = tMaxZ;
            z += stepZ;
            tMaxZ += tDeltaZ;
            enterAxis = 2;
        }
        tExit = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));
        return tEnter <= maxT;
    }

    /**
     * Jumps past the axis-aligned box [bx, bx+size) x [by, by+size) x [bz, bz+size) that contains the current cell
     * (e.g. a chunk section with nothing in it), landing in the first cell after it. Returns false once that cell
     * would start beyond maxT.
     */
    public boolean skipBox(int bx, int by, int bz, int size) {
        double exitX = stepX == 0 ? Double.POSITIVE_INFINITY : ((stepX > 0 ? bx + size : bx) - ox) / dx;
        double exitY = stepY == 0 ? Double.POSITIVE_INFINITY : ((stepY > 0 ? by + size : by) - oy) / dy;
        double exitZ = stepZ == 0 ? Double.POSITIVE_INFINITY : ((stepZ > 0 ? bz + size : bz) - oz) / dz;
        int axis = exitX <= exitY && exitX <= exitZ ? 0 : (exitY <= exitZ ? 1 : 2);
        double t = axis == 0 ? exitX : (axis == 1 ? exitY : exitZ);
        if (t > maxT) return false;
        if (t < tEnter) return advance(); // numerically at the edge already

        // Land in the neighbouring cell across the exited face; the other axes follow the ray.
        int nx = axis == 0 ? (stepX > 0 ? bx + size : bx - 1) : floor(ox + dx * t);
        int ny = axis == 1 ? (stepY > 0 ? by + size : by - 1) : floor(oy + dy * t);
        int nz = axis == 2 ? (stepZ > 0 ? bz + size : bz - 1) : floor(oz + dz * t);
        x = nx;
        y = ny;
        z = nz;
        tMaxX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.max(t, ((stepX > 0 ? x + 1 : x) - ox) / dx);
        tMaxY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.max(t, ((stepY > 0 ? y + 1 : y) - oy) / dy);
        tMaxZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.max(t, ((stepZ > 0 ? z + 1 : z) - oz) / dz);
        tEnter = t;
        enterAxis = axis;
        tExit = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));
        return true;
    }

    /** Sign of the step along the given axis (the ray's direction sign). */
    public int step(int axis) {
        return axis == 0 ? stepX : (axis == 1 ? stepY : stepZ);
    }

    public static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
