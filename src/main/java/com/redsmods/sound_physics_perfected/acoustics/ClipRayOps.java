package com.redsmods.sound_physics_perfected.acoustics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
//? if neoforge && =1.21.1 {
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
//?}

/**
 * {@link RayOps} on top of vanilla {@code Level#clip}. Slower than {@link VoxelRayOps}, but it sees
 * geometry that other mods inject into clip (e.g. Sable sub-levels), so it is used when those are nearby
 * or when fast ray tracing is turned off.
 */
public final class ClipRayOps implements RayOps {
    private final Level level;
    private final Entity entity;
    private final boolean barrierAsAir;
    private final boolean useExplosionResistance;
    private final double maxBlocksPermeated;
    private final double stepSize;

    private double hitT;
    private int hitNormalX, hitNormalY, hitNormalZ;
    private AcousticMaterial hitMaterial;

    public ClipRayOps(Level level, Entity entity, boolean barrierAsAir, boolean useExplosionResistance,
                      double maxBlocksPermeated, double stepSize) {
        this.level = level;
        this.entity = entity;
        this.barrierAsAir = barrierAsAir;
        this.useExplosionResistance = useExplosionResistance;
        this.maxBlocksPermeated = maxBlocksPermeated;
        this.stepSize = Math.max(stepSize, 0.01);
    }

    private BlockHitResult clip(Vec3 from, Vec3 to) {
        ClipContext context = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity) {
            @Override
            public VoxelShape getBlockShape(BlockState blockState, BlockGetter getter, BlockPos pos) {
                if (barrierAsAir && blockState.is(Blocks.BARRIER)) {
                    return Shapes.empty();
                }
                return super.getBlockShape(blockState, getter, pos);
            }
        };
        return level.clip(context);
    }

    @Override
    public boolean firstHit(double ox, double oy, double oz, double dx, double dy, double dz, double maxDistance) {
        Vec3 from = new Vec3(ox, oy, oz);
        BlockHitResult hit = clip(from, new Vec3(ox + dx * maxDistance, oy + dy * maxDistance, oz + dz * maxDistance));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        hitT = from.distanceTo(hit.getLocation());
        Direction side = hit.getDirection();
        hitNormalX = side.getStepX();
        hitNormalY = side.getStepY();
        hitNormalZ = side.getStepZ();
        hitMaterial = AcousticMaterials.of(level.getBlockState(hit.getBlockPos())).material;
        return true;
    }

    @Override
    public double hitT() {
        return hitT;
    }

    @Override
    public int hitNormalX() {
        return hitNormalX;
    }

    @Override
    public int hitNormalY() {
        return hitNormalY;
    }

    @Override
    public int hitNormalZ() {
        return hitNormalZ;
    }

    @Override
    public AcousticMaterial hitMaterial() {
        return hitMaterial;
    }

    @Override
    public boolean clearPath(double ax, double ay, double az, double bx, double by, double bz, double tolerance) {
        Vec3 from = new Vec3(ax, ay, az);
        Vec3 to = new Vec3(bx, by, bz);
        double distance = from.distanceTo(to);
        if (distance - tolerance <= 0.0) {
            return true;
        }
        BlockHitResult hit = clip(from, to);
        return hit.getType() != HitResult.Type.BLOCK || from.distanceTo(hit.getLocation()) >= distance - tolerance;
    }

    private double weightAt(BlockPos pos) {
        return AcousticMaterials.of(level.getBlockState(pos)).transmissionWeight(useExplosionResistance, maxBlocksPermeated);
    }

    @Override
    public double obstruction(double ax, double ay, double az, double bx, double by, double bz, double maxWeighted) {
        return countBlocksBetween(new Vec3(ax, ay, az), new Vec3(bx, by, bz), maxWeighted);
    }

    //? if neoforge && =1.21.1 {
    private double countBlocksBetween(Vec3 start, Vec3 end, double maxWeighted) {
        double total = 0;
        Vec3 currentStart = start;
        BlockPos endBlockPos = BlockPos.containing(end);

        while (total < maxWeighted) {
            BlockHitResult hit = clip(currentStart, end);
            if (hit.getType() != HitResult.Type.BLOCK) break;

            BlockPos hitBlockPos = hit.getBlockPos();
            BlockState blockState = level.getBlockState(hitBlockPos);

            // Hits inside a Sable sub-level are reported in plot space; project them back into the world.
            SubLevelAccess subLevel = SableCompanion.INSTANCE.getContaining(level, hit.getLocation());
            Vec3 globalHitLoc = subLevel != null ? subLevel.logicalPose().transformPosition(hit.getLocation()) : hit.getLocation();

            if (subLevel == null && hitBlockPos.equals(endBlockPos)) break;
            if (subLevel != null && globalHitLoc.distanceToSqr(end) < 0.5) break;

            Vec3 plotDirection;
            if (subLevel != null) {
                Vec3 plotStart = subLevel.logicalPose().transformPositionInverse(currentStart);
                Vec3 plotEnd = subLevel.logicalPose().transformPositionInverse(end);
                plotDirection = plotEnd.subtract(plotStart).normalize();
            } else {
                plotDirection = end.subtract(currentStart).normalize();
            }

            VoxelShape blockShape = blockState.getShape(level, hitBlockPos);
            AABB blockBounds = blockShape.isEmpty() ? new AABB(hitBlockPos) : blockShape.bounds().move(hitBlockPos);

            Vec3 exitPoint = hit.getLocation();
            while (blockBounds.contains(exitPoint)) {
                exitPoint = exitPoint.add(plotDirection.scale(stepSize));
            }
            Vec3 globalExitPoint = subLevel != null ? subLevel.logicalPose().transformPosition(exitPoint) : exitPoint;

            if (SableCompanion.INSTANCE.distanceSquaredWithSubLevels(level, currentStart, start) >= end.distanceToSqr(start)) break;

            total += globalHitLoc.distanceTo(globalExitPoint) * weightAt(hitBlockPos);
            currentStart = globalExitPoint.add(end.subtract(currentStart).normalize().scale(0.01));
        }
        return total;
    }
    //?} else {
    /*private double countBlocksBetween(Vec3 start, Vec3 end, double maxWeighted) {
        double total = 0;
        Vec3 currentStart = start;
        BlockPos endBlockPos = BlockPos.containing(end);

        while (total < maxWeighted) {
            BlockHitResult hit = clip(currentStart, end);
            if (hit.getType() != HitResult.Type.BLOCK) break;

            BlockPos hitBlockPos = hit.getBlockPos();
            if (hitBlockPos.equals(endBlockPos)) break;

            BlockState blockState = level.getBlockState(hitBlockPos);
            Vec3 direction = end.subtract(currentStart).normalize();
            VoxelShape blockShape = blockState.getShape(level, hitBlockPos);
            AABB blockBounds = blockShape.isEmpty() ? new AABB(hitBlockPos) : blockShape.bounds().move(hitBlockPos);
            if (blockBounds.contains(end)) break;

            Vec3 exitPoint = hit.getLocation();
            while (blockBounds.contains(exitPoint)) {
                exitPoint = exitPoint.add(direction.scale(stepSize));
            }
            if (currentStart.distanceTo(start) >= end.distanceTo(start)) break;

            total += hit.getLocation().distanceTo(exitPoint) * weightAt(hitBlockPos);
            currentStart = exitPoint.add(direction.scale(0.01));
        }
        return total;
    }
    *///?}
}
