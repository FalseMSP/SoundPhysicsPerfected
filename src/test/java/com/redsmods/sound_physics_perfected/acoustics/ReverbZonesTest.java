package com.redsmods.sound_physics_perfected.acoustics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReverbZonesTest {
    private static ReverbParams space(float decay, float enclosure, float late) {
        return new ReverbParams(1, 1, 0.45f, 0.7f, 1, decay, 0.6f, 1, 0.5f, 0.02f, late, 0.02f, 0.25f, 0, 0.25f, 0,
                0.994f, 5000, 250, 0, enclosure, 10, 0.05f);
    }

    private static final ReverbParams OUTSIDE = space(0.3f, 0.5f, 0.2f);
    private static final ReverbParams CAVE = space(4.0f, 1.0f, 0.5f);
    private static final ReverbParams HALL = space(1.8f, 1.0f, 1.2f);

    @Test
    void soundsInTheListenersSpaceUseTheListenersReverb() {
        ReverbZones zones = new ReverbZones();
        zones.beginTick();
        assertEquals(-1, zones.offer(new ReverbZones.SourceReverb(space(0.32f, 0.52f, 0.2f), 1.0f, 0, 0), 1.0f, OUTSIDE));
        assertEquals(-1, zones.offer(new ReverbZones.SourceReverb(CAVE, 0.0f, 0, 0), 1.0f, OUTSIDE), "inaudible reverb");
    }

    @Test
    void similarSpacesShareAZoneAndDifferentOnesGetTheirOwn() {
        ReverbZones zones = new ReverbZones();
        zones.beginTick();
        int cave = zones.offer(new ReverbZones.SourceReverb(CAVE, 0.8f, 0, 0.4f), 1.0f, OUTSIDE);
        int cave2 = zones.offer(new ReverbZones.SourceReverb(space(3.6f, 1.0f, 0.5f), 0.5f, 0, 0.4f), 0.5f, OUTSIDE);
        int hall = zones.offer(new ReverbZones.SourceReverb(HALL, 0.6f, 0.4f, 0), 1.0f, OUTSIDE);
        assertTrue(cave >= 0);
        assertEquals(cave, cave2);
        assertTrue(hall >= 0 && hall != cave);
        // Out of zones: a third kind of space joins the nearest.
        int third = zones.offer(new ReverbZones.SourceReverb(space(2.5f, 1.0f, 0.9f), 0.6f, 0, 0), 1.0f, OUTSIDE);
        assertTrue(third == cave || third == hall);
        zones.endTick(0.05, 0.0);
        assertEquals(4.0f, zones.zone(cave).params().decayTime, 1e-3f, "the loudest member defines the zone");
        assertEquals(0.8f, zones.zone(cave).gain(), 1e-3f);
    }

    @Test
    void idleZonesRingOutThenFree() {
        ReverbZones zones = new ReverbZones();
        zones.beginTick();
        int zone = zones.offer(new ReverbZones.SourceReverb(CAVE, 1.0f, 0, 0), 1.0f, OUTSIDE);
        zones.endTick(0.05, 0.0);
        for (int tick = 0; tick < 60; tick++) { // 3 s, shorter than the 4 s decay
            zones.beginTick();
            zones.endTick(0.05, 0.0);
        }
        assertTrue(zones.zone(zone).active());
        assertEquals(1.0f, zones.zone(zone).gain(), 1e-3f, "keeps ringing while its tail would");
        for (int tick = 0; tick < 200; tick++) {
            zones.beginTick();
            zones.endTick(0.05, 0.0);
        }
        assertFalse(zones.zone(zone).active());
    }
}
