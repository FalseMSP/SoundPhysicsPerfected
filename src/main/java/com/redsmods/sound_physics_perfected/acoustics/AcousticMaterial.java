package com.redsmods.sound_physics_perfected.acoustics;

/**
 * Frequency dependent surface absorption for a block, in three bands (≈250 Hz, ≈1 kHz, ≈5 kHz),
 * which is what the EAX reverb model can express (LF reference, mid, HF reference).
 * Absorption is the fraction of incident energy a surface does NOT reflect.
 */
public final class AcousticMaterial {
    // Absorption low/mid/high, scattering (0 = mirror-like, 1 = fully diffuse), HF transmission factor
    public static final AcousticMaterial STONE = new AcousticMaterial("stone", 0.03f, 0.05f, 0.07f, 0.4f, 2.0f);
    public static final AcousticMaterial SMOOTH_STONE = new AcousticMaterial("smooth_stone", 0.02f, 0.03f, 0.05f, 0.15f, 2.0f);
    public static final AcousticMaterial POROUS_STONE = new AcousticMaterial("porous_stone", 0.05f, 0.1f, 0.15f, 0.5f, 2.2f);
    public static final AcousticMaterial METAL = new AcousticMaterial("metal", 0.02f, 0.03f, 0.04f, 0.1f, 2.5f);
    public static final AcousticMaterial GLASS = new AcousticMaterial("glass", 0.18f, 0.06f, 0.04f, 0.05f, 1.3f);
    public static final AcousticMaterial WOOD = new AcousticMaterial("wood", 0.15f, 0.1f, 0.08f, 0.25f, 1.6f);
    public static final AcousticMaterial BOOKS = new AcousticMaterial("books", 0.2f, 0.35f, 0.45f, 0.6f, 2.2f);
    public static final AcousticMaterial CLOTH = new AcousticMaterial("cloth", 0.2f, 0.55f, 0.7f, 0.6f, 3.0f);
    public static final AcousticMaterial SOIL = new AcousticMaterial("soil", 0.12f, 0.3f, 0.45f, 0.5f, 2.2f);
    public static final AcousticMaterial SAND = new AcousticMaterial("sand", 0.15f, 0.35f, 0.4f, 0.5f, 2.2f);
    public static final AcousticMaterial GRAVEL = new AcousticMaterial("gravel", 0.25f, 0.5f, 0.6f, 0.6f, 2.2f);
    public static final AcousticMaterial SNOW = new AcousticMaterial("snow", 0.45f, 0.75f, 0.9f, 0.6f, 3.0f);
    public static final AcousticMaterial FOLIAGE = new AcousticMaterial("foliage", 0.1f, 0.25f, 0.4f, 0.8f, 1.5f);
    public static final AcousticMaterial ORGANIC = new AcousticMaterial("organic", 0.1f, 0.3f, 0.45f, 0.5f, 2.0f);
    public static final AcousticMaterial GOO = new AcousticMaterial("goo", 0.05f, 0.15f, 0.3f, 0.3f, 2.0f);
    public static final AcousticMaterial DEFAULT = new AcousticMaterial("default", 0.05f, 0.1f, 0.12f, 0.3f, 2.0f);

    public final String name;
    public final float absorptionLow;
    public final float absorptionMid;
    public final float absorptionHigh;
    /** How much reflections scatter: 0 = mirror-like (glass, polished stone), 1 = fully diffuse (foliage). */
    public final float scattering;
    /**
     * High frequencies lose this many times more (in dB) than low frequencies when passing through the material
     * (mass law: dense walls pass bass far better than treble; porous soft blocks eat treble).
     */
    public final float transmissionHf;

    public AcousticMaterial(String name, float absorptionLow, float absorptionMid, float absorptionHigh,
                            float scattering, float transmissionHf) {
        this.name = name;
        this.absorptionLow = absorptionLow;
        this.absorptionMid = absorptionMid;
        this.absorptionHigh = absorptionHigh;
        this.scattering = scattering;
        this.transmissionHf = transmissionHf;
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
