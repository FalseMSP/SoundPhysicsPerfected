package com.redsmods.sound_physics_perfected.acoustics;

/**
 * Velocity of something that moves once per game tick (an entity, a sound following one, the listener), for the
 * Doppler effect: position change per tick, smoothed a little so a jerky path does not warble the pitch. A jump
 * too fast to be movement (a teleport) resets it to standing still. Pure Java (testable).
 */
public final class VelocityTracker {
    /** Game ticks per second: things move once per tick. */
    static final double TICKS_PER_SECOND = 20.0;
    /** Faster than this (blocks per second) is a teleport, not movement. Elytra with rockets reach ~35. */
    static final double MAX_SPEED = 100.0;
    /** Share of the newest measurement per tick. */
    static final double SMOOTHING = 0.5;

    private double x, y, z;
    private double vx, vy, vz;
    private boolean started;

    /** Records this tick's position. The first position gives no velocity. */
    public void update(double x, double y, double z) {
        if (started) {
            double nx = (x - this.x) * TICKS_PER_SECOND;
            double ny = (y - this.y) * TICKS_PER_SECOND;
            double nz = (z - this.z) * TICKS_PER_SECOND;
            if (nx * nx + ny * ny + nz * nz > MAX_SPEED * MAX_SPEED) {
                vx = vy = vz = 0.0;
            } else {
                vx += (nx - vx) * SMOOTHING;
                vy += (ny - vy) * SMOOTHING;
                vz += (nz - vz) * SMOOTHING;
            }
        }
        this.x = x;
        this.y = y;
        this.z = z;
        started = true;
    }

    public float vx() {
        return (float) vx;
    }

    public float vy() {
        return (float) vy;
    }

    public float vz() {
        return (float) vz;
    }

    /** Whether it moves at all (below 1 cm/s counts as still). */
    public boolean moving() {
        return vx * vx + vy * vy + vz * vz > 1.0E-4;
    }
}
