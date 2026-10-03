package com.redsmods.sound_physics_perfected.acoustics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps block states to acoustic properties: the surface material, the gaps sound gets through, and the shape sound
 * meets (which is not always the collision shape: a fence collides 1.5 blocks high but is a post and two rails).
 * <p>
 * Vanilla blocks come from {@link BlockAcoustics}, a table built from the game's own data (see that class). Other
 * blocks are classified by tag, then by family (leaves, fences, bars...), then by the block's sound type (its break
 * sound, e.g. "block.deepslate_bricks.break"), which also covers modded blocks that reuse vanilla sound types or
 * name theirs sensibly. Results are cached per state, so the tracer's hot loop only does a map lookup.
 */
public final class AcousticMaterials {
    private static final Map<BlockState, Entry> CACHE = new ConcurrentHashMap<>();

    /** Gaps of bars the table does not list (measured on iron bars), per band: bass, mid, treble. */
    private static final float[] BARS = {0.86f, 0.63f, 0.44f};

    private AcousticMaterials() {
    }

    public static final class Entry {
        public final AcousticMaterial material;
        /** Explosion resistance / 6, the classic "how much sound does one block stop" measure (deepslate = 1). */
        public final double resistanceWeight;
        /** Physical lower bound for resistanceWeight, for dense blocks explosions happen to break easily. */
        public final double minimumWeight;
        /** Wool-like blocks (vibration dampening tags) stop sound outright. */
        public final boolean dampens;
        /**
         * Share of the sound meeting this block's shape that goes straight through its gaps, per band (bass, mid,
         * treble), or null for a solid block. Bass gets through small gaps far better than treble does.
         */
        public final float[] pass;
        /** The shape sound meets when it is not the collision shape (null: use the collision shape). */
        public final VoxelShape shape;

        Entry(AcousticMaterial material, double resistanceWeight, double minimumWeight, boolean dampens,
              float[] pass, VoxelShape shape) {
            this.material = material;
            this.resistanceWeight = resistanceWeight;
            this.minimumWeight = minimumWeight;
            this.dampens = dampens;
            this.pass = pass;
            this.shape = shape;
        }

        /** Weight of one block of this material for permeation. */
        public double transmissionWeight(boolean useExplosionResistance, double maxBlocksPermeated) {
            if (!useExplosionResistance) return 1.0;
            if (dampens) return maxBlocksPermeated;
            return Math.min(Math.max(resistanceWeight, minimumWeight), 5.0);
        }
    }

    public static Entry of(BlockState state) {
        Entry entry = CACHE.get(state);
        if (entry == null) {
            entry = classify(state);
            CACHE.put(state, entry);
        }
        return entry;
    }

    /**
     * The shape sound meets at this block: its own acoustic shape when it has one, else its collision shape.
     * Empty for blocks sound passes freely.
     */
    public static VoxelShape shape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        Entry entry = of(state);
        return entry.shape != null ? entry.shape : state.getCollisionShape(level, pos);
    }

    /** Needed when tags reload (joining another world/server): classification depends on them. */
    public static void clearCache() {
        CACHE.clear();
    }

    private static Entry classify(BlockState state) {
        boolean dampens = state.is(BlockTags.DAMPENS_VIBRATIONS) || state.is(BlockTags.OCCLUDES_VIBRATION_SIGNALS);
        Block block = state.getBlock();
        double resistance = Math.min(block.getExplosionResistance() / 6.0, 5.0);
        BlockAcoustics.Row row = BlockAcoustics.get(BuiltInRegistries.BLOCK.getKey(block).toString());
        AcousticMaterial material = row != null && row.material() != null ? row.material() : classifyMaterial(state);
        float[] pass = row != null ? row.pass(state) : familyPass(state, material);
        VoxelShape shape = acousticShape(state);
        return new Entry(material, resistance, minimumWeight(material), dampens, pass, shape);
    }

    /** Gaps of blocks the table does not know (modded ones), by family. */
    private static float[] familyPass(BlockState state, AcousticMaterial material) {
        if (state.getBlock() instanceof LeavesBlock || state.is(BlockTags.LEAVES)) return BlockAcoustics.FOLIAGE;
        // Glass panes share the bars' class; only real bars have gaps.
        if (state.getBlock() instanceof IronBarsBlock && material != AcousticMaterial.GLASS) return BARS;
        return null;
    }

    /** The shape sound meets, where it differs from the collision shape; null when it does not. */
    private static VoxelShape acousticShape(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof FenceBlock) return fence(state);
        if (block instanceof FenceGateBlock) return state.getValue(FenceGateBlock.OPEN) ? null : fenceGate(state);
        if (block instanceof SnowLayerBlock) {
            // Drawn one layer higher than it collides: a single layer collides with nothing, yet covers the ground.
            return Shapes.box(0.0, 0.0, 0.0, 1.0, state.getValue(SnowLayerBlock.LAYERS) / 8.0, 1.0);
        }
        if (block instanceof PowderSnowBlock) return Shapes.block(); // a full block of snow you can sink into
        if (block instanceof LiquidBlock && state.getFluidState().getType().isSame(Fluids.LAVA)) {
            // A hard, reflecting surface, as water's is (water is handled as its own medium).
            float height = state.getFluidState().getOwnHeight();
            return height >= 0.85f ? Shapes.block() : height > 0.0f ? Shapes.box(0.0, 0.0, 0.0, 1.0, height, 1.0) : null;
        }
        if (block.hasDynamicShape()) return null;
        // Walls and the like collide higher than they reach (so nothing jumps over them): sound only meets what is
        // drawn, inside the block's own cell.
        try {
            VoxelShape collision = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (collision.isEmpty()) return null;
            AABB b = collision.bounds();
            if (b.minX < 0.0 || b.minY < 0.0 || b.minZ < 0.0 || b.maxX > 1.0 || b.maxY > 1.0 || b.maxZ > 1.0) {
                return Shapes.join(collision, Shapes.block(), BooleanOp.AND);
            }
        } catch (RuntimeException e) {
            // A modded shape that needs a real world: keep its collision shape.
        }
        return null;
    }

    private static final VoxelShape FENCE_POST = Block.box(6, 0, 6, 10, 16, 10);
    /** The two rails towards the north; rotated for the other sides. */
    private static final double[][] FENCE_RAILS = {{7, 12, 0, 9, 15, 9}, {7, 6, 0, 9, 9, 9}};
    /** A closed gate across x (facing north or south): two posts, the gate's two inner posts and four rails. */
    private static final double[][] GATE = {{0, 5, 7, 2, 16, 9}, {14, 5, 7, 16, 16, 9}, {6, 6, 7, 8, 15, 9},
            {8, 6, 7, 10, 15, 9}, {2, 6, 7, 6, 9, 9}, {2, 12, 7, 6, 15, 9}, {10, 6, 7, 14, 9, 9}, {10, 12, 7, 14, 15, 9}};

    /** The post and two rails per connected side, as the fence is drawn (it collides as a 1.5 block high wall). */
    private static VoxelShape fence(BlockState state) {
        VoxelShape shape = FENCE_POST;
        Direction[] sides = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        var connected = new net.minecraft.world.level.block.state.properties.BooleanProperty[]{
                CrossCollisionBlock.NORTH, CrossCollisionBlock.EAST, CrossCollisionBlock.SOUTH, CrossCollisionBlock.WEST};
        for (int i = 0; i < 4; i++) {
            if (!state.getValue(connected[i])) continue;
            for (double[] r : FENCE_RAILS) shape = Shapes.or(shape, rotated(r, sides[i], 0.0));
        }
        return shape;
    }

    private static VoxelShape fenceGate(BlockState state) {
        Direction facing = state.getValue(FenceGateBlock.FACING);
        // Along a wall the gate sits lower, to line up with the wall's top.
        double drop = state.getValue(FenceGateBlock.IN_WALL) ? 3.0 : 0.0;
        Direction along = facing.getAxis() == Direction.Axis.Z ? Direction.NORTH : Direction.EAST;
        VoxelShape shape = Shapes.empty();
        for (double[] r : GATE) shape = Shapes.or(shape, rotated(r, along, drop));
        return shape;
    }

    /** A box in pixels, given for the north side, turned to face {@code side} and lowered by {@code drop} pixels. */
    private static VoxelShape rotated(double[] r, Direction side, double drop) {
        double x0 = r[0], z0 = r[2], x1 = r[3], z1 = r[5];
        double nx0, nz0, nx1, nz1;
        switch (side) {
            case EAST -> { nx0 = 16 - z1; nx1 = 16 - z0; nz0 = x0; nz1 = x1; }
            case SOUTH -> { nx0 = 16 - x1; nx1 = 16 - x0; nz0 = 16 - z1; nz1 = 16 - z0; }
            case WEST -> { nx0 = z0; nx1 = z1; nz0 = 16 - x1; nz1 = 16 - x0; }
            default -> { nx0 = x0; nx1 = x1; nz0 = z0; nz1 = z1; }
        }
        return Block.box(nx0, r[1] - drop, nz0, nx1, r[4] - drop, nz1);
    }

    /** The value of a block state property by name, as written in block state files ("upper", "true"...). */
    static String propertyValue(BlockState state, String name) {
        for (Property<?> property : state.getProperties()) {
            if (!property.getName().equals(name)) continue;
            Object value = state.getValue(property);
            return value instanceof StringRepresentable s ? s.getSerializedName() : String.valueOf(value);
        }
        return null;
    }

    private static AcousticMaterial classifyMaterial(BlockState state) {
        if (state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS) || state.is(BlockTags.DAMPENS_VIBRATIONS)) {
            return AcousticMaterial.CLOTH;
        }
        if (state.is(BlockTags.LEAVES)) return AcousticMaterial.FOLIAGE;
        if (state.is(BlockTags.ICE)) return AcousticMaterial.SMOOTH_STONE;
        if (state.is(BlockTags.IMPERMEABLE)) return AcousticMaterial.GLASS;
        if (state.is(Blocks.BOOKSHELF)) return AcousticMaterial.BOOKS;

        String sound = soundName(state.getSoundType());
        if (sound == null) return AcousticMaterial.DEFAULT;

        if (has(sound, "snow")) return AcousticMaterial.SNOW;
        if (has(sound, "wool", "sponge", "cobweb")) return AcousticMaterial.CLOTH;
        if (has(sound, "moss", "sculk", "wart_block", "shroomlight", "fungus", "froglight", "azalea", "vine",
                "crop", "dripleaf", "spore", "lichen", "petals", "roots", "stem")) {
            return has(sound, "stem") ? AcousticMaterial.WOOD : AcousticMaterial.ORGANIC;
        }
        if (has(sound, "slime", "honey", "frogspawn")) return AcousticMaterial.GOO;
        if (has(sound, "glass")) return AcousticMaterial.GLASS;
        if (has(sound, "chiseled_bookshelf")) return AcousticMaterial.BOOKS;
        if (has(sound, "metal", "anvil", "chain", "lantern", "netherite", "copper", "iron", "heavy_core",
                "vault", "spawner", "bell")) {
            return AcousticMaterial.METAL;
        }
        if (has(sound, "wood", "bamboo", "ladder", "scaffolding", "hanging_sign", "shelf")) return AcousticMaterial.WOOD;
        if (has(sound, "gravel")) return AcousticMaterial.GRAVEL;
        if (has(sound, "soul_soil")) return AcousticMaterial.SOIL;
        if (has(sound, "sand")) return AcousticMaterial.SAND;
        if (has(sound, "bricks", "tiles", "polished", "calcite", "decorated_pot", "amethyst", "packed_mud")) {
            return AcousticMaterial.SMOOTH_STONE;
        }
        if (has(sound, "grass", "mud", "dirt", "hay", "nylium")) return AcousticMaterial.SOIL;
        if (has(sound, "netherrack", "nether_ore", "nether_gold_ore", "coral")) return AcousticMaterial.POROUS_STONE;
        if (has(sound, "stone", "deepslate", "tuff", "dripstone", "basalt", "bone", "debris", "ore", "resin",
                "trial")) {
            return AcousticMaterial.STONE;
        }
        return AcousticMaterial.DEFAULT;
    }

    private static double minimumWeight(AcousticMaterial m) {
        if (m == AcousticMaterial.SOIL || m == AcousticMaterial.SAND || m == AcousticMaterial.GRAVEL
                || m == AcousticMaterial.BOOKS) return 0.8;
        if (m == AcousticMaterial.GLASS || m == AcousticMaterial.SMOOTH_STONE || m == AcousticMaterial.STONE
                || m == AcousticMaterial.POROUS_STONE || m == AcousticMaterial.WOOD) return 0.5;
        if (m == AcousticMaterial.SNOW) return 0.4;
        if (m == AcousticMaterial.ORGANIC || m == AcousticMaterial.GOO) return 0.3;
        if (m == AcousticMaterial.METAL) return 1.0;
        return 0.0;
    }

    private static String soundName(SoundType type) {
        try {
            //? if >=1.21.2 {
            /*ResourceLocation id = type.getBreakSound().location();
            *///?} else {
            ResourceLocation id = type.getBreakSound().getLocation();
            //?}
            return id.getPath();
        } catch (RuntimeException e) {
            return null; // deferred modded sound types that are not registered yet
        }
    }

    private static boolean has(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) return true;
        }
        return false;
    }
}
