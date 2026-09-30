package com.redsmods.sound_physics_perfected.acoustics;

import com.redsmods.sound_physics_perfected.config.RedsAttenuationType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Energy loudness (Vercidium's share of rays, plus walls) on synthetic scenes. */
class EnergyLoudnessTest {
    private static final int RAYS = 200;
    private static final double SHARE = 0.1;

    private static double[] gains(GridWorld world, boolean permeation, double[] eye, double[] sound) {
        TraceSettings s = new TraceSettings(3, 64.0, permeation, true, 0.4, 3.0, 0.9, RedsAttenuationType.ENERGY);
        ListenerTrace.Targets targets = new ListenerTrace.Targets(1);
        targets.set(0, sound[0], sound[1], sound[2], 16.0);
        ListenerTrace.Accumulator acc = new ListenerTrace.Accumulator(1);
        VoxelRayOps ops = new VoxelRayOps(world);
        ListenerTrace.traceDirect(ops, s, RAYS, eye[0], eye[1], eye[2], targets, 0, acc);
        ListenerTrace.traceRays(ops, s, eye[0], eye[1], eye[2], ListenerTrace.fibonacciSphere(RAYS), 0, RAYS, targets, acc);
        return new double[]{
                ListenerTrace.energyGain(acc.sums, 0, false, RAYS, SHARE),
                ListenerTrace.energyGain(acc.sums, 0, true, RAYS, SHARE)};
    }

    /** Two 9x5x9 rooms side by side, sharing the wall at x = 9. */
    private static GridWorld twoRooms(AcousticMaterial material, boolean door) {
        GridWorld world = new GridWorld()
                .room(0, 0, 0, 8, 4, 8, material)
                .room(10, 0, 0, 18, 4, 8, material);
        if (door) {
            for (int y = 0; y <= 1; y++)
                for (int z = 7; z <= 8; z++)
                    world.clear(9, y, z);
        }
        return world;
    }

    private static final double[] EYE = {4.5, 1.62, 4.5};
    private static final double[] NEXT_ROOM = {14.5, 1.5, 3.5};

    @Test
    void soundInPlainViewIsAtFullVolume() {
        GridWorld world = twoRooms(AcousticMaterial.STONE, false);
        double[] g = gains(world, true, EYE, new double[]{7.5, 1.5, 2.5});
        assertEquals(1.0, g[0], 1e-9);
        assertEquals(1.0, g[1], 1e-9);
    }

    @Test
    void soundBehindAPillarInTheSameRoomIsNearlyFull() {
        GridWorld world = new GridWorld().room(0, 0, 0, 12, 4, 12, AcousticMaterial.STONE)
                .fill(6, 0, 5, 7, 4, 7, AcousticMaterial.STONE);
        double[] g = gains(world, true, new double[]{3.5, 1.62, 6.5}, new double[]{10.5, 1.5, 6.5});
        assertTrue(g[0] > 0.9, "pillar LF " + g[0]);
    }

    @Test
    void wallsDoorwaysAndOpenings() {
        double[] sealed = gains(twoRooms(AcousticMaterial.STONE, false), true, EYE, NEXT_ROOM);
        double[] door = gains(twoRooms(AcousticMaterial.STONE, true), true, EYE, NEXT_ROOM);
        System.out.printf("sealed %.3f/%.3f door %.3f/%.3f%n", sealed[0], sealed[1], door[0], door[1]);

        // A sealed stone wall: what leaks through it (0.4 per block for bass, less for treble).
        assertEquals(0.4, sealed[0], 0.05);
        assertTrue(sealed[1] < sealed[0], "walls take the treble first");
        // A doorway lets energy round the corner: louder than the wall alone, but not in plain view.
        assertTrue(door[0] > sealed[0] + 0.1, "door " + door[0] + " vs sealed " + sealed[0]);
        assertTrue(door[0] < 1.0, "door " + door[0]);
    }

    @Test
    void lineOfSightModeHearsOnlyThroughOpenings() {
        double[] sealed = gains(twoRooms(AcousticMaterial.STONE, false), false, EYE, NEXT_ROOM);
        double[] door = gains(twoRooms(AcousticMaterial.STONE, true), false, EYE, NEXT_ROOM);
        assertEquals(0.0, sealed[0], 1e-9);
        assertTrue(door[0] > 0.05 && door[0] < 1.0, "door " + door[0]);
    }

    @Test
    void softRoomsPassLessEnergyAndLessTreble() {
        double[] stone = gains(twoRooms(AcousticMaterial.STONE, true), false, EYE, NEXT_ROOM);
        double[] cloth = gains(twoRooms(AcousticMaterial.CLOTH, true), false, EYE, NEXT_ROOM);
        System.out.printf("door stone %.3f/%.3f cloth %.3f/%.3f%n", stone[0], stone[1], cloth[0], cloth[1]);
        assertTrue(cloth[0] < stone[0], "cloth " + cloth[0] + " vs stone " + stone[0]);
        assertTrue(cloth[1] < cloth[0], "cloth treble " + cloth[1] + " vs bass " + cloth[0]);
    }

    @Test
    void replayingARecordedTrailGivesTheSameResult() {
        GridWorld world = twoRooms(AcousticMaterial.STONE, true);
        for (boolean permeation : new boolean[]{true, false}) {
            TraceSettings s = new TraceSettings(3, 8, 64.0, permeation, true, 0.4, 3.0, 0.9, RedsAttenuationType.ENERGY);
            double[] dirs = ListenerTrace.fibonacciSphere(RAYS);
            ListenerTrace.Targets targets = new ListenerTrace.Targets(3);
            targets.set(0, NEXT_ROOM[0], NEXT_ROOM[1], NEXT_ROOM[2], 16.0);
            targets.set(1, 7.5, 1.5, 2.5, 16.0);
            targets.set(2, 16.5, 3.5, 7.5, 16.0);

            ListenerTrace.Accumulator traced = new ListenerTrace.Accumulator(3);
            ListenerTrace.Trail[] trails = {new ListenerTrace.Trail(), new ListenerTrace.Trail()};
            VoxelRayOps ops = new VoxelRayOps(world);
            ListenerTrace.traceRays(ops, s, EYE[0], EYE[1], EYE[2], dirs, 0, 90, targets, traced, trails[0]);
            ListenerTrace.traceRays(ops, s, EYE[0], EYE[1], EYE[2], dirs, 90, RAYS, targets, traced, trails[1]);

            ListenerTrace.Accumulator replayed = new ListenerTrace.Accumulator(3);
            for (ListenerTrace.Trail trail : trails) ListenerTrace.connectTrail(ops, s, trail, targets, replayed);
            assertArrayEquals(traced.sums, replayed.sums, 1e-9, "permeation " + permeation);
            assertArrayEquals(traced.counts, replayed.counts);

            // A sound marked as already known is left alone.
            targets.skip[1] = true;
            ListenerTrace.Accumulator partial = new ListenerTrace.Accumulator(3);
            for (ListenerTrace.Trail trail : trails) ListenerTrace.connectTrail(ops, s, trail, targets, partial);
            assertEquals(0, partial.counts[1]);
            assertEquals(traced.sums[ListenerTrace.OCC_LF], partial.sums[ListenerTrace.OCC_LF], 1e-9);
        }
    }

    /** A stone basin with water from y = 0 to 2 (surface at y = 3), air above. */
    private static GridWorld pool() {
        GridWorld world = new GridWorld().room(0, 0, 0, 10, 7, 10, AcousticMaterial.STONE);
        world.fill(0, 0, 0, 10, 2, 10, AcousticMaterial.WATER);
        return world;
    }

    @Test
    void soundCrossesTheWaterSurfaceOnlyAtACost() {
        double[] reference = gains(pool(), true, new double[]{2.5, 5.0, 5.5}, new double[]{7.5, 4.0, 5.5});
        double[] fromAbove = gains(pool(), true, new double[]{2.5, 5.0, 5.5}, new double[]{7.5, 1.5, 5.5});
        double[] fromBelow = gains(pool(), true, new double[]{2.5, 1.5, 5.5}, new double[]{7.5, 5.0, 5.5});
        System.out.printf("pool reference %.3f above %.3f/%.3f below %.3f/%.3f%n", reference[0], fromAbove[0],
                fromAbove[1], fromBelow[0], fromBelow[1]);
        assertEquals(1.0, reference[0], 1e-9);
        // Bounces off the surface are on the listener's side of it: every path crosses once (0.4^2.5 ~ 0.1).
        assertTrue(fromAbove[0] < 0.2, "listener above, sound under water " + fromAbove[0]);
        assertTrue(fromBelow[0] < 0.2, "listener under water, sound above " + fromBelow[0]);
        assertTrue(fromAbove[1] < fromAbove[0] && fromBelow[1] < fromBelow[0], "the surface takes the treble first");
    }

    @Test
    void reverbUnderTheWaterSurfaceIsNotInViewFromAbove() {
        TraceSettings s = new TraceSettings(3, 64.0, true, true, 0.4, 3.0, 0.9, RedsAttenuationType.ENERGY);
        double[] dirs = ListenerTrace.fibonacciSphere(32);
        ListenerTrace.SourceEnvironment under = new ListenerTrace.SourceEnvironment();
        ListenerTrace.traceSource(new VoxelRayOps(pool()), s, 7.5, 1.5, 5.5, 2.5, 5.0, 5.5, dirs, 5, under);
        ListenerTrace.SourceEnvironment above = new ListenerTrace.SourceEnvironment();
        ListenerTrace.traceSource(new VoxelRayOps(pool()), s, 7.5, 5.0, 5.5, 2.5, 5.0, 5.5, dirs, 5, above);
        assertEquals(0.0, under.visibleFraction(), 1e-9);
        assertTrue(above.visibleFraction() > 0.3, "above " + above.visibleFraction());
    }

    private static double[] sealedWall(double muffle) {
        TraceSettings s = new TraceSettings(3, 3, 64.0, true, true, 0.4, 3.0, 0.9, RedsAttenuationType.ENERGY, muffle);
        ListenerTrace.Targets targets = new ListenerTrace.Targets(1);
        targets.set(0, NEXT_ROOM[0], NEXT_ROOM[1], NEXT_ROOM[2], 16.0);
        ListenerTrace.Accumulator acc = new ListenerTrace.Accumulator(1);
        VoxelRayOps ops = new VoxelRayOps(twoRooms(AcousticMaterial.STONE, false));
        ListenerTrace.traceDirect(ops, s, RAYS, EYE[0], EYE[1], EYE[2], targets, 0, acc);
        ListenerTrace.traceRays(ops, s, EYE[0], EYE[1], EYE[2], ListenerTrace.fibonacciSphere(RAYS), 0, RAYS, targets, acc);
        return new double[]{ListenerTrace.energyGain(acc.sums, 0, false, RAYS, SHARE),
                ListenerTrace.energyGain(acc.sums, 0, true, RAYS, SHARE)};
    }

    @Test
    void mufflingSetsHowMuchMoreTrebleThanBassAWallStops() {
        double[] none = sealedWall(0.0), realistic = sealedWall(2.5);
        double relative = 20 * Math.log10(realistic[1] / realistic[0]);
        assertEquals(none[0], none[1], 1e-9, "0%: treble like bass");
        assertEquals(none[0], realistic[0], 1e-9, "the bass does not change");
        assertTrue(relative < -18 && relative > -26, "stone wall treble vs bass " + relative + " dB");
    }

    @Test
    void aSoundInsideItsOwnBlockStillHearsItsRoom() {
        // A note block in the middle of a stone room: its rays start where they leave the block.
        GridWorld world = new GridWorld().room(0, 0, 0, 8, 4, 8, AcousticMaterial.STONE).set(4, 0, 4, AcousticMaterial.WOOD);
        TraceSettings s = new TraceSettings(3, 64.0, true, true, 0.4, 3.0, 0.9, RedsAttenuationType.ENERGY);
        ListenerTrace.SourceEnvironment env = new ListenerTrace.SourceEnvironment();
        ListenerTrace.traceSource(new VoxelRayOps(world), s, 4.5, 0.5, 4.5, 1.5, 1.62, 1.5,
                ListenerTrace.fibonacciSphere(32), 5, env);
        assertTrue(env.visibleFraction() > 0.3, "visible " + env.visibleFraction());
        assertTrue(env.stats.firstDistanceSum / env.stats.firstHits > 0.5, "rays reach the walls, not the block itself");
    }

    @Test
    void runningOutOfDistanceIsNotAnEscape() {
        // One ray down a sealed corridor (x 0..37): 35 blocks to the far wall leaves 5 of the 40-block connecting
        // budget, too short to reach the other end. That is running out of distance, not escaping: the ray carries on
        // for the room estimate and hits the wall behind the listener.
        AcousticMaterial mirror = new AcousticMaterial("mirror", 0.01f, 0.01f, 0.01f, 0.0f, 2.0f); // no scattering
        GridWorld corridor = new GridWorld().room(0, 0, 0, 37, 2, 2, mirror);
        TraceSettings s = new TraceSettings(1, 3, 40.0, true, true, 0.4, 3.0, 0.9, RedsAttenuationType.ENERGY);
        ListenerTrace.Accumulator acc = new ListenerTrace.Accumulator(0);
        ListenerTrace.traceRays(new VoxelRayOps(corridor), s, 35.0, 1.5, 1.5, new double[]{-1, 0, 0}, 0, 1,
                new ListenerTrace.Targets(0), acc);
        assertEquals(0, acc.stats.firstEscapes + acc.stats.laterEscapes, "escapes from a sealed corridor");
        assertTrue(acc.stats.laterHits >= 2, "the ray went on bouncing: " + acc.stats.laterHits);
        assertEquals(1, acc.stats.legacyEscapes, "the legacy modes still count it as they always did");
    }
}
