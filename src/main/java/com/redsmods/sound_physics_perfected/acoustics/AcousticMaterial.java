package com.redsmods.sound_physics_perfected.acoustics;

/**
 * Frequency dependent surface absorption for a block, in three bands (≈250 Hz, ≈1 kHz, ≈5 kHz),
 * which is what the EAX reverb model can express (LF reference, mid, HF reference).
 * Absorption is the fraction of incident energy a surface does NOT reflect.
 */
public final class AcousticMaterial {
    public static final AcousticMaterial STONE = new AcousticMaterial("stone", 0.03f, 0.05f, 0.07f);
    public static final AcousticMaterial SMOOTH_STONE = new AcousticMaterial("smooth_stone", 0.02f, 0.03f, 0.05f);
    public static final AcousticMaterial POROUS_STONE = new AcousticMaterial("porous_stone", 0.05f, 0.10f, 0.15f);
    public static final AcousticMaterial METAL = new AcousticMaterial("metal", 0.02f, 0.03f, 0.04f);
    public static final AcousticMaterial GLASS = new AcousticMaterial("glass", 0.18f, 0.06f, 0.04f);
    public static final AcousticMaterial WOOD = new AcousticMaterial("wood", 0.15f, 0.10f, 0.08f);
    public static final AcousticMaterial BOOKS = new AcousticMaterial("books", 0.20f, 0.35f, 0.45f);
    public static final AcousticMaterial CLOTH = new AcousticMaterial("cloth", 0.20f, 0.55f, 0.70f);
    public static final AcousticMaterial SOIL = new AcousticMaterial("soil", 0.12f, 0.30f, 0.45f);
    public static final AcousticMaterial SAND = new AcousticMaterial("sand", 0.15f, 0.35f, 0.40f);
    public static final AcousticMaterial GRAVEL = new AcousticMaterial("gravel", 0.25f, 0.50f, 0.60f);
    public static final AcousticMaterial SNOW = new AcousticMaterial("snow", 0.45f, 0.75f, 0.90f);
    public static final AcousticMaterial FOLIAGE = new AcousticMaterial("foliage", 0.10f, 0.25f, 0.40f);
    public static final AcousticMaterial ORGANIC = new AcousticMaterial("organic", 0.10f, 0.30f, 0.45f);
    public static final AcousticMaterial GOO = new AcousticMaterial("goo", 0.05f, 0.15f, 0.30f);
    public static final AcousticMaterial DEFAULT = new AcousticMaterial("default", 0.05f, 0.10f, 0.12f);

    public final String name;
    public final float absorptionLow;
    public final float absorptionMid;
    public final float absorptionHigh;

    public AcousticMaterial(String name, float absorptionLow, float absorptionMid, float absorptionHigh) {
        this.name = name;
        this.absorptionLow = absorptionLow;
        this.absorptionMid = absorptionMid;
        this.absorptionHigh = absorptionHigh;
    }

    /** Fraction of mid-band energy kept per reflection. */
    public float reflectivity() {
        return 1.0f - absorptionMid;
    }

    @Override
    public String toString() {
        return name;
    }
}
