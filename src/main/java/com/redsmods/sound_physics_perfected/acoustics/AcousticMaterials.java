package com.redsmods.sound_physics_perfected.acoustics;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps block states to acoustic properties. Classification is by tag first, then by the block's sound
 * type (its break sound, e.g. "block.deepslate_bricks.break"), which also covers modded blocks that reuse
 * vanilla sound types or name theirs sensibly. Results are cached per state, so the tracer's hot loop only
 * does a map lookup.
 */
public final class AcousticMaterials {
    private static final Map<BlockState, Entry> CACHE = new ConcurrentHashMap<>();

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

        Entry(AcousticMaterial material, double resistanceWeight, double minimumWeight, boolean dampens) {
            this.material = material;
            this.resistanceWeight = resistanceWeight;
            this.minimumWeight = minimumWeight;
            this.dampens = dampens;
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

    /** Needed when tags reload (joining another world/server): classification depends on them. */
    public static void clearCache() {
        CACHE.clear();
    }

    private static Entry classify(BlockState state) {
        boolean dampens = state.is(BlockTags.DAMPENS_VIBRATIONS) || state.is(BlockTags.OCCLUDES_VIBRATION_SIGNALS);
        double resistance = Math.min(state.getBlock().getExplosionResistance() / 6.0, 5.0);
        AcousticMaterial material = classifyMaterial(state);
        return new Entry(material, resistance, minimumWeight(material), dampens);
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
