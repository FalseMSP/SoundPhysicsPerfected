package com.redsmods.sound_physics_perfected.acoustics;

import com.redsmods.sound_physics_perfected.config.RedsAttenuationType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The echogram decay fit against Eyring, and where the two should differ. */
class EchogramTest {
    private static final int RAYS = 200;

    private static ReverbStats stats(GridWorld world, double x, double y, double z) {
        TraceSettings s = new TraceSettings(3, 8, 64.0, true, true, 0.4, 3.0, 0.9, RedsAttenuationType.ENERGY);
        ListenerTrace.Accumulator acc = new ListenerTrace.Accumulator(0);
        ListenerTrace.traceRays(new VoxelRayOps(world), s, x, y, z, ListenerTrace.fibonacciSphere(RAYS), 0, RAYS,
                new ListenerTrace.Targets(0), acc);
        return acc.stats;
    }

    private static double[] echoAndEyring(GridWorld world, double x, double y, double z) {
        ReverbStats s = stats(world, x, y, z);
        double echo = RoomAcousticsModel.estimate(s, RoomAcousticsModel.Settings.defaults()).decayTime;
        s.echoRays = null; // no echogram: Eyring only
        double eyring = RoomAcousticsModel.estimate(s, RoomAcousticsModel.Settings.defaults()).decayTime;
        return new double[]{echo, eyring};
    }

    @Test
    void uniformRoomsAgreeWithEyring() {
        double[] stone = echoAndEyring(new GridWorld().room(0, 0, 0, 6, 4, 6, AcousticMaterial.STONE), 3.5, 1.62, 3.5);
        double[] cave = echoAndEyring(new GridWorld().room(0, 0, 0, 39, 19, 39, AcousticMaterial.STONE), 20, 1.62, 20);
        assertEquals(stone[1], stone[0], 0.1 * stone[1], "stone room");
        assertEquals(cave[1], cave[0], 0.1 * cave[1], "cave");
    }

    @Test
    void wallsRingLongerOverASoftFloorThanAverageAbsorptionSays() {
        GridWorld world = new GridWorld().room(0, 0, 0, 8, 4, 8, AcousticMaterial.STONE)
                .fill(0, -1, 0, 8, -1, 8, AcousticMaterial.CLOTH);
        double[] t = echoAndEyring(world, 4.5, 1.62, 4.5);
        assertTrue(t[0] > 1.1 * t[1], "echogram " + t[0] + " vs Eyring " + t[1]);
    }

    @Test
    void bandsDecayInMaterialOrder() {
        ReverbStats s = stats(new GridWorld().room(0, 0, 0, 8, 4, 8, AcousticMaterial.STONE), 4.5, 1.62, 4.5);
        double low = s.echogramDecayRate(0), mid = s.echogramDecayRate(1), high = s.echogramDecayRate(2);
        assertTrue(low < mid && mid < high, low + " " + mid + " " + high);
    }

    @Test
    void openFieldKeepsEyring() {
        double[] t = echoAndEyring(new GridWorld().ground(AcousticMaterial.SOIL, 90), 0.5, 1.62, 0.5);
        assertEquals(t[1], t[0], 1e-6);
    }

    /** The echogram as it used to be built: every ray painted bin by bin. */
    private static void paint(double[][] e, int hits, double[] hitL, double[] low, double[] mid, double[] high,
                              boolean escaped, double escapeL) {
        int end = escaped ? ReverbStats.ECHO_BINS : Math.min(ReverbStats.ECHO_BINS, hits > 0 ? (int) Math.ceil(hitL[hits - 1]) : 0);
        int i = 0;
        for (int b = 0; b < end; b++) {
            double l = b + 0.5;
            e[3][b] += 1.0;
            if (escaped && l >= escapeL) continue;
            while (i < hits && hitL[i] <= l) i++;
            e[0][b] += i == 0 ? 1.0 : low[i - 1];
            e[1][b] += i == 0 ? 1.0 : mid[i - 1];
            e[2][b] += i == 0 ? 1.0 : high[i - 1];
        }
    }

    @Test
    void differencesMatchPaintingEveryBin() {
        java.util.Random random = new java.util.Random(1);
        ReverbStats s = new ReverbStats();
        double[][] expected = new double[4][ReverbStats.ECHO_BINS];
        for (int ray = 0; ray < 3000; ray++) {
            int hits = random.nextInt(26);
            double[] hitL = new double[hits], low = new double[hits], mid = new double[hits], high = new double[hits];
            double travelled = 0.0, l = 1.0, m = 1.0, h = 1.0;
            for (int k = 0; k < hits; k++) {
                // Some reflections land exactly on bin centres and edges, some several in one bin, some past the end.
                int kind = random.nextInt(4);
                travelled += kind == 0 ? 0.0 : kind == 1 ? 0.5 : random.nextDouble() * (ray % 3 == 0 ? 30.0 : 6.0);
                if (kind == 1) travelled = Math.floor(travelled) + 0.5;
                hitL[k] = travelled;
                low[k] = l *= 0.5 + 0.5 * random.nextDouble();
                mid[k] = m *= 0.5 + 0.5 * random.nextDouble();
                high[k] = h *= 0.5 + 0.5 * random.nextDouble();
            }
            boolean escaped = random.nextBoolean();
            double escapeL = random.nextInt(8) == 0 ? Math.floor(travelled) + 0.5
                    : travelled + (hits > 0 ? travelled / hits : 0.0);
            s.echogramRay(hits, hitL, low, mid, high, escaped, escapeL);
            paint(expected, hits, hitL, low, mid, high, escaped, escapeL);
        }
        for (int band = 0; band < 4; band++) {
            for (int b = 0; b < ReverbStats.ECHO_BINS; b++) {
                assertEquals(expected[band][b], s.echogram(band, b), band == 3 ? 0.0 : 1e-9 * Math.max(1.0, expected[band][b]),
                        "band " + band + " bin " + b);
            }
        }
    }

    @Test
    void tooFewRaysFallBackToEyring() {
        ReverbStats s = new ReverbStats();
        assertTrue(Double.isNaN(s.echogramDecayRate(1)));
        assertEquals(1.5, RoomAcousticsModel.decay(Double.NaN, 0.001, 343.0, 1.0, 1.5), 1e-12);
    }
}
