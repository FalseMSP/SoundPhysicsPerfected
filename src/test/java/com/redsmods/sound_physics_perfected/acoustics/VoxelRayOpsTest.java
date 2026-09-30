package com.redsmods.sound_physics_perfected.acoustics;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class VoxelRayOpsTest {
    private static final AcousticMaterial M = AcousticMaterial.STONE;

    @Test
    void traversalVisitsTheSameCellsAsDenseSampling() {
        Random random = new Random(42);
        VoxelTraversal dda = new VoxelTraversal();
        for (int n = 0; n < 500; n++) {
            double ox = random.nextDouble() * 20 - 10, oy = random.nextDouble() * 20 - 10, oz = random.nextDouble() * 20 - 10;
            double dx = random.nextGaussian(), dy = random.nextGaussian(), dz = random.nextGaussian();
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= len; dy /= len; dz /= len;
            double max = 15;
            dda.init(ox, oy, oz, dx, dy, dz, max);
            // Every point sampled along the ray must lie in a cell the walk visited during that stretch of t.
            double t = 0;
            do {
                while (t < Math.min(dda.tExit, max)) {
                    if (t > dda.tEnter + 1e-6 && t < dda.tExit - 1e-6) {
                        assertEquals(VoxelTraversal.floor(ox + dx * t), dda.x);
                        assertEquals(VoxelTraversal.floor(oy + dy * t), dda.y);
                        assertEquals(VoxelTraversal.floor(oz + dz * t), dda.z);
                    }
                    t += 0.01;
                }
            } while (dda.advance() && t < max);
        }
    }

    @Test
    void firstHitReportsDistanceAndFacingNormal() {
        VoxelRayOps ops = new VoxelRayOps(new GridWorld().set(5, 0, 0, M));
        assertTrue(ops.firstHit(0.5, 0.5, 0.5, 1, 0, 0, 10));
        assertEquals(4.5, ops.hitT(), 1e-9);
        assertEquals(-1, ops.hitNormalX());
        assertSame(M, ops.hitMaterial());
        assertFalse(ops.firstHit(0.5, 0.5, 0.5, 1, 0, 0, 4.0), "hit is beyond the max distance");
        assertFalse(ops.firstHit(0.5, 0.5, 0.5, -1, 0, 0, 10), "nothing behind");
    }

    @Test
    void rayLeavingTheFaceItStartsOnDoesNotHitThatBlock() {
        VoxelRayOps ops = new VoxelRayOps(new GridWorld().set(5, 0, 0, M));
        // A bounce point sits exactly on the block's face; the reflected ray points away from it.
        assertFalse(ops.firstHit(5.0, 0.5, 0.5, -1, 0, 0, 10));
        // Pointing into the block it does hit, immediately.
        assertTrue(ops.firstHit(5.0, 0.5, 0.5, 1, 0, 0, 10));
        assertEquals(0.0, ops.hitT(), 1e-9);
    }

    @Test
    void partialBlocksUseTheirShape() {
        // A bottom slab: the ray above it passes, the ray through it hits its top.
        GridWorld world = new GridWorld().setPartial(3, 0, 0, M, 0, 0, 0, 1, 0.5, 1);
        VoxelRayOps ops = new VoxelRayOps(world);
        assertFalse(ops.firstHit(0.5, 0.75, 0.5, 1, 0, 0, 10));
        assertTrue(ops.firstHit(3.5, 2.0, 0.5, 0, -1, 0, 10));
        assertEquals(1.5, ops.hitT(), 1e-9);
        assertEquals(1, ops.hitNormalY());
    }

    @Test
    void obstructionMeasuresPathLengthInsideBlocks() {
        GridWorld world = new GridWorld().fill(3, -5, -5, 4, 5, 5, M); // a wall two blocks thick
        VoxelRayOps ops = new VoxelRayOps(world);
        assertEquals(2.0, ops.obstruction(0.5, 0.5, 0.5, 8.5, 0.5, 0.5, 10), 1e-9);
        // At 45 degrees the same wall is sqrt(2) times thicker.
        assertEquals(2.0 * Math.sqrt(2), ops.obstruction(0.5, 0.5, 0.5, 8.5, 8.5, 0.5, 10), 1e-6);
        // Stops accumulating once past the limit.
        assertTrue(ops.obstruction(0.5, 0.5, 0.5, 8.5, 0.5, 0.5, 1.0) < 2.0);
        // The sound's own block never counts.
        assertEquals(0.0, ops.obstruction(0.5, 0.5, 0.5, 3.5, 0.5, 0.5, 10), 1e-9);
    }

    @Test
    void skippingEmptySectionsChangesNothing() {
        Random random = new Random(7);
        GridWorld skipping = new GridWorld(), plain = new GridWorld();
        plain.reportSections = false;
        for (int i = 0; i < 300; i++) { // sparse blocks spread over many sections, some sections left empty
            int x = random.nextInt(120) - 60, y = random.nextInt(60) - 30, z = random.nextInt(120) - 60;
            skipping.set(x, y, z, M);
            plain.set(x, y, z, M);
        }
        VoxelRayOps a = new VoxelRayOps(skipping), b = new VoxelRayOps(plain);
        for (int n = 0; n < 3000; n++) {
            double ox = random.nextDouble() * 80 - 40, oy = random.nextDouble() * 40 - 20, oz = random.nextDouble() * 80 - 40;
            double dx = random.nextGaussian(), dy = random.nextGaussian(), dz = random.nextGaussian();
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= len; dy /= len; dz /= len;
            boolean ha = a.firstHit(ox, oy, oz, dx, dy, dz, 90), hb = b.firstHit(ox, oy, oz, dx, dy, dz, 90);
            assertEquals(hb, ha, "hit mismatch at ray " + n);
            if (ha) {
                assertEquals(b.hitT(), a.hitT(), 1e-9);
                assertEquals(b.hitNormalX() + 2 * b.hitNormalY() + 4 * b.hitNormalZ(), a.hitNormalX() + 2 * a.hitNormalY() + 4 * a.hitNormalZ());
            }
            double tx = ox + dx * 70, ty = oy + dy * 70, tz = oz + dz * 70;
            assertEquals(b.obstruction(ox, oy, oz, tx, ty, tz, 50), a.obstruction(ox, oy, oz, tx, ty, tz, 50), 1e-9);
        }
    }

    @Test
    void waterSurfaceReflectsAndIsCostlyToCross() {
        GridWorld lake = new GridWorld().fill(-20, -10, -20, 20, -1, 20, AcousticMaterial.WATER);
        VoxelRayOps ops = new VoxelRayOps(lake);
        // From the air, a ray going down hits the surface like a wall.
        assertTrue(ops.firstHit(0.5, 3.5, 0.5, 0, -1, 0, 20));
        assertEquals(3.5, ops.hitT(), 1e-9);
        assertSame(AcousticMaterial.WATER, ops.hitMaterial());
        assertEquals(1, ops.hitNormalY());
        // From underwater, the surface reflects back down.
        assertTrue(ops.firstHit(0.5, -5.5, 0.5, 0, 1, 0, 20));
        assertEquals(5.5, ops.hitT(), 1e-9); // the top water cell is y = -1, so the surface is at y = 0
        assertEquals(-1, ops.hitNormalY());
        // Underwater, sound travels freely; across the surface it loses a lot.
        double through = ops.obstruction(0.5, 3.5, 0.5, 0.5, -5.5, 0.5, 100);
        double within = ops.obstruction(0.5, -2.5, 0.5, 6.5, -2.5, 0.5, 100);
        assertTrue(through >= VoxelRayOps.WATER_SURFACE_WEIGHT, "surface crossing: " + through);
        assertTrue(within < 1.0, "within water: " + within);
        assertFalse(ops.clearPath(0.5, 3.5, 0.5, 0.5, -5.5, 0.5, 1.0), "no line of sight through the surface");
        // Something at the surface (in the top water block, or just above it) is heard from both sides for free.
        assertTrue(ops.obstruction(0.5, 3.5, 0.5, 0.5, -0.5, 0.5, 100) < 0.2, "sound in the top water block");
        assertTrue(ops.obstruction(0.5, -5.5, 0.5, 0.5, 0.05, 0.5, 100) < 1.0, "listener under, sound on the surface");
        // One block deeper it is under water again.
        assertTrue(ops.obstruction(0.5, 3.5, 0.5, 0.5, -1.5, 0.5, 100) >= VoxelRayOps.WATER_SURFACE_WEIGHT);
    }

    @Test
    void clearPathToleratesTheLastBlock() {
        VoxelRayOps ops = new VoxelRayOps(new GridWorld().set(5, 0, 0, M));
        assertFalse(ops.clearPath(0.5, 0.5, 0.5, 9.5, 0.5, 0.5, 0));
        assertTrue(ops.clearPath(0.5, 0.5, 0.5, 4.5, 0.5, 0.5, 0));
        // A sound inside a block (jukebox...) is still reachable with the one block tolerance.
        assertTrue(ops.clearPath(0.5, 0.5, 0.5, 5.5, 0.5, 0.5, 1.0));
    }

    @Test
    void raysAlongSectionEdgesAlwaysFinish() {
        // Negative x and z exactly along the vertical edge where empty sections meet used to bounce between two
        // sections forever (hanging the tracer thread).
        double k = 1 / Math.sqrt(3);
        GridWorld empty = new GridWorld();
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> {
            VoxelRayOps ops = new VoxelRayOps(empty);
            assertFalse(ops.firstHit(16.0, 70.3, 32.0, -k, k, -k, 64));
            GridWorld ground = new GridWorld().fill(-40, 63, -40, 40, 63, 40, AcousticMaterial.STONE);
            VoxelRayOps groundOps = new VoxelRayOps(ground);
            assertEquals(0.0, groundOps.obstruction(8.5, 65.62, 8.5, -7.5, 64.5, -7.5, 3), 1e-9);
            assertTrue(groundOps.clearPath(8.5, 65.62, 8.5, -7.5, 64.5, -7.5, 1.0));
            // Every diagonal through section corners, in every direction.
            for (int sx = -1; sx <= 1; sx += 2)
                for (int sy = -1; sy <= 1; sy += 2)
                    for (int sz = -1; sz <= 1; sz += 2) {
                        ops.firstHit(16.0, 16.0, 16.0, sx * k, sy * k, sz * k, 200);
                        ops.firstHit(0.0, 70.3, 0.0, sx / Math.sqrt(2), 0, sz / Math.sqrt(2), 200);
                    }
        });
    }

    @Test
    void waterloggedBlocksAreWaterAroundTheirShape() {
        // A lake with a waterlogged bottom slab on its floor.
        GridWorld lake = new GridWorld().fill(-20, -10, -20, 20, -1, 20, AcousticMaterial.WATER)
                .fill(-20, -11, -20, 20, -11, 20, AcousticMaterial.STONE)
                .setWaterlogged(0, -10, 0, AcousticMaterial.STONE, 0, 0, 0, 1, 0.5, 1);
        VoxelRayOps ops = new VoxelRayOps(lake);
        // Bouncing up off the slab, the ray is under water: the next surface is the lake's, 9.5 blocks up.
        assertTrue(ops.firstHit(0.5, -9.49, 0.5, 0, 1, 0, 20));
        assertEquals(9.49, ops.hitT(), 1e-6);
        // Under water, from the slab to a sound in the same water: no surface on the way.
        assertTrue(ops.obstruction(0.5, -9.49, 0.5, 5.5, -9.5, 0.5, 100) < 1.0);
    }
}
