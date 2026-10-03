package com.redsmods.sound_physics_perfected.acoustics;

import com.redsmods.sound_physics_perfected.config.RedsAttenuationType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Blocks with gaps (leaves, copper grates, bars, door windows): sound goes through the openings as often as they
 * say, bass better than treble, and a leaf canopy is no roof.
 */
class BlockGapsTest {
    // A copper grate's gaps (block_acoustics.json): bass, mid, treble.
    private static final double BASS = 0.70, MID = 0.33, TREBLE = 0.16;
    private static final int RAYS = 400;

    @Test
    void raysGetThroughAsOftenAsTheGapsSay() {
        GridWorld one = new GridWorld().fillGaps(5, -40, -40, 5, 40, 40, AcousticMaterial.METAL, BASS, MID, TREBLE);
        GridWorld two = new GridWorld().fillGaps(5, -40, -40, 6, 40, 40, AcousticMaterial.METAL, BASS, MID, TREBLE);
        int n = 20000;
        for (int layers = 1; layers <= 2; layers++) {
            VoxelRayOps ops = new VoxelRayOps(layers == 1 ? one : two);
            double through = 0, bass = 0, treble = 0, reflectedBass = 0;
            for (int i = 0; i < n; i++) {
                ops.nextRay(i * 0x9E3779B97F4A7C15L);
                if (!ops.firstHit(0.5, 0.5, 0.5, 1.0, 0.0, 0.0, 20.0)) {
                    through++;
                    bass += ops.segmentBass();
                    treble += ops.segmentTreble();
                } else {
                    // What one layer reflects: the bass it does not let through.
                    if (layers == 1) reflectedBass += ops.hitBass();
                }
            }
            assertEquals(Math.pow(MID, layers), through / n, 0.015, "rays through " + layers + " layer(s)");
            assertEquals(Math.pow(BASS, layers), bass / n, 0.02, "bass through " + layers + " layer(s)");
            assertEquals(Math.pow(TREBLE, layers), treble / n, 0.015, "treble through " + layers + " layer(s)");
            if (layers == 1) assertEquals(1.0 - BASS, reflectedBass / n, 0.02, "bass reflected");
        }
    }

    @Test
    void theSameRayChoosesTheSame() {
        GridWorld w = new GridWorld().fillGaps(5, -4, -4, 5, 4, 4, AcousticMaterial.FOLIAGE, 0.99, 0.97, 0.94);
        VoxelRayOps a = new VoxelRayOps(w), b = new VoxelRayOps(w);
        for (int i = 0; i < 500; i++) {
            a.nextRay(i);
            b.nextRay(i);
            assertEquals(a.firstHit(0.5, 0.5, 0.5, 1, 0, 0, 20), b.firstHit(0.5, 0.5, 0.5, 1, 0, 0, 20));
            // Unseeded walks choose from their geometry: the same walk, the same answer.
            assertEquals(a.clearPath(0.5, 0.5, 0.5, 9.5, 0.5, 0.5, 0.0), b.clearPath(0.5, 0.5, 0.5, 9.5, 0.5, 0.5, 0.0));
        }
    }

    private static ReverbParams reverb(GridWorld world) {
        TraceSettings s = new TraceSettings(3, 64.0, true, true, 0.4, 3.0, 0.9, RedsAttenuationType.VERCIDIUM_LINEAR);
        ListenerTrace.Targets targets = new ListenerTrace.Targets(0);
        ListenerTrace.Accumulator acc = new ListenerTrace.Accumulator(0);
        ListenerTrace.traceRays(new VoxelRayOps(world), s, 0.5, 1.62, 0.5, ListenerTrace.fibonacciSphere(RAYS), 0, RAYS,
                targets, acc);
        return RoomAcousticsModel.estimate(acc.stats, RoomAcousticsModel.Settings.defaults());
    }

    @Test
    void aLeafCanopyIsNoRoof() {
        // Standing in a clearing under a thick tree canopy: two blocks of leaves overhead.
        GridWorld leaves = new GridWorld().ground(AcousticMaterial.SOIL, 60)
                .fillGaps(-30, 4, -30, 30, 5, 30, AcousticMaterial.FOLIAGE, 0.99, 0.97, 0.94);
        GridWorld stone = new GridWorld().ground(AcousticMaterial.SOIL, 60)
                .fill(-30, 4, -30, 30, 5, 30, AcousticMaterial.STONE);
        GridWorld open = new GridWorld().ground(AcousticMaterial.SOIL, 60);
        ReverbParams under = reverb(leaves), roofed = reverb(stone), field = reverb(open);
        assertTrue(roofed.enclosure > 0.9, "a stone roof encloses: " + roofed.enclosure);
        // Rays skimming the canopy cross many leaf blocks, so some still meet one; what you hear is the decay.
        assertTrue(under.enclosure < roofed.enclosure - 0.15, "leaves " + under.enclosure + " vs stone " + roofed.enclosure);
        assertTrue(under.decayTime < field.decayTime + 0.15, "leaves " + under.decayTime + " vs open " + field.decayTime);
        assertTrue(under.decayTime < roofed.decayTime * 0.5, "leaves " + under.decayTime + " vs stone " + roofed.decayTime);
    }

    private static double[] gainDb(GridWorld world, double[] eye, double[] sound) {
        TraceSettings s = new TraceSettings(3, 3, 64.0, true, true, 0.4, 3.0, 0.9, RedsAttenuationType.ENERGY, 2.5);
        ListenerTrace.Targets t = new ListenerTrace.Targets(1);
        t.set(0, sound[0], sound[1], sound[2], 16.0);
        ListenerTrace.Accumulator acc = new ListenerTrace.Accumulator(1);
        VoxelRayOps ops = new VoxelRayOps(world);
        ListenerTrace.traceDirect(ops, s, RAYS, eye[0], eye[1], eye[2], t, 0, acc);
        ListenerTrace.traceRays(ops, s, eye[0], eye[1], eye[2], ListenerTrace.fibonacciSphere(RAYS), 0, RAYS, t, acc);
        return new double[]{20 * Math.log10(ListenerTrace.energyGain(acc.sums, 0, false, RAYS, 0.1)),
                20 * Math.log10(ListenerTrace.energyGain(acc.sums, 0, true, RAYS, 0.1))};
    }

    @Test
    void aGrateWallLetsSoundThrough() {
        // A closed stone room split by a wall: solid stone, or copper grates.
        GridWorld stone = new GridWorld().room(0, 0, 0, 12, 4, 8, AcousticMaterial.STONE).fill(6, 0, 0, 6, 4, 8, AcousticMaterial.STONE);
        GridWorld grate = new GridWorld().room(0, 0, 0, 12, 4, 8, AcousticMaterial.STONE)
                .fillGaps(6, 0, 0, 6, 4, 8, AcousticMaterial.METAL, BASS, MID, TREBLE);
        GridWorld none = new GridWorld().room(0, 0, 0, 12, 4, 8, AcousticMaterial.STONE);
        double[] eye = {2.5, 1.62, 4.5}, sound = {10.5, 1.5, 4.5};
        double[] s = gainDb(stone, eye, sound), g = gainDb(grate, eye, sound), o = gainDb(none, eye, sound);
        assertTrue(g[0] > s[0] + 2.0, "bass through grate " + g[0] + " vs stone " + s[0]);
        assertTrue(g[1] > s[1] + 2.0, "treble through grate " + g[1] + " vs stone " + s[1]);
        assertTrue(g[0] <= o[0] + 0.5 && g[1] < o[1], "a grate still muffles: " + g[1] + " vs open " + o[1]);
        // Holes smaller than bass wavelengths: the grate takes more treble than bass.
        assertTrue(o[1] - g[1] > o[0] - g[0], "treble loss " + (o[1] - g[1]) + " vs bass loss " + (o[0] - g[0]));
    }
}
