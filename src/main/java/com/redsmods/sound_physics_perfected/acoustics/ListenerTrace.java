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
    public static final int STRIDE = 14;
    public static final int WEIGHT = 0, DIR_X = 1, DIR_Y = 2, DIR_Z = 3, DISTANCE = 4, MUFFLE = 5, MUFFLE_HF = 6;
    /**
     * Energy loudness (after Vercidium): per band, the energy of rays that found the sound in plain view (counted
     * once per ray, at the first bounce that sees it), the through-wall energy of every other bounce and how many
     * bounces tried, and the direct path's own transmission.
     */
    public static final int OCC_LF = 7, OCC_HF = 8, PERM_LF = 9, PERM_HF = 10, PERM_COUNT = 11, DIRECT_LF = 12,
            DIRECT_HF = 13;

    private ListenerTrace() {
    }

    /** Sound positions and per-sound audible ranges for one trace. */
    public static final class Targets {
        public final int count;
        public final double[] x, y, z;
        /** Green-ray (line of sight mode) range; red rays use the segment length instead. */
        public final double[] greenRange;
        /** Sounds whose result is already known (cached): not traced. */
        public final boolean[] skip;

        public Targets(int count) {
            this.count = count;
            this.x = new double[count];
            this.y = new double[count];
            this.z = new double[count];
            this.greenRange = new double[count];
            this.skip = new boolean[count];
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

        void add(int target, double weight, double dirX, double dirY, double dirZ, double distance, double muffle,
                 double muffleHf, int count) {
            int o = target * STRIDE;
            sums[o + WEIGHT] += weight;
            sums[o + DIR_X] += weight * dirX;
            sums[o + DIR_Y] += weight * dirY;
            sums[o + DIR_Z] += weight * dirZ;
            sums[o + DISTANCE] += weight * distance;
            sums[o + MUFFLE] += weight * muffle;
            sums[o + MUFFLE_HF] += weight * muffleHf;
            counts[target] += count;
        }

        void addEnergy(int target, int slot, double value) {
            sums[target * STRIDE + slot] += value;
        }

        public void merge(Accumulator o) {
            for (int i = 0; i < sums.length; i++) sums[i] += o.sums[i];
            for (int i = 0; i < counts.length; i++) counts[i] += o.counts[i];
            stats.merge(o.stats);
        }
    }

    /**
     * The listener's ray paths (trail caching, after Vercidium): every bounce that connects to sounds, with what
     * the path to it carries. While the listener stands still and nothing around changes, new sounds then only
     * need connecting to these bounces. Written by one worker, read-only afterwards.
     */
    public static final class Trail {
        static final int V = 10; // hx, hy, hz, pathLength, energy, energyLf, energyHf, ax, ay, az
        int rays;
        int[] rayEnd = new int[64]; // vertex count after each ray
        double[] v = new double[64 * V];
        int vertices;

        void add(double hx, double hy, double hz, double pathLength, double energy, double energyLf,
                 double energyHf, double ax, double ay, double az) {
            if ((vertices + 1) * V > v.length) v = java.util.Arrays.copyOf(v, v.length * 2);
            int o = vertices++ * V;
            v[o] = hx;
            v[o + 1] = hy;
            v[o + 2] = hz;
            v[o + 3] = pathLength;
            v[o + 4] = energy;
            v[o + 5] = energyLf;
            v[o + 6] = energyHf;
            v[o + 7] = ax;
            v[o + 8] = ay;
            v[o + 9] = az;
        }

        void endRay() {
            if (rays == rayEnd.length) rayEnd = java.util.Arrays.copyOf(rayEnd, rays * 2);
            rayEnd[rays++] = vertices;
        }
    }

    /** Connects sounds to a recorded trail: the same result as {@link #traceRays}, without re-tracing the rays. */
    public static void connectTrail(RayOps ops, TraceSettings s, Trail trail, Targets targets, Accumulator acc) {
        boolean[] found = new boolean[targets.count];
        double[] d = trail.v;
        int vertex = 0;
        for (int ray = 0; ray < trail.rays; ray++) {
            java.util.Arrays.fill(found, false);
            for (; vertex < trail.rayEnd[ray]; vertex++) {
                int o = vertex * Trail.V;
                connectVertex(ops, s, d[o], d[o + 1], d[o + 2], d[o + 3], d[o + 4], d[o + 5], d[o + 6], found,
                        d[o + 7], d[o + 8], d[o + 9], targets, acc);
            }
        }
    }

    /**
     * Whether the listener can see a surface, shared by every thread of one trace, per block face. Used for the
     * bounces that only measure the room (where it only steers the late reverb's direction); bounces that connect
     * to sounds are always checked exactly.
     */
    public static final class Visibility {
        private final java.util.concurrent.ConcurrentHashMap<Long, Boolean> faces =
                new java.util.concurrent.ConcurrentHashMap<>();

        boolean visible(RayOps ops, double hx, double hy, double hz, int nx, int ny, int nz,
                        double ex, double ey, double ez) {
            // The face is identified by the (air) cell just off it and which way it faces.
            long cx = (long) Math.floor(hx + nx * 1.0E-3), cy = (long) Math.floor(hy + ny * 1.0E-3),
                    cz = (long) Math.floor(hz + nz * 1.0E-3);
            int face = nx != 0 ? (nx > 0 ? 0 : 1) : ny != 0 ? (ny > 0 ? 2 : 3) : (nz > 0 ? 4 : 5);
            long key = ((cx & 0xFFFFF) << 43) | ((cy & 0xFFFFF) << 23) | ((cz & 0xFFFFF) << 3) | face;
            Boolean known = faces.get(key);
            if (known != null) return known;
            boolean seen = clear(ops, hx, hy, hz, ex, ey, ez);
            faces.put(key, seen);
            return seen;
        }
    }

    /** Line of sight from a surface point to the listener. */
    private static boolean clear(RayOps ops, double hx, double hy, double hz, double ex, double ey, double ez) {
        double tx = ex - hx, ty = ey - hy, tz = ez - hz;
        double toListener = Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (toListener <= 1.0E-6) return true;
        double inv = 1.0 / toListener;
        return ops.clearPath(hx + tx * inv * 0.1, hy + ty * inv * 0.1, hz + tz * inv * 0.1, ex, ey, ez, 0.0);
    }

    /** Below this energy a room-measuring bounce no longer moves the late reverb's direction noticeably. */
    static final double LATE_MIN_ENERGY = 0.01;

    /** Traces rays [from, to) of the direction table (xyz triples) into the accumulator. */
    public static void traceRays(RayOps ops, TraceSettings s, double ex, double ey, double ez,
                                 double[] dirs, int from, int to, Targets targets, Accumulator acc) {
        traceRays(ops, s, ex, ey, ez, dirs, from, to, targets, acc, null, null);
    }

    /** As above, also recording the rays' connecting bounces into {@code trail} when given. */
    public static void traceRays(RayOps ops, TraceSettings s, double ex, double ey, double ez,
                                 double[] dirs, int from, int to, Targets targets, Accumulator acc, Trail trail,
                                 Visibility visibility) {
        ReverbStats stats = acc.stats;
        stats.segmentLength = s.segmentLength;
        boolean[] found = new boolean[targets.count]; // sounds this ray has already seen from a bounce
        // The echogram only matters when rays keep going to measure the room (the in-between traces for new sounds
        // keep the last room estimate).
        Echo echo = s.reverb && s.measureRoom ? new Echo(s.reverbBounces + 1) : null;
        for (int ray = from; ray < to; ray++) {
            stats.rays++;
            java.util.Arrays.fill(found, false);
            if (echo != null) echo.start();
            double px = ex, py = ey, pz = ez;
            double dx = dirs[ray * 3], dy = dirs[ray * 3 + 1], dz = dirs[ray * 3 + 2];
            // Direction the sound finally arrives from, and the length/energy of the path to the listener.
            double ax = dx, ay = dy, az = dz;
            double pathLength = 0.0;
            double energy = 1.0;
            double energyLf = 1.0, energyHf = 1.0; // the same, per band, for energy loudness
            double chainEnergy = 1.0; // energy left in the ray itself (no shortcuts), for the reverb direction
            double legacyDistance = 0.0;
            double remaining = s.maxTotalDistance;
            double reverbRemaining = s.segmentLength * Math.max(1, s.reverbBounces);
            int lastBounce = s.reverb ? s.reverbBounces : s.bounces;

            for (int bounce = 0; bounce <= lastBounce; bounce++) {
                // The first bounces connect to sounds as before; later ones only feed the room estimate.
                boolean connecting = bounce <= s.bounces && remaining > 0.0;
                double budget = connecting ? remaining : reverbRemaining;
                if (budget <= 0.0 || (!connecting && !s.reverb)) break;
                double segment = Math.min(s.segmentLength, budget);
                if (!ops.firstHit(px, py, pz, dx, dy, dz, segment)) {
                    if (connecting && segment < s.segmentLength) {
                        // Out of connecting budget, not out in the open: carry on for the room estimate only.
                        stats.legacyEscape(); // (the legacy modes counted it as an escape)
                        remaining = 0.0;
                        bounce--;
                        continue;
                    }
                    // Escaped into open air: that energy never comes back.
                    stats.escape(bounce, connecting);
                    if (echo != null) echo.escaped = true;
                    break;
                }
                double t = ops.hitT();
                double hx = px + dx * t, hy = py + dy * t, hz = pz + dz * t;
                int nx = ops.hitNormalX(), ny = ops.hitNormalY(), nz = ops.hitNormalZ();
                AcousticMaterial material = ops.hitMaterial();
                double cosIncidence = Math.abs(dx * nx + dy * ny + dz * nz);
                if (echo != null) echo.hit(t, material);

                pathLength += t;
                if (connecting) legacyDistance = (legacyDistance + t) / s.bounceAbsorption;
                stats.hit(bounce, t, material, cosIncidence, connecting);
                if (bounce == 0) {
                    // Reverb panning is horizontal: the floor is always the nearest reflector, and a pan that
                    // points straight down would collapse the reflections to mono on stereo output.
                    double near = Math.max(t, 1.0);
                    stats.early(dx, 0.0, dz, material.reflectivity() / (near * near));
                }

                // Energy kept by this reflection: the surface's own reflectivity times the user's bounce loss
                // (bounceAbsorption is an amplitude factor, hence squared).
                double loss = s.bounceAbsorption * s.bounceAbsorption;
                double reflection = loss * material.reflectivity();
                double reflectionLf = loss * (1.0 - material.absorptionLow);
                double reflectionHf = loss * (1.0 - material.absorptionHigh);
                chainEnergy *= reflection;

                boolean shortcut = false;
                // Room-measuring bounces only need visibility for the late reverb's direction: not at all when that
                // is off or the ray has next to no energy left, and shared per block face within this trace.
                boolean lateOnly = !connecting;
                if (s.reverb && (!lateOnly || (bounce >= 1 && s.lateDirections && chainEnergy > LATE_MIN_ENERGY))) {
                    double tx = ex - hx, ty = ey - hy, tz = ez - hz;
                    double toListener = Math.sqrt(tx * tx + ty * ty + tz * tz);
                    boolean visible = lateOnly && visibility != null
                            ? visibility.visible(ops, hx, hy, hz, nx, ny, nz, ex, ey, ez)
                            : clear(ops, hx, hy, hz, ex, ey, ez);
                    if (connecting) stats.blueRay(visible, legacyDistance);
                    if (visible && toListener > 1.0E-6) {
                        double inv = 1.0 / toListener;
                        if (bounce >= 1) stats.late(-tx * inv, 0.0, -tz * inv, chainEnergy);
                        if (connecting) {
                            // A diffuse reflection straight back to the listener is the shortest path through here.
                            ax = -tx * inv;
                            ay = -ty * inv;
                            az = -tz * inv;
                            pathLength = toListener;
                            energy = reflection;
                            energyLf = reflectionLf;
                            energyHf = reflectionHf;
                            legacyDistance = Math.max(toListener - 0.1, 0.0) / s.bounceAbsorption;
                            shortcut = true;
                        }
                    }
                }
                if (!shortcut) {
                    energy *= reflection;
                    energyLf *= reflectionLf;
                    energyHf *= reflectionHf;
                }

                if (connecting) {
                    // Connect from just off the surface, on the side the ray arrived from: a bounce off the water
                    // surface is in the air (or under water) and has to cross the surface to reach the other side.
                    double cx = hx + nx * 1.0E-3, cy = hy + ny * 1.0E-3, cz = hz + nz * 1.0E-3;
                    connectVertex(ops, s, cx, cy, cz, pathLength, energy, energyLf, energyHf, found, ax, ay, az,
                            targets, acc);
                    if (trail != null) trail.add(cx, cy, cz, pathLength, energy, energyLf, energyHf, ax, ay, az);
                    remaining -= t;
                } else {
                    reverbRemaining -= t;
                }

                // Reflect: mirror-like, blended towards a diffuse direction by the surface's roughness.
                if (nx != 0) dx = -dx;
                if (ny != 0) dy = -dy;
                if (nz != 0) dz = -dz;
                if (material.scattering > 0.0f) {
                    long h = mix(((long) ray << 8) ^ bounce ^ 0x5DEECE66DL);
                    double u1 = (h >>> 11) * 0x1.0p-53;
                    double u2 = (mix(h) >>> 11) * 0x1.0p-53;
                    // Lambertian sample: surface normal plus a uniformly random unit vector.
                    double rz = 2.0 * u1 - 1.0, rr = Math.sqrt(Math.max(0.0, 1.0 - rz * rz)), phi = 2.0 * Math.PI * u2;
                    double lx = nx + rr * Math.cos(phi), ly = ny + rr * Math.sin(phi), lz = nz + rz;
                    double ll = Math.sqrt(lx * lx + ly * ly + lz * lz);
                    if (ll > 1.0E-6) {
                        double sc = material.scattering;
                        double qx = dx * (1 - sc) + lx / ll * sc, qy = dy * (1 - sc) + ly / ll * sc, qz = dz * (1 - sc) + lz / ll * sc;
                        double ql = Math.sqrt(qx * qx + qy * qy + qz * qz);
                        if (ql > 1.0E-6 && (qx * nx + qy * ny + qz * nz) / ql > 1.0E-3) {
                            dx = qx / ql;
                            dy = qy / ql;
                            dz = qz / ql;
                        }
                    }
                }
                px = hx + dx * 0.01;
                py = hy + dy * 0.01;
                pz = hz + dz * 0.01;
            }
            if (echo != null) echo.finish(stats);
            if (trail != null) trail.endRay();
        }
    }

    /** One ray's reflections, for the echogram (see {@link ReverbStats#echogramRay}). Reused ray to ray. */
    private static final class Echo {
        final double[] hitL, low, mid, high;
        int hits;
        double travelled, eLow, eMid, eHigh;
        boolean escaped;

        Echo(int maxHits) {
            hitL = new double[maxHits];
            low = new double[maxHits];
            mid = new double[maxHits];
            high = new double[maxHits];
        }

        void start() {
            hits = 0;
            travelled = 0.0;
            eLow = eMid = eHigh = 1.0;
            escaped = false;
        }

        void hit(double t, AcousticMaterial material) {
            travelled += t;
            if (hits == hitL.length) return;
            eLow *= 1.0 - material.absorptionLow;
            eMid *= 1.0 - material.absorptionMid;
            eHigh *= 1.0 - material.absorptionHigh;
            hitL[hits] = travelled;
            low[hits] = eLow;
            mid[hits] = eMid;
            high[hits] = eHigh;
            hits++;
        }

        void finish(ReverbStats stats) {
            // An escaping ray leaves about one of its own average segments after its last reflection.
            double escapeL = travelled + (hits > 0 ? travelled / hits : 0.0);
            stats.echogramRay(hits, hitL, low, mid, high, escaped, escapeL);
        }
    }

    /** What a sound's own surroundings sound like, and how much of that reverb reaches the listener. */
    public static final class SourceEnvironment {
        public final ReverbStats stats = new ReverbStats();
        /** Energy of the sound's reflections that the listener can see, and in total. */
        public double visibleEnergy, totalEnergy;
        /** Energy-weighted horizontal direction from the listener to those visible reflections. */
        public double dirX, dirZ;

        /** Fraction of the sound's reverberant energy that happens where the listener can hear it directly. */
        public double visibleFraction() {
            return totalEnergy > 0.0 ? visibleEnergy / totalEnergy : 0.0;
        }
    }

    /**
     * Rays from a sound source, to estimate the reverb of the space the sound is in (which may not be the
     * listener's) and how much of it reaches the listener. Like the listener's rays: scattered bounces,
     * materials, escapes to open air.
     */
    public static void traceSource(RayOps ops, TraceSettings s, double sx, double sy, double sz,
                                   double ex, double ey, double ez, double[] dirs, int bounces, SourceEnvironment env) {
        ReverbStats stats = env.stats;
        stats.segmentLength = s.segmentLength;
        int rays = dirs.length / 3;
        Echo echo = new Echo(bounces + 1);
        for (int ray = 0; ray < rays; ray++) {
            stats.rays++;
            echo.start();
            double px = sx, py = sy, pz = sz;
            double dx = dirs[ray * 3], dy = dirs[ray * 3 + 1], dz = dirs[ray * 3 + 2];
            if (ops.firstHit(px, py, pz, dx, dy, dz, 1.0E-3)) {
                // A sound inside its own block (note block, jukebox, chest...) is heard from its faces: start
                // where the ray leaves that block, not with a hit at distance 0 inside it.
                double exit = cellExit(px, py, pz, dx, dy, dz) + 1.0E-4;
                px += dx * exit;
                py += dy * exit;
                pz += dz * exit;
            }
            double energy = 1.0;
            for (int bounce = 0; bounce <= bounces; bounce++) {
                if (!ops.firstHit(px, py, pz, dx, dy, dz, s.segmentLength)) {
                    stats.escape(bounce, false);
                    echo.escaped = true;
                    break;
                }
                double t = ops.hitT();
                double hx = px + dx * t, hy = py + dy * t, hz = pz + dz * t;
                int nx = ops.hitNormalX(), ny = ops.hitNormalY(), nz = ops.hitNormalZ();
                AcousticMaterial material = ops.hitMaterial();
                stats.hit(bounce, t, material, Math.abs(dx * nx + dy * ny + dz * nz), false);
                echo.hit(t, material);
                energy *= s.bounceAbsorption * s.bounceAbsorption * material.reflectivity();

                // Does this reflection reach the listener directly?
                double tx = ex - hx, ty = ey - hy, tz = ez - hz;
                double toListener = Math.sqrt(tx * tx + ty * ty + tz * tz);
                env.totalEnergy += energy;
                if (toListener > 1.0E-6) {
                    double inv = 1.0 / toListener;
                    // From just off the surface on the ray's side: a reflection under the water surface is not in
                    // view of a listener above it.
                    if (ops.clearPath(hx + nx * 1.0E-3, hy + ny * 1.0E-3, hz + nz * 1.0E-3, ex, ey, ez, 0.0)) {
                        env.visibleEnergy += energy;
                        env.dirX -= tx * inv * energy;
                        env.dirZ -= tz * inv * energy;
                    }
                }

                if (nx != 0) dx = -dx;
                if (ny != 0) dy = -dy;
                if (nz != 0) dz = -dz;
                if (material.scattering > 0.0f) {
                    long h = mix(((long) ray << 8) ^ bounce ^ 0x2545F4914F6CDD1DL);
                    double u1 = (h >>> 11) * 0x1.0p-53, u2 = (mix(h) >>> 11) * 0x1.0p-53;
                    double rz = 2.0 * u1 - 1.0, rr = Math.sqrt(Math.max(0.0, 1.0 - rz * rz)), phi = 2.0 * Math.PI * u2;
                    double lx = nx + rr * Math.cos(phi), ly = ny + rr * Math.sin(phi), lz = nz + rz;
                    double ll = Math.sqrt(lx * lx + ly * ly + lz * lz);
                    if (ll > 1.0E-6) {
                        double sc = material.scattering;
                        double qx = dx * (1 - sc) + lx / ll * sc, qy = dy * (1 - sc) + ly / ll * sc, qz = dz * (1 - sc) + lz / ll * sc;
                        double ql = Math.sqrt(qx * qx + qy * qy + qz * qz);
                        if (ql > 1.0E-6 && (qx * nx + qy * ny + qz * nz) / ql > 1.0E-3) {
                            dx = qx / ql;
                            dy = qy / ql;
                            dz = qz / ql;
                        }
                    }
                }
                px = hx + dx * 0.01;
                py = hy + dy * 0.01;
                pz = hz + dz * 0.01;
            }
            echo.finish(stats);
        }
    }

    /** Distance along the ray to where it leaves the unit cell it starts in. */
    static double cellExit(double px, double py, double pz, double dx, double dy, double dz) {
        return Math.min(axisExit(px, dx), Math.min(axisExit(py, dy), axisExit(pz, dz)));
    }

    private static double axisExit(double p, double d) {
        if (d > 0.0) return (Math.floor(p) + 1.0 - p) / d;
        if (d < 0.0) return (p - Math.floor(p)) / -d;
        return Double.POSITIVE_INFINITY;
    }

    /** SplitMix64 finaliser: deterministic per ray and bounce, so identical geometry gives identical results. */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static void connectVertex(RayOps ops, TraceSettings s, double vx, double vy, double vz,
                                      double pathLength, double energy, double energyLf, double energyHf,
                                      boolean[] found, double ax, double ay, double az, Targets targets,
                                      Accumulator acc) {
        for (int j = 0; j < targets.count; j++) {
            if (targets.skip[j]) continue;
            double dx = targets.x[j] - vx, dy = targets.y[j] - vy, dz = targets.z[j] - vz;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (s.permeation) {
                if (distance + pathLength > s.segmentLength) continue;
                double obstruction = ops.obstruction(vx, vy, vz, targets.x[j], targets.y[j], targets.z[j], s.maxBlocksPermeated);
                double transmission = Math.pow(s.permeationAbsorption, obstruction);
                double transmissionHf = s.transmissionHf(obstruction, ops.lastObstructionHf());
                // weight() already includes one transmission factor: weight ~ energy, muffle ~ amplitude.
                double weight = s.weight(pathLength, obstruction, distance) * transmission * energy;
                acc.add(j, weight, ax, ay, az, distance, transmission, transmissionHf, 1);
                acc.addEnergy(j, PERM_COUNT, 1.0);
                if (obstruction <= 0.0) {
                    seen(acc, found, j, energyLf, energyHf);
                } else {
                    acc.addEnergy(j, PERM_LF, energyLf * transmission);
                    acc.addEnergy(j, PERM_HF, energyHf * transmissionHf);
                }
            } else {
                if (distance + pathLength > targets.greenRange[j]) continue;
                if (!ops.clearPath(vx, vy, vz, targets.x[j], targets.y[j], targets.z[j], 1.0)) continue;
                double weight = s.weight(pathLength, 0.0, distance) * energy;
                acc.add(j, weight, ax, ay, az, distance, 0.0, 0.0, 1);
                seen(acc, found, j, energyLf, energyHf);
            }
        }
    }

    /** A bounce that sees the sound: the ray's energy counts towards it, once per ray. */
    private static void seen(Accumulator acc, boolean[] found, int j, double energyLf, double energyHf) {
        if (found[j]) return;
        found[j] = true;
        acc.addEnergy(j, OCC_LF, energyLf);
        acc.addEnergy(j, OCC_HF, energyHf);
    }

    /**
     * Energy loudness of one sound in one band, 0..1 (Vercidium's formula, plus walls): the sound plays at full
     * volume once {@code fullShare} of the listener's rays reach it in plain view; otherwise it also gets whatever
     * leaks through walls, straight or via a reflection, whichever is louder. Distance is left to the normal
     * falloff, so a visible sound is exactly as loud as without the mod.
     *
     * @param sums      merged accumulator sums
     * @param high      high band instead of low
     * @param rays      number of rays cast
     * @param fullShare share of rays (0..1) whose energy makes a sound fully audible
     */
    public static double energyGain(double[] sums, int j, boolean high, int rays, double fullShare) {
        int o = j * STRIDE;
        double direct = sums[o + (high ? DIRECT_HF : DIRECT_LF)];
        if (direct >= 1.0) return 1.0; // plain view
        double occluded = rays > 0 ? sums[o + (high ? OCC_HF : OCC_LF)] / (Math.max(fullShare, 1.0E-3) * rays) : 0.0;
        double count = sums[o + PERM_COUNT];
        double reflectedThrough = count > 0.0 ? sums[o + (high ? PERM_HF : PERM_LF)] / count : 0.0;
        return Math.min(1.0, occluded + Math.max(direct, reflectedThrough));
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
            double obstructionHf = ops.lastObstructionHf();
            acc.addEnergy(j, DIRECT_LF, obstruction <= 0.0 ? 1.0 : Math.pow(s.permeationAbsorption, obstruction));
            acc.addEnergy(j, DIRECT_HF, obstruction <= 0.0 ? 1.0 : s.transmissionHf(obstruction, obstructionHf));
            if (distance <= s.segmentLength) {
                double transmission = Math.pow(s.permeationAbsorption, obstruction);
                double transmissionHf = s.transmissionHf(obstruction, obstructionHf);
                double weight = s.weight(0.0, obstruction, distance) * transmission;
                acc.add(j, weight * rayCount, ux, uy, uz, distance, transmission, transmissionHf, rayCount);
            }
            return obstruction;
        }
        boolean visible = ops.clearPath(ex, ey, ez, tx, ty, tz, 1.0);
        acc.addEnergy(j, DIRECT_LF, visible ? 1.0 : 0.0);
        acc.addEnergy(j, DIRECT_HF, visible ? 1.0 : 0.0);
        if (distance <= targets.greenRange[j] && visible) {
            double weight = s.weight(0.0, 0.0, distance);
            acc.add(j, weight * rayCount, ux, uy, uz, distance, 0.0, 0.0, rayCount);
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
