package com.redsmods.sound_physics_perfected.acoustics;

import com.redsmods.sound_physics_perfected.config.RedsAttenuationType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Whole-kernel checks on synthetic scenes: the listener tracer plus the room model must give each kind of
 * space a plausible, clearly distinct reverb, and must localise sounds sensibly. Ranges are wide on purpose;
 * they catch regressions (inverted materials, parameters stuck at one value...), not fine tuning.
 */
class RoomAcousticsTest {
    private static final int RAYS = 200;
    private static final double EYE = 1.62;

    private static TraceSettings settings() {
        return new TraceSettings(3, 64.0, true, true, 0.4, 3.0, 0.9, RedsAttenuationType.VERCIDIUM_LINEAR);
    }

    private static ListenerTrace.Accumulator trace(GridWorld world, double x, double y, double z, double[]... sounds) {
        ListenerTrace.Targets targets = new ListenerTrace.Targets(sounds.length);
        for (int i = 0; i < sounds.length; i++) targets.set(i, sounds[i][0], sounds[i][1], sounds[i][2], 16.0);
        ListenerTrace.Accumulator acc = new ListenerTrace.Accumulator(sounds.length);
        VoxelRayOps ops = new VoxelRayOps(world);
        for (int j = 0; j < sounds.length; j++) {
            ListenerTrace.traceDirect(ops, settings(), RAYS, x, y, z, targets, j, acc);
        }
        ListenerTrace.traceRays(ops, settings(), x, y, z, ListenerTrace.fibonacciSphere(RAYS), 0, RAYS, targets, acc);
        return acc;
    }

    private static ReverbParams reverb(GridWorld world, double x, double y, double z) {
        return RoomAcousticsModel.estimate(trace(world, x, y, z).stats, RoomAcousticsModel.Settings.defaults());
    }

    @Test
    void stoneRoomRingsLikeAStoneRoom() {
        ReverbParams p = reverb(new GridWorld().room(0, 0, 0, 4, 2, 4, AcousticMaterial.STONE), 2.5, EYE, 2.5);
        assertTrue(p.decayTime > 1.2 && p.decayTime < 2.5, "decay " + p.decayTime);
        assertTrue(p.lateReverbGain > 0.8, "late " + p.lateReverbGain);
        assertTrue(p.enclosure > 0.99);
        assertTrue(p.reflectionsDelay < 0.03);
    }

    @Test
    void woolAbsorbsWhereStoneReflects() {
        ReverbParams wool = reverb(new GridWorld().room(0, 0, 0, 4, 2, 4, AcousticMaterial.CLOTH), 2.5, EYE, 2.5);
        ReverbParams stone = reverb(new GridWorld().room(0, 0, 0, 4, 2, 4, AcousticMaterial.STONE), 2.5, EYE, 2.5);
        assertTrue(wool.decayTime < 0.3, "wool decay " + wool.decayTime);
        assertTrue(wool.gainHF < stone.gainHF, "wool should be duller than stone");
    }

    @Test
    void biggerSpacesDecayLonger() {
        ReverbParams room = reverb(new GridWorld().room(0, 0, 0, 4, 2, 4, AcousticMaterial.STONE), 2.5, EYE, 2.5);
        ReverbParams cave = reverb(new GridWorld().room(0, 0, 0, 39, 19, 39, AcousticMaterial.STONE), 20, EYE, 20);
        assertTrue(cave.decayTime > room.decayTime * 1.8, room.decayTime + " vs " + cave.decayTime);
        assertTrue(cave.lateReverbDelay > room.lateReverbDelay);
        assertTrue(cave.decayTime <= 5.0 + 1e-3, "soft limit keeps caves pleasant: " + cave.decayTime);
    }

    @Test
    void openFieldHasAlmostNoReverb() {
        ReverbParams p = reverb(new GridWorld().ground(AcousticMaterial.SOIL, 90), 0.5, EYE, 0.5);
        assertTrue(p.decayTime < 0.5, "decay " + p.decayTime);
        assertTrue(p.lateReverbGain < 0.3, "late " + p.lateReverbGain);
        assertTrue(p.enclosure < 0.6);
    }

    @Test
    void canyonWallsGiveDistinctEchoes() {
        GridWorld canyon = new GridWorld().ground(AcousticMaterial.STONE, 90)
                .fill(-40, 0, -90, -39, 40, 90, AcousticMaterial.STONE)
                .fill(39, 0, -90, 40, 40, 90, AcousticMaterial.STONE);
        ReverbParams p = reverb(canyon, 0.5, EYE, 0.5);
        assertTrue(p.echoDepth > 0.3, "echo " + p.echoDepth);
        assertTrue(p.echoTime > 0.2, "echo time " + p.echoTime);
    }

    @Test
    void lfDecayRatioNeverExceedsOne() {
        // OpenAL Soft renders LF ratios above 1 with a large overshoot; stone would otherwise ask for ~1.6.
        for (AcousticMaterial m : new AcousticMaterial[]{AcousticMaterial.STONE, AcousticMaterial.CLOTH, AcousticMaterial.WOOD}) {
            ReverbParams p = reverb(new GridWorld().room(0, 0, 0, 4, 2, 4, m), 2.5, EYE, 2.5);
            assertTrue(p.decayLFRatio <= 1.0f, m + ": " + p.decayLFRatio);
        }
    }

    private static double angleToSource(ListenerTrace.Accumulator acc, double ex, double ey, double ez, double[] s) {
        double w = acc.sums[ListenerTrace.WEIGHT];
        double dx = acc.sums[ListenerTrace.DIR_X] / w, dy = acc.sums[ListenerTrace.DIR_Y] / w, dz = acc.sums[ListenerTrace.DIR_Z] / w;
        double tx = s[0] - ex, ty = s[1] - ey, tz = s[2] - ez;
        double cos = (dx * tx + dy * ty + dz * tz) / Math.sqrt((dx * dx + dy * dy + dz * dz) * (tx * tx + ty * ty + tz * tz));
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
    }

    @Test
    void occludedOutdoorSoundComesFromItsDirection() {
        // Behind a free-standing wall on open ground: the old tracer placed this in an arbitrary direction.
        GridWorld world = new GridWorld().ground(AcousticMaterial.SOIL, 90).fill(-3, 0, 4, 3, 3, 4, AcousticMaterial.STONE);
        double[] sound = {0.5, 1.0, 8.5};
        ListenerTrace.Accumulator acc = trace(world, 0.5, EYE, 0.5, sound);
        assertTrue(angleToSource(acc, 0.5, EYE, 0.5, sound) < 15);
        assertTrue(acc.sums[ListenerTrace.MUFFLE] / acc.sums[ListenerTrace.WEIGHT] < 0.9, "should be muffled");
    }

    @Test
    void visibleSoundIsUnmuffledAndOnTarget() {
        GridWorld world = new GridWorld().room(0, 0, 0, 7, 3, 7, AcousticMaterial.STONE);
        double[] sound = {3.5, 1.0, 1.5};
        ListenerTrace.Accumulator acc = trace(world, 4.5, EYE, 5.5, sound);
        assertTrue(angleToSource(acc, 4.5, EYE, 5.5, sound) < 10);
        assertEquals(1.0, acc.sums[ListenerTrace.MUFFLE] / acc.sums[ListenerTrace.WEIGHT], 0.05);
    }

    private static TraceSettings reverbSettings() {
        return new TraceSettings(3, 8, 64.0, true, true, 0.4, 3.0, 0.9, RedsAttenuationType.VERCIDIUM_LINEAR);
    }

    private static ReverbParams reverb8(GridWorld world, double x, double y, double z) {
        ListenerTrace.Targets none = new ListenerTrace.Targets(0);
        ListenerTrace.Accumulator acc = new ListenerTrace.Accumulator(0);
        ListenerTrace.traceRays(new VoxelRayOps(world), reverbSettings(), x, y, z, ListenerTrace.fibonacciSphere(RAYS), 0, RAYS, none, acc);
        return RoomAcousticsModel.estimate(acc.stats, RoomAcousticsModel.Settings.defaults());
    }

    @Test
    void reverbComesFromInsideTheCaveWhenStandingAtItsMouth() {
        GridWorld world = new GridWorld().room(0, 0, 0, 29, 9, 29, AcousticMaterial.STONE)
                .fill(-20, -1, -20, 50, -1, -1, AcousticMaterial.SOIL);
        for (int x = 13; x <= 16; x++) for (int y = 0; y <= 4; y++) world.clear(x, y, -1);
        ReverbParams p = reverb8(world, 14.5, EYE, -4.5);
        assertTrue(p.lateReverbPanZ > 0.4, "late reverb should point into the cave (+z): " + p.lateReverbPanZ);
        assertTrue(p.lateReverbPanZ <= 0.5 + 1e-6, "never fully one-sided: " + p.lateReverbPanZ);
        assertEquals(0.0, p.lateReverbPanY, 1e-6, "panning is horizontal");
    }

    @Test
    void reflectionsLeanTowardsALoneWallButRoomsStayDiffuse() {
        GridWorld wall = new GridWorld().ground(AcousticMaterial.SOIL, 90).fill(3, 0, -40, 3, 6, 40, AcousticMaterial.STONE);
        ReverbParams outside = reverb8(wall, 0.5, EYE, 0.5);
        assertTrue(outside.reflectionsPanX > 0.07, "reflections should lean east: " + outside.reflectionsPanX);
        ReverbParams room = reverb8(new GridWorld().room(0, 0, 0, 4, 2, 4, AcousticMaterial.STONE), 2.5, EYE, 2.5);
        float lean = Math.abs(room.reflectionsPanX) + Math.abs(room.reflectionsPanZ) + Math.abs(room.lateReverbPanX) + Math.abs(room.lateReverbPanZ);
        assertTrue(lean < 0.05, "a closed room surrounds you: " + lean);
    }

    @Test
    void wallsPassBassBetterThanTrebleDependingOnMaterial() {
        double[] sound = {6.5, 1.0, 0.5};
        double[] hfRatio = new double[3];
        AcousticMaterial[] materials = {AcousticMaterial.GLASS, AcousticMaterial.STONE, AcousticMaterial.CLOTH};
        for (int i = 0; i < 3; i++) {
            GridWorld world = new GridWorld().fill(3, -3, -5, 3, 6, 5, materials[i]); // thin wall, open elsewhere
            ListenerTrace.Accumulator acc = trace(world, 0.5, EYE, 0.5, sound);
            hfRatio[i] = acc.sums[ListenerTrace.MUFFLE_HF] / acc.sums[ListenerTrace.MUFFLE];
            assertTrue(hfRatio[i] < 1.0, materials[i] + " should cut treble");
        }
        assertTrue(hfRatio[0] > hfRatio[1] && hfRatio[1] > hfRatio[2], "glass > stone > wool: " + java.util.Arrays.toString(hfRatio));
    }

    @Test
    void scatteredTracesAreDeterministic() {
        GridWorld world = new GridWorld().room(0, 0, 0, 9, 4, 9, AcousticMaterial.STONE);
        ReverbStats a = trace(world, 4.5, EYE, 4.5).stats;
        ReverbStats b = trace(world, 4.5, EYE, 4.5).stats;
        assertEquals(a.laterDistanceSum, b.laterDistanceSum, 0.0);
        assertEquals(a.lateX, b.lateX, 0.0);
    }

    @Test
    void pansFollowTheCamera() {
        // A tail coming from the east (+x): facing north it is on the right, facing east it is ahead.
        ReverbParams p = new ReverbParams(1, 1, 0.3f, 1, 1, 1, 1, 1, 0, 0, 1, 0, 0.25f, 0, 0.25f, 0, 0.994f, 5000, 250, 0,
                0, 0, 0, 1, 0, 0, 1, 1, 0.1f);
        float[] north = com.redsmods.sound_physics_perfected.OpenALEffectsHandlerAccess.pans(p, new float[]{1, 0, 0, 0, 1, 0, 0, 0, -1});
        assertEquals(1.0f, north[3], 1e-6f);
        assertEquals(0.0f, north[5], 1e-6f);
        float[] east = com.redsmods.sound_physics_perfected.OpenALEffectsHandlerAccess.pans(p, new float[]{0, 0, 1, 0, 1, 0, 1, 0, 0});
        assertEquals(0.0f, east[3], 1e-6f);
        assertEquals(1.0f, east[5], 1e-6f);
    }

    @Test
    void smoothingConvergesAndSplitsStructureFromLevels() {
        ReverbParams a = RoomAcousticsModel.openAir(RoomAcousticsModel.Settings.defaults());
        ReverbParams b = reverb(new GridWorld().room(0, 0, 0, 4, 2, 4, AcousticMaterial.STONE), 2.5, EYE, 2.5);
        ReverbParams p = a;
        for (int i = 0; i < 100; i++) p = p.approach(b, 0.2f);
        assertFalse(p.differsAudiblyFrom(b));
        ReverbParams mixed = b.withStructureOf(a);
        assertFalse(mixed.differsStructurallyFrom(a));
        assertFalse(mixed.differsInLevelsFrom(b));
    }
}
