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
}
