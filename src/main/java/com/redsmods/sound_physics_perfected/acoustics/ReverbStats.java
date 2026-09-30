package com.redsmods.sound_physics_perfected.acoustics;

/**
 * Geometry and material statistics gathered from the listener's rays. One instance per worker chunk,
 * merged once the trace finishes, then treated as immutable.
 */
public final class ReverbStats {
    /** First-segment hits further than this produce distinct echoes (≥ ~0.1 s round trip). */
    public static final double ECHO_DISTANCE = 17.0;

    public int rays;
    public double segmentLength;

    // First segment (listener -> first surface)
    public int firstHits;
    public int firstEscapes;
    public double firstDistanceSum;
    public double firstInverseDistanceSum;
    public double firstReflectivitySum;
    /** Sum of r^2/cos over first hits: visible surface area is (4π/N) times this. */
    public double firstAreaSum;

    // Later segments (surface -> surface)
    public int laterHits;
    public int laterEscapes;
    public double laterDistanceSum;

    // Absorption over every hit
    public double absorptionLowSum;
    public double absorptionMidSum;
    public double absorptionHighSum;

    // Distant first reflections (echoes)
    public int farHits;
    public double farDistanceSum;
    public double farReflectivitySum;

    // Where reflections come from (world space, weighted sums of unit vectors from the listener)
    public double earlyX, earlyY, earlyZ, earlyWeight;
    public double lateX, lateY, lateZ, lateWeight;

    // Quantities used by the legacy (1.0.0 / 1.4.0) reverb modes, with their original definitions.
    public double legacyWallDistanceSum;
    public double legacyWallDistanceCount;
    public int legacyVisible;
    public int legacyBlueRays;
    public int legacyEscapes;
    public int legacyLeakDenominator;

    /** Echogram bins: 1 block of travel each. */
    public static final int ECHO_BINS = 256;
    /**
     * Echogram: per band, the energy left in the rays after each block of travel, summed over the rays whose
     * energy is known there ({@link #echoRays}). Allocated on first use.
     */
    double[] echoLow, echoMid, echoHigh, echoRays;

    public void hit(int bounce, double distance, AcousticMaterial material, double cosIncidence) {
        hit(bounce, distance, material, cosIncidence, true);
    }

    /** @param legacy whether this segment also counts for the legacy reverb modes (original bounce range only) */
    public void hit(int bounce, double distance, AcousticMaterial material, double cosIncidence, boolean legacy) {
        if (bounce == 0) {
            firstHits++;
            firstDistanceSum += distance;
            firstInverseDistanceSum += 1.0 / Math.max(distance, 1.0);
            firstReflectivitySum += material.reflectivity();
            firstAreaSum += distance * distance / Math.max(cosIncidence, 0.2);
            if (distance >= ECHO_DISTANCE) {
                farHits++;
                farDistanceSum += distance;
                farReflectivitySum += material.reflectivity();
            }
        } else {
            laterHits++;
            laterDistanceSum += distance;
        }
        absorptionLowSum += material.absorptionLow;
        absorptionMidSum += material.absorptionMid;
        absorptionHighSum += material.absorptionHigh;
        if (legacy) legacyLeakDenominator++;
    }

    public void escape(int bounce) {
        escape(bounce, true);
    }

    public void escape(int bounce, boolean legacy) {
        if (bounce == 0) {
            firstEscapes++;
        } else {
            laterEscapes++;
        }
        if (legacy) {
            legacyEscapes++;
            legacyLeakDenominator++;
        }
    }

    /** A first reflection in direction (x,y,z) from the listener; nearer, harder surfaces weigh more. */
    public void early(double x, double y, double z, double weight) {
        earlyX += x * weight;
        earlyY += y * weight;
        earlyZ += z * weight;
        earlyWeight += weight;
    }

    /** Energy scattered back to the listener from a later reflection in direction (x,y,z). */
    public void late(double x, double y, double z, double weight) {
        lateX += x * weight;
        lateY += y * weight;
        lateZ += z * weight;
        lateWeight += weight;
    }

    public void blueRay(boolean visible, double legacyDistance) {
        legacyBlueRays++;
        if (visible) {
            legacyVisible++;
            legacyWallDistanceSum += legacyDistance;
            legacyWallDistanceCount += 1.0;
        }
    }

    /**
     * Adds one ray to the echogram.
     *
     * @param hits     reflections the ray made
     * @param hitL     distance travelled at each reflection
     * @param low      energy left after each reflection (surfaces only), per band
     * @param escaped  whether the ray then left into open air: its energy is gone from {@code escapeL} on, for
     *                 good; otherwise it was stopped by the bounce limit and is unknown after its last reflection
     */
    public void echogramRay(int hits, double[] hitL, double[] low, double[] mid, double[] high, boolean escaped,
                            double escapeL) {
        if (echoRays == null) {
            echoLow = new double[ECHO_BINS];
            echoMid = new double[ECHO_BINS];
            echoHigh = new double[ECHO_BINS];
            echoRays = new double[ECHO_BINS];
        }
        int end = escaped ? ECHO_BINS : Math.min(ECHO_BINS, hits > 0 ? (int) Math.ceil(hitL[hits - 1]) : 0);
        int i = 0;
        for (int b = 0; b < end; b++) {
            double l = b + 0.5;
            echoRays[b] += 1.0;
            if (escaped && l >= escapeL) continue; // left: no energy, but known
            while (i < hits && hitL[i] <= l) i++;
            if (i == 0) {
                echoLow[b] += 1.0;
                echoMid[b] += 1.0;
                echoHigh[b] += 1.0;
            } else {
                echoLow[b] += low[i - 1];
                echoMid[b] += mid[i - 1];
                echoHigh[b] += high[i - 1];
            }
        }
    }

    /**
     * Energy decay rate (nepers per block of travel) from the echogram's slope, for band 0/1/2 (low/mid/high), or
     * NaN when there is too little of it to fit. Unlike Eyring's average absorption this sees that energy
     * lingers where rays keep missing the soft or open parts of a space, and leaves fast where they escape.
     * All bands are fitted over the same stretch, chosen on the mid band, so they stay comparable.
     */
    public double echogramDecayRate(int band) {
        int[] window = echogramWindow();
        if (window == null) return Double.NaN;
        double[] e = band == 0 ? echoLow : (band == 1 ? echoMid : echoHigh);

        // Least squares of ln(energy) against distance, weighted by how many rays each bin knows about.
        double sw = 0, sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int b = window[0]; b < window[1]; b++) {
            if (e[b] <= 0.0) return Double.NaN;
            double w = echoRays[b], x = b + 0.5, y = Math.log(e[b] / echoRays[b]);
            sw += w;
            sx += w * x;
            sy += w * y;
            sxx += w * x * x;
            sxy += w * x * y;
        }
        double denominator = sw * sxx - sx * sx;
        if (denominator <= 1.0E-9) return Double.NaN;
        double slope = (sw * sxy - sx * sy) / denominator;
        return slope < 0.0 ? -slope : Double.NaN;
    }

    /** Bins [start, end) of the echogram to fit, or null. */
    private int[] echogramWindow() {
        if (echoRays == null || echoRays[0] < 8.0) return null;
        double n0 = echoRays[0];
        double e0 = echoMid[0] / n0;
        if (e0 <= 0.0) return null;

        // Skip the direct part (before most rays have reflected). Stop before the bounce limit starts cutting rays
        // off: only rays still going get cut, so past that point the escaped (silent) ones would be over-represented.
        int start = firstHits > 0 ? (int) Math.ceil(1.5 * firstDistanceSum / firstHits) : 0;
        int end = start;
        while (end < ECHO_BINS && echoRays[end] >= 0.9 * n0 && echoMid[end] > 0.0
                && echoMid[end] / echoRays[end] >= e0 * 3.2e-4) { // down to -35 dB
            end++;
        }
        // Skip the first 5 dB too when the fall is that deep (as T20/T30 do).
        for (int b = start; b < end - 4; b++) {
            if (echoMid[b] / echoRays[b] <= e0 * 0.316) {
                start = b;
                break;
            }
        }
        return end - start < 4 ? null : new int[]{start, end};
    }

    public int totalHits() {
        return firstHits + laterHits;
    }

    public void merge(ReverbStats o) {
        rays += o.rays;
        segmentLength = Math.max(segmentLength, o.segmentLength);
        firstHits += o.firstHits;
        firstEscapes += o.firstEscapes;
        firstDistanceSum += o.firstDistanceSum;
        firstInverseDistanceSum += o.firstInverseDistanceSum;
        firstReflectivitySum += o.firstReflectivitySum;
        firstAreaSum += o.firstAreaSum;
        laterHits += o.laterHits;
        laterEscapes += o.laterEscapes;
        laterDistanceSum += o.laterDistanceSum;
        absorptionLowSum += o.absorptionLowSum;
        absorptionMidSum += o.absorptionMidSum;
        absorptionHighSum += o.absorptionHighSum;
        farHits += o.farHits;
        farDistanceSum += o.farDistanceSum;
        farReflectivitySum += o.farReflectivitySum;
        earlyX += o.earlyX;
        earlyY += o.earlyY;
        earlyZ += o.earlyZ;
        earlyWeight += o.earlyWeight;
        lateX += o.lateX;
        lateY += o.lateY;
        lateZ += o.lateZ;
        lateWeight += o.lateWeight;
        legacyWallDistanceSum += o.legacyWallDistanceSum;
        legacyWallDistanceCount += o.legacyWallDistanceCount;
        legacyVisible += o.legacyVisible;
        legacyBlueRays += o.legacyBlueRays;
        legacyEscapes += o.legacyEscapes;
        legacyLeakDenominator += o.legacyLeakDenominator;
        if (o.echoRays != null) {
            if (echoRays == null) {
                echoLow = o.echoLow.clone();
                echoMid = o.echoMid.clone();
                echoHigh = o.echoHigh.clone();
                echoRays = o.echoRays.clone();
            } else {
                for (int b = 0; b < ECHO_BINS; b++) {
                    echoLow[b] += o.echoLow[b];
                    echoMid[b] += o.echoMid[b];
                    echoHigh[b] += o.echoHigh[b];
                    echoRays[b] += o.echoRays[b];
                }
            }
        }
    }
}
