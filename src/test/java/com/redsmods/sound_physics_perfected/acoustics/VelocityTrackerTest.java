package com.redsmods.sound_physics_perfected.acoustics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VelocityTrackerTest {
    @Test
    void steadyMovementGivesItsSpeedInBlocksPerSecond() {
        VelocityTracker t = new VelocityTracker();
        for (int tick = 0; tick < 20; tick++) t.update(tick * 0.4, 64, 0); // 0.4 blocks per tick = 8 blocks/s
        assertEquals(8.0, t.vx(), 1e-3);
        assertEquals(0.0, t.vy(), 1e-9);
        assertTrue(t.moving());
    }

    @Test
    void firstPositionAndStandingStillGiveNoVelocity() {
        VelocityTracker t = new VelocityTracker();
        t.update(10, 64, 10);
        assertFalse(t.moving());
        t.update(10, 64, 10);
        assertFalse(t.moving());
    }

    @Test
    void aTeleportIsNotMovement() {
        VelocityTracker t = new VelocityTracker();
        for (int tick = 0; tick < 10; tick++) t.update(tick * 0.4, 64, 0);
        t.update(1000, 64, 0); // 996 blocks in one tick
        assertFalse(t.moving(), "teleport resets to standing still");
        t.update(1000.4, 64, 0);
        assertEquals(4.0, t.vx(), 1e-3, "then picks the movement up again (smoothed)");
    }

    @Test
    void anElytraFlightIsMovement() {
        VelocityTracker t = new VelocityTracker();
        for (int tick = 0; tick < 20; tick++) t.update(0, 64, tick * 1.75); // 35 blocks/s
        assertEquals(35.0, t.vz(), 1e-3);
    }
}
