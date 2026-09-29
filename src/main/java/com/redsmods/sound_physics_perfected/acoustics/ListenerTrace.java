package com.redsmods.sound_physics_perfected.acoustics;

/**
 * The acoustic ray tracer, independent of Minecraft. Rays leave the listener, bounce specularly off
 * surfaces, and at every bounce point ("vertex") connect to each sound: through walls (red rays,
 * permeation mode) or only with line of sight (green rays). Blue rays check whether the vertex is visible
 * from the listener, which both feeds reverb statistics and shortens the path (the sound then arrives
 * from that vertex's direction).
 *
 * Per-sound contributions are summed into a flat array with {@link #STRIDE} slots per sound, so worker
 * threads can each fill their own array and the results are merged afterwards without locking.
 */
public final class ListenerTrace {
    public static final int STRIDE = 6;
    public static final int WEIGHT = 0, DIR_X = 1, DIR_Y = 2, DIR_Z = 3, DISTANCE = 4, MUFFLE = 5;

    private ListenerTrace() {
    }

    /** Sound positions and per-sound audible ranges for one trace. */
    public static final class Targets {
        public final int count;
        public final double[] x, y, z;
        /** Green-ray (line of sight mode) range; red rays use the segment length instead. */
        public final double[] greenRange;

        public Targets(int count) {
            this.count = count;
            this.x = new double[count];
            this.y = new double[count];
            this.z = new double[count];
            this.greenRange = new double[count];
        }

        public void set(int i, double x, double y, double z, double greenRange) {
            this.x[i] = x;
            this.y[i] = y;
            this.z[i] = z;
            this.greenRange[i] = greenRange;
        }
    }

    /** Per-chunk output: path sums per sound, how many paths reached each sound, and room statistics. */
    public static final class Accumulator {
        public final double[] sums;
        public final int[] counts;
        public final ReverbStats stats = new ReverbStats();

        public Accumulator(int targets) {
            this.sums = new double[targets * STRIDE];
            this.counts = new int[targets];
        }

        void add(int target, double weight, double dirX, double dirY, double dirZ, double distance, double muffle, int count) {
            int o = target * STRIDE;
            sums[o + WEIGHT] += weight;
            sums[o + DIR_X] += weight * dirX;
            sums[o + DIR_Y] += weight * dirY;
            sums[o + DIR_Z] += weight * dirZ;
            sums[o + DISTANCE] += weight * distance;
            sums[o + MUFFLE] += weight * muffle;
            counts[target] += count;
        }

        public void merge(Accumulator o) {
            for (int i = 0; i < sums.length; i++) sums[i] += o.sums[i];
            for (int i = 0; i < counts.length; i++) counts[i] += o.counts[i];
            stats.merge(o.stats);
        }
    }

    /** Traces rays [from, to) of the direction table (xyz triples) into the accumulator. */
    public static void traceRays(RayOps ops, TraceSettings s, double ex, double ey, double ez,
                                 double[] dirs, int from, int to, Targets targets, Accumulator acc) {
        ReverbStats stats = acc.stats;
        stats.segmentLength = s.segmentLength;
        for (int ray = from; ray < to; ray++) {
            stats.rays++;
            double px = ex, py = ey, pz = ez;
            double dx = dirs[ray * 3], dy = dirs[ray * 3 + 1], dz = dirs[ray * 3 + 2];
            // Direction the sound finally arrives from, and the length/energy of the path to the listener.
            double ax = dx, ay = dy, az = dz;
            double pathLength = 0.0;
            double energy = 1.0;
            double legacyDistance = 0.0;
            double remaining = s.maxTotalDistance;

            for (int bounce = 0; bounce <= s.bounces && remaining > 0.0; bounce++) {
                double segment = Math.min(s.segmentLength, remaining);
                if (!ops.firstHit(px, py, pz, dx, dy, dz, segment)) {
                    // Escaped into open air: that energy never comes back.
                    stats.escape(bounce);
                    break;
                }
                double t = ops.hitT();
                double hx = px + dx * t, hy = py + dy * t, hz = pz + dz * t;
                int nx = ops.hitNormalX(), ny = ops.hitNormalY(), nz = ops.hitNormalZ();
                AcousticMaterial material = ops.hitMaterial();
                double cosIncidence = Math.abs(dx * nx + dy * ny + dz * nz);

                pathLength += t;
                legacyDistance = (legacyDistance + t) / s.bounceAbsorption;
                stats.hit(bounce, t, material, cosIncidence);

                // Energy kept by this reflection: the surface's own reflectivity times the user's bounce loss
                // (bounceAbsorption is an amplitude factor, hence squared).
                double reflection = s.bounceAbsorption * s.bounceAbsorption * material.reflectivity();

                boolean shortcut = false;
                if (s.reverb) {
                    double tx = ex - hx, ty = ey - hy, tz = ez - hz;
                    double toListener = Math.sqrt(tx * tx + ty * ty + tz * tz);
                    boolean visible = true;
                    if (toListener > 1.0E-6) {
                        double inv = 1.0 / toListener;
                        visible = ops.clearPath(hx + tx * inv * 0.1, hy + ty * inv * 0.1, hz + tz * inv * 0.1,
                                ex, ey, ez, 0.0);
                    }
                    stats.blueRay(visible, legacyDistance);
                    if (visible && toListener > 1.0E-6) {
                        // A diffuse reflection straight back to the listener is the shortest path through here.
                        double inv = 1.0 / toListener;
                        ax = -tx * inv;
                        ay = -ty * inv;
                        az = -tz * inv;
                        pathLength = toListener;
                        energy = reflection;
                        legacyDistance = Math.max(toListener - 0.1, 0.0) / s.bounceAbsorption;
                        shortcut = true;
                    }
                }
                if (!shortcut) {
                    energy *= reflection;
                }

                connectVertex(ops, s, hx, hy, hz, pathLength, energy, ax, ay, az, targets, acc);

                // Specular reflection about the (axis aligned) surface normal.
                if (nx != 0) dx = -dx;
                if (ny != 0) dy = -dy;
                if (nz != 0) dz = -dz;
                px = hx + dx * 0.01;
                py = hy + dy * 0.01;
                pz = hz + dz * 0.01;
                remaining -= t;
            }
        }
    }

    private static void connectVertex(RayOps ops, TraceSettings s, double vx, double vy, double vz,
                                      double pathLength, double energy, double ax, double ay, double az,
                                      Targets targets, Accumulator acc) {
        for (int j = 0; j < targets.count; j++) {
            double dx = targets.x[j] - vx, dy = targets.y[j] - vy, dz = targets.z[j] - vz;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (s.permeation) {
                if (distance + pathLength > s.segmentLength) continue;
                double obstruction = ops.obstruction(vx, vy, vz, targets.x[j], targets.y[j], targets.z[j], s.maxBlocksPermeated);
                double transmission = Math.pow(s.permeationAbsorption, obstruction);
                // weight() already includes one transmission factor: weight ~ energy, muffle ~ amplitude.
                double weight = s.weight(pathLength, obstruction, distance) * transmission * energy;
                acc.add(j, weight, ax, ay, az, distance, transmission, 1);
            } else {
                if (distance + pathLength > targets.greenRange[j]) continue;
                if (!ops.clearPath(vx, vy, vz, targets.x[j], targets.y[j], targets.z[j], 1.0)) continue;
                double weight = s.weight(pathLength, 0.0, distance) * energy;
                acc.add(j, weight, ax, ay, az, distance, 0.0, 1);
            }
        }
    }

    /**
     * The direct listener→sound path. Every ray used to repeat this identical check (N times, each
     * labelled with that ray's own direction, which averaged out to no direction at all). It is now done
     * once, with the true direction, and weighted N times so loudness is unchanged.
     *
     * @return the weighted obstruction between listener and sound (0 = clear line of sight)
     */
    public static double traceDirect(RayOps ops, TraceSettings s, int rayCount, double ex, double ey, double ez,
                                     Targets targets, int j, Accumulator acc) {
        double tx = targets.x[j], ty = targets.y[j], tz = targets.z[j];
        double dx = tx - ex, dy = ty - ey, dz = tz - ez;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double ux = 0.0, uy = 0.0, uz = 0.0;
        if (distance > 1.0E-6) {
            ux = dx / distance;
            uy = dy / distance;
            uz = dz / distance;
        }
        if (s.permeation) {
            double obstruction = ops.obstruction(ex, ey, ez, tx, ty, tz, s.maxBlocksPermeated);
            if (distance <= s.segmentLength) {
                double transmission = Math.pow(s.permeationAbsorption, obstruction);
                double weight = s.weight(0.0, obstruction, distance) * transmission;
                acc.add(j, weight * rayCount, ux, uy, uz, distance, transmission, rayCount);
            }
            return obstruction;
        }
        boolean visible = ops.clearPath(ex, ey, ez, tx, ty, tz, 1.0);
        if (distance <= targets.greenRange[j] && visible) {
            double weight = s.weight(0.0, 0.0, distance);
            acc.add(j, weight * rayCount, ux, uy, uz, distance, 0.0, rayCount);
        }
        return visible ? 0.0 : Double.POSITIVE_INFINITY;
    }

    /** Evenly spread directions on the unit sphere (Fibonacci lattice), as xyz triples. */
    public static double[] fibonacciSphere(int count) {
        double[] dirs = new double[count * 3];
        double goldenRatio = (1.0 + Math.sqrt(5.0)) / 2.0;
        for (int i = 0; i < count; i++) {
            double theta = 2.0 * Math.PI * i / goldenRatio;
            double phi = Math.acos(1.0 - 2.0 * (i + 0.5) / count);
            dirs[i * 3] = Math.sin(phi) * Math.cos(theta);
            dirs[i * 3 + 1] = Math.cos(phi);
            dirs[i * 3 + 2] = Math.sin(phi) * Math.sin(theta);
        }
        return dirs;
    }
}
