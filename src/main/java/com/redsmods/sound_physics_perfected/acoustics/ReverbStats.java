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

    // Quantities used by the legacy (1.0.0 / 1.4.0) reverb modes, with their original definitions.
    public double legacyWallDistanceSum;
    public double legacyWallDistanceCount;
    public int legacyVisible;
    public int legacyBlueRays;
    public int legacyEscapes;
    public int legacyLeakDenominator;

    public void hit(int bounce, double distance, AcousticMaterial material, double cosIncidence) {
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
        legacyLeakDenominator++;
    }

    public void escape(int bounce) {
        if (bounce == 0) {
            firstEscapes++;
        } else {
            laterEscapes++;
        }
        legacyEscapes++;
        legacyLeakDenominator++;
    }

    public void blueRay(boolean visible, double legacyDistance) {
        legacyBlueRays++;
        if (visible) {
            legacyVisible++;
            legacyWallDistanceSum += legacyDistance;
            legacyWallDistanceCount += 1.0;
        }
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
        legacyWallDistanceSum += o.legacyWallDistanceSum;
        legacyWallDistanceCount += o.legacyWallDistanceCount;
        legacyVisible += o.legacyVisible;
        legacyBlueRays += o.legacyBlueRays;
        legacyEscapes += o.legacyEscapes;
        legacyLeakDenominator += o.legacyLeakDenominator;
    }
}
